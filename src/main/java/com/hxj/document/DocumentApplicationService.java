package com.hxj.document;

import com.hxj.approval.ApprovalActionService;
import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.*;
import com.hxj.enums.BusinessTypeEnum;
import com.hxj.common.PageResponse;
import com.hxj.enums.CcSourceEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaAttachmentRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUserResponse;
import com.hxj.security.CurrentUser;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.service.DocumentCodeGenerator;
import com.hxj.entity.FormField;
import com.hxj.entity.FormTemplate;import com.hxj.workflow.WorkflowPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocumentApplicationService {

    private final OaDocumentRepository documentRepository;
    private final OaAttachmentRepository attachmentRepository;
    private final ApprovalRecordRepository approvalRepository;
    private final CcRecordRepository ccRepository;
    private final SysUserRepository userRepository;
    private final FlowConfigRepository flowConfigRepository;
    private final DocumentCodeGenerator codeGenerator;
    private final FormTemplateManagementService formTemplateService;
    private final WorkflowPort workflowPort;
    private final DocumentAccessPolicy accessPolicy;
    private final LocalAttachmentStorage attachmentStorage;
    private final com.hxj.workflow.DeptAccountantResolver deptAccountantResolver;
    private final BigDecimal riskThreshold;

    public DocumentApplicationService(
            OaDocumentRepository documentRepository,
            OaAttachmentRepository attachmentRepository,
            ApprovalRecordRepository approvalRepository,
            CcRecordRepository ccRepository,
            SysUserRepository userRepository,
            FlowConfigRepository flowConfigRepository,
            FormTemplateManagementService formTemplateService,
            DocumentCodeGenerator codeGenerator,
            WorkflowPort workflowPort,
            DocumentAccessPolicy accessPolicy,
            LocalAttachmentStorage attachmentStorage,
            com.hxj.workflow.DeptAccountantResolver deptAccountantResolver,
            @Value("${app.risk-threshold:80000}") BigDecimal riskThreshold) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.approvalRepository = approvalRepository;
        this.ccRepository = ccRepository;
        this.userRepository = userRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.formTemplateService = formTemplateService;
        this.codeGenerator = codeGenerator;
        this.workflowPort = workflowPort;
        this.accessPolicy = accessPolicy;
        this.attachmentStorage = attachmentStorage;
        this.deptAccountantResolver = deptAccountantResolver;
        this.riskThreshold = riskThreshold;
    }

    @Transactional
    public DocumentSummaryResponse submit(SubmitDocumentRequest request) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        SysUser applicant = currentUserEntity(currentUser);
        FormTemplate template = formTemplateService.requireEnabled(request.templateId());
        List<FormField> fields = formTemplateService.fields(template.getId());
        Map<String, Object> values = formTemplateService.validateFieldValues(fields, request.fieldValues());
        if (template.getFlowConfigId() == null) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "模板未绑定审批流程");
        }
        FlowConfig flowConfig = flowConfigRepository.findById(template.getFlowConfigId())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "未配置对应审批流程"));
        BusinessTypeEnum businessType = BusinessTypeEnum.valueOf(template.getCategory());

        OaDocument document = new OaDocument();
        document.setBusinessType(businessType);
        document.setDocCode(codeGenerator.generate(template.getDocPrefix()));
        document.setProjectName(template.getName());
        document.setApplicant(applicant);
        document.setDepartment(applicant.getDepartment());
        BigDecimal amount = decimalValue(values, "amount");
        document.setAmount(businessType == BusinessTypeEnum.SEAL_APPLICATION ? null : amount);
        document.setNeedPostMaterial(booleanValue(values, "needPostMaterial"));
        Object contractNo = values.get("contractNo");
        if (contractNo != null) {
            // 合同编号提升列：前置关联单据检索依赖（findLinkCandidates byContract）
            document.setContractNo(String.valueOf(contractNo));
        }
        document.setStatus(DocumentStatusEnum.PENDING);
        document.setCurrentNode(flowConfig.firstActionNode() == null
                ? null : flowConfig.firstActionNode().getName());
        document.setRiskFlag(document.getAmount() != null
                && document.getAmount().compareTo(riskThreshold) >= 0);
        if (request.linkedDocumentId() != null) {
            document.setLinkedDocument(approvedDocument(request.linkedDocumentId()));
        }
        document.setFormTemplateId(template.getId());
        document.setFormVersion(template.getVersion());
        document.setFormSnapshot(formTemplateService.snapshotJson(fields));
        document.setFieldValues(formTemplateService.valuesJson(values));
        documentRepository.saveAndFlush(document);
        createSelfSelectedCc(document, request.ccUserIds());

        String processInstanceId = workflowPort.startProcess(
                flowConfig.getId(), document.getId(), workflowVariables(values, applicant));
        document.setProcessInstanceId(processInstanceId);
        document.setFlowConfigId(flowConfig.getId());
        return toSummary(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> search(DocumentSearchCondition criteria) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        Specification<OaDocument> spec = accessPolicy.visibleTo(currentUser)
                .and(criteriaSpecification(criteria));
        return documentRepository.findAll(spec).stream().map(this::toSummary).toList();
    }

    /** 分页版单据查询：分页参数见 {@link DocumentPageRequest}，按更新时间倒序。 */
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> searchPaged(DocumentPageRequest request) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        Specification<OaDocument> spec = accessPolicy.visibleTo(currentUser)
                .and(criteriaSpecification(request.toCriteria()));
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        if (Boolean.TRUE.equals(request.myPending())) {
            boolean superApprover = currentUser.permissions() != null
                    && currentUser.permissions().contains(ApprovalActionService.APPROVE_ALL_NODES);
            if (superApprover) {
                // 超级审批人：看待审单据全量，但被加签委派中的单据除外（它在加签人的待我审批里）
                Set<String> active = workflowPort.allActiveTasks().stream()
                        .map(task -> task.getProcessInstanceId())
                        .collect(Collectors.toSet());
                workflowPort.delegatedTasks().stream()
                        .map(task -> task.getProcessInstanceId())
                        .forEach(active::remove);
                if (active.isEmpty()) {
                    return PageResponse.of(new PageImpl<OaDocument>(List.of(), request.toPageable(sort), 0).map(this::toSummary));
                }
                spec = spec.and((root, query, builder) -> root.get("processInstanceId").in(active));
            } else {
                // 普通审批人：按其在 Flowable 中持有的任务（处理人/候选）反查单据
                Set<String> processInstanceIds = workflowPort
                        .pendingTasksForUser(currentUser.account(), currentUser.roles()).stream()
                        .map(task -> task.getProcessInstanceId())
                        .collect(Collectors.toSet());
                if (processInstanceIds.isEmpty()) {
                    return PageResponse.of(new PageImpl<OaDocument>(List.of(), request.toPageable(sort), 0).map(this::toSummary));
                }
                spec = spec.and((root, query, builder) -> root.get("processInstanceId").in(processInstanceIds));
            }
        }
        return PageResponse.of(documentRepository.findAll(spec, request.toPageable(sort)).map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse detail(Long documentId) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        List<ApprovalRecord> approvals = approvalRepository
                .findByDocumentIdOrderByCreatedAtAsc(documentId);
        List<CcRecord> ccRecords = ccRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        return new DocumentDetailResponse(
                document.getId(), document.getDocCode(), document.getProjectName(),
                document.getBusinessType(), document.getDocumentType(), document.getApplicantName(),
                document.getDepartment(), document.getAmount(), document.getStatus(),
                document.getCurrentNode(), document.getProcessInstanceId(), linked(document),
                document.getAttachments().stream().map(this::attachmentItem).toList(),
                approvals.stream().map(this::approvalItem).toList(),
                ccRecords.stream().map(this::ccItem).toList(),
                document.getProcessInstanceId() == null ? List.of()
                        : workflowPort.history(document.getProcessInstanceId()),
                formTemplateService.mergeFields(document.getFormSnapshot(), document.getFieldValues()));
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> findLinkCandidates(String query, boolean byContract) {
        String value = query == null ? "" : query;
        Set<OaDocument> documents = new LinkedHashSet<>();
        if (byContract) {
            documents.addAll(documentRepository.findByStatusAndContractNoContainingIgnoreCase(
                    DocumentStatusEnum.APPROVED, value));
        } else {
            documents.addAll(documentRepository.findByStatusAndDocCodeContainingIgnoreCase(
                    DocumentStatusEnum.APPROVED, value));
        }
        documents.addAll(documentRepository.findByStatusAndProjectNameContainingIgnoreCase(
                DocumentStatusEnum.APPROVED, value));
        documents.addAll(documentRepository.findByStatusAndApplicant_NameContainingIgnoreCase(
                DocumentStatusEnum.APPROVED, value));
        return documents.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<QuickDocumentItemResponse> quickDocuments() {
        // 只返回已绑定审批流程且启用的模板：未绑定流程的模板提交时会被 FLOW_CONFIG_NOT_FOUND 拒绝，
        // 与其让用户点了报错，不如不出现在可发起目录里（目录与流程配置保持自洽）。
        return formTemplateService.listEnabled().stream()
                .map(t -> new QuickDocumentItemResponse(
                        t.id(), t.businessType(), t.name(), t.sortOrder()))
                .toList();
    }

    /**
     * 可驳回层级：提交人 + 当前节点之前已流转过的审批节点。
     *
     * <p>设计图原先写死 5 个层级（直属部门负责人、核算会计…），与各预置流程的真实节点名
     * （直属主管、会计（按部门）…）对不上，导致大量 REJECT_TARGET_INVALID。
     * 改为按单据实际流程动态给出候选，前端下拉随之动态渲染。
     */
    @Transactional(readOnly = true)
    public List<String> rejectTargets(Long documentId) {
        OaDocument document = visibleDocument(documentId, CurrentUser.require());
        List<String> targets = new ArrayList<>();
        targets.add("提交人");
        FlowConfig config = document.getFlowConfigId() != null
                ? flowConfigRepository.findById(document.getFlowConfigId()).orElse(null)
                : flowConfigRepository.findByType(document.getProjectName()).orElse(null);
        if (config == null) {
            return targets;
        }
        List<String> approvalNodes = new ArrayList<>();
        for (FlowNodeConfig node : config.getNodes()) {
            if (node.getNodeType() == FlowNodeTypeEnum.START || node.getNodeType() == FlowNodeTypeEnum.CONDITION
                    || node.getNodeType() == FlowNodeTypeEnum.CC || node.getNodeType() == FlowNodeTypeEnum.END) {
                continue;
            }
            approvalNodes.add(node.getName());
        }
        int currentIndex = approvalNodes.indexOf(document.getCurrentNode());
        if (currentIndex <= 0) {
            return targets;
        }
        targets.addAll(approvalNodes.subList(0, currentIndex));
        return targets;
    }

    @Transactional
    public DocumentSummaryResponse repeat(Long sourceId) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument source = visibleDocument(sourceId, currentUser);
        if (source.getFormTemplateId() == null) {
            throw new BusinessException(ErrorCodeEnum.FORM_TEMPLATE_NOT_FOUND, "该单据无表单模板，无法再次提交");
        }
        return submit(new SubmitDocumentRequest(
                source.getFormTemplateId(),
                formTemplateService.parseValues(source.getFieldValues()),
                List.of(), null));
    }

    @Transactional
    public DocumentDetailResponse.Attachment upload(
            Long documentId,
            String nodeName,
            MultipartFile file) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        LocalAttachmentStorage.StoredFile stored = attachmentStorage.store(file);
        OaAttachment attachment = new OaAttachment(
                stored.originalName(), stored.path(), stored.contentType(), stored.size());
        attachment.setNodeName(nodeName);
        attachment.setUploader(currentUserEntity(currentUser));
        document.addAttachment(attachment);
        documentRepository.flush();
        return attachmentItem(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(Long attachmentId) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.ATTACHMENT_NOT_FOUND, "附件不存在"));
        visibleDocument(attachment.getDocument().getId(), currentUser);
        return new AttachmentDownload(
                attachment.getFileName(), attachment.getContentType(), attachmentStorage.load(attachment.getFilePath()));
    }

    public List<String> attachmentRequirements(Long templateId) {
        FormTemplate template = formTemplateService.requireEnabled(templateId);
        return formTemplateService.attachmentRequirements(template);
    }


    private BigDecimal decimalValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private boolean booleanValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Map<String, Object> workflowVariables(
            Map<String, Object> fieldValues, SysUser applicant) {
        // 直属主管账号：审批流「直属主管」节点以 ${managerAccount} 动态指派；未设置汇报线时为空串（任务待管理员指派）
        String managerAccount = applicant.getManagerId() == null ? ""
                : userRepository.findById(applicant.getManagerId())
                        .map(SysUser::getAccount).orElse("");
        // 主办会计账号：「会计（按部门）」节点以 ${deptAccountant} 动态指派（核算分工表解析，无映射时为空串）
        String deptAccountant = deptAccountantResolver.resolve(applicant);
        return Map.ofEntries(
                Map.entry("amount", decimalValue(fieldValues, "amount") == null
                        ? BigDecimal.ZERO : decimalValue(fieldValues, "amount")),
                Map.entry("involvesFunds", booleanValue(fieldValues, "involvesFunds")),
                Map.entry("requiresAdminReview", booleanValue(fieldValues, "requiresAdminReview")),
                Map.entry("businessMode", String.valueOf(fieldValues.getOrDefault("businessMode", ""))),
                // 发起人回环节点（签收/归还/上传归档附件等）以此为 assignee 表达式动态指派
                Map.entry("initiator", applicant.getAccount()),
                Map.entry("managerAccount", managerAccount),
                Map.entry("deptAccountant", deptAccountant));
    }

    /** ccUserIds 来自请求 DTO 的不可变列表（紧凑构造器已保证非 null），可直接构造集合。 */
    private void createSelfSelectedCc(OaDocument document, List<Long> ccUserIds) {
        Set<Long> ids = new LinkedHashSet<>(ccUserIds);
        List<SysUser> users = userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            throw new BusinessException(ErrorCodeEnum.CC_USER_NOT_FOUND, "抄送人员不存在");
        }
        users.stream().map(user -> CcRecord.toUser(document, user, CcSourceEnum.SELF_SELECTED))
                .forEach(ccRepository::save);
    }

    private OaDocument approvedDocument(Long id) {
        OaDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.LINKED_DOCUMENT_NOT_FOUND, "前置单据不存在"));
        if (document.getStatus() != DocumentStatusEnum.APPROVED) {
            throw new BusinessException(ErrorCodeEnum.LINKED_DOCUMENT_NOT_APPROVED, "仅可关联已审批通过单据");
        }
        return document;
    }

    private OaDocument visibleDocument(Long id, AuthenticatedUserResponse currentUser) {
        Specification<OaDocument> spec = accessPolicy.visibleTo(currentUser)
                .and((root, query, builder) -> builder.equal(root.get("id"), id));
        return documentRepository.findOne(spec)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DOCUMENT_NOT_FOUND, "单据不存在或无权查看"));
    }

    private SysUser currentUserEntity(AuthenticatedUserResponse currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "当前用户不存在");
        }
        return currentUser.userId() == null
                ? userRepository.findByAccount(currentUser.account())
                    .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "当前用户不存在"))
                : userRepository.findById(currentUser.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "当前用户不存在"));
    }

    private Specification<OaDocument> criteriaSpecification(DocumentSearchCondition criteria) {
        if (criteria == null) return Specification.where(null);
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (StringUtils.hasText(criteria.keyword())) {
                String keyword = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("docCode")), keyword),
                        builder.like(builder.lower(root.get("projectName")), keyword),
                        builder.like(builder.lower(root.get("applicant").get("name")), keyword)));
            }
            if (criteria.status() != null) predicates.add(builder.equal(root.get("status"), criteria.status()));
            if (criteria.businessType() != null) predicates.add(builder.equal(root.get("businessType"), criteria.businessType()));
            if (criteria.documentType() != null) predicates.add(builder.equal(root.get("documentType"), criteria.documentType()));
            if (criteria.applicantId() != null) predicates.add(builder.equal(root.get("applicant").get("id"), criteria.applicantId()));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private DocumentSummaryResponse toSummary(OaDocument document) {
        return new DocumentSummaryResponse(
                document.getId(), document.getDocCode(), document.getProjectName(),
                document.getBusinessType(), document.getDocumentType(), document.getApplicantName(),
                document.getDepartment(), document.getAmount(), document.getStatus(),
                document.getCurrentNode(), document.isRiskFlag(), document.getUpdatedAt());
    }

    private DocumentDetailResponse.LinkedDocument linked(OaDocument document) {
        OaDocument linked = document.getLinkedDocument();
        return linked == null ? null : new DocumentDetailResponse.LinkedDocument(
                linked.getId(), linked.getDocCode(), linked.getProjectName(), linked.getContractNo());
    }

    private DocumentDetailResponse.Attachment attachmentItem(OaAttachment attachment) {
        return new DocumentDetailResponse.Attachment(
                attachment.getId(), attachment.getFileName(), attachment.getContentType(), attachment.getFileSize(),
                attachment.getNodeName(), attachment.getUploader() == null ? null : attachment.getUploader().getName(),
                attachment.getCreatedAt());
    }

    private DocumentDetailResponse.Approval approvalItem(ApprovalRecord approval) {
        return new DocumentDetailResponse.Approval(
                approval.getId(), approval.getNodeName(), approval.getApprover().getName(),
                approval.getAction(), approval.getComment(), approval.getCreatedAt());
    }

    private DocumentDetailResponse.Cc ccItem(CcRecord cc) {
        return new DocumentDetailResponse.Cc(
                cc.getId(), cc.getTargetName(), cc.getSource().name(), cc.getCreatedAt());
    }

    public record AttachmentDownload(String fileName, String contentType, Resource resource) {}
}
