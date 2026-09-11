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
import com.hxj.repository.QuickDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUserResponse;
import com.hxj.security.CurrentUser;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.service.DocumentCodeGenerator;
import com.hxj.workflow.WorkflowPort;
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
    private final QuickDocumentRepository quickRepository;
    private final SysUserRepository userRepository;
    private final FlowConfigRepository flowConfigRepository;
    private final DocumentClassifier classifier;
    private final DocumentCodeGenerator codeGenerator;
    private final WorkflowPort workflowPort;
    private final DocumentAccessPolicy accessPolicy;
    private final AttachmentRequirementService requirementService;
    private final LocalAttachmentStorage attachmentStorage;
    private final com.hxj.workflow.DeptAccountantResolver deptAccountantResolver;
    private final BigDecimal riskThreshold;

    public DocumentApplicationService(
            OaDocumentRepository documentRepository,
            OaAttachmentRepository attachmentRepository,
            ApprovalRecordRepository approvalRepository,
            CcRecordRepository ccRepository,
            QuickDocumentRepository quickRepository,
            SysUserRepository userRepository,
            FlowConfigRepository flowConfigRepository,
            DocumentClassifier classifier,
            DocumentCodeGenerator codeGenerator,
            WorkflowPort workflowPort,
            DocumentAccessPolicy accessPolicy,
            AttachmentRequirementService requirementService,
            LocalAttachmentStorage attachmentStorage,
            com.hxj.workflow.DeptAccountantResolver deptAccountantResolver,
            @Value("${app.risk-threshold:80000}") BigDecimal riskThreshold) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.approvalRepository = approvalRepository;
        this.ccRepository = ccRepository;
        this.quickRepository = quickRepository;
        this.userRepository = userRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.classifier = classifier;
        this.codeGenerator = codeGenerator;
        this.workflowPort = workflowPort;
        this.accessPolicy = accessPolicy;
        this.requirementService = requirementService;
        this.attachmentStorage = attachmentStorage;
        this.deptAccountantResolver = deptAccountantResolver;
        this.riskThreshold = riskThreshold;
    }

    @Transactional
    public DocumentSummaryResponse submit(SubmitDocumentRequest request) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        SysUser applicant = currentUserEntity(currentUser);
        BusinessTypeEnum businessType = classifier.classify(request.projectName(), request.businessType());
        validate(request, businessType);
        FlowConfig flowConfig = flowConfigRepository.findByType(request.projectName())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "未配置对应审批流程"));

        OaDocument document = new OaDocument();
        document.setDocCode(codeGenerator.generate(businessType));
        applyRequest(document, request, businessType);
        document.setApplicant(applicant);
        document.setDepartment(applicant.getDepartment());
        document.setStatus(DocumentStatusEnum.PENDING);
        document.setCurrentNode(flowConfig.firstActionNode() == null
                ? null : flowConfig.firstActionNode().getName());
        document.setRiskFlag(document.getAmount() != null
                && document.getAmount().compareTo(riskThreshold) >= 0);
        if (request.linkedDocumentId() != null) {
            document.setLinkedDocument(approvedDocument(request.linkedDocumentId()));
        }
        documentRepository.saveAndFlush(document);
        createSelfSelectedCc(document, request.ccUserIds());

        String processInstanceId = workflowPort.startProcess(
                flowConfig.getId(), document.getId(), workflowVariables(document, request, applicant));
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
                        : workflowPort.history(document.getProcessInstanceId()));
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
    public List<QuickDocumentItemResponse> quickDocuments(BusinessTypeEnum businessType) {
        // 只返回已配置审批流程的快捷项：未配置流程的项目提交时会被 FLOW_CONFIG_NOT_FOUND 拒绝，
        // 与其让用户点了报错，不如不出现在可发起目录里（目录与流程配置保持自洽）。
        Set<String> configuredTypes = flowConfigRepository.findAll().stream()
                .map(FlowConfig::getType)
                .collect(Collectors.toSet());
        return quickRepository.findByBusinessTypeOrderBySortOrderAsc(businessType).stream()
                .filter(item -> configuredTypes.contains(item.getName()))
                .map(item -> new QuickDocumentItemResponse(
                        item.getId(), item.getBusinessType(), item.getName(), item.getSortOrder()))
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
        SubmitDocumentRequest request = new SubmitDocumentRequest(
                source.getBusinessType(), source.getProjectName(), source.getCompany(), source.getAmount(),
                source.getInvoiceSummary(), source.getReason(), source.isNeedPostMaterial(),
                source.getContractNo(), source.getLinkedDocument() == null ? null : source.getLinkedDocument().getId(),
                false, false, null, source.getSealProject(), source.getSealDepartment(), source.getSealTime(),
                source.getSealFileName(), source.getSealType(), source.getSealReason(), List.of());
        return submit(request);
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

    public List<String> attachmentRequirements(BusinessTypeEnum type, String projectName) {
        return requirementService.requiredFor(type, projectName);
    }

    private void validate(SubmitDocumentRequest request, BusinessTypeEnum type) {
        if (type == BusinessTypeEnum.SEAL_APPLICATION) {
            if (!StringUtils.hasText(request.sealProject()) || !StringUtils.hasText(request.sealDepartment())
                    || request.sealTime() == null || !StringUtils.hasText(request.sealFileName())
                    || request.sealType() == null || !StringUtils.hasText(request.sealReason())) {
                throw new BusinessException(ErrorCodeEnum.SEAL_INFO_INCOMPLETE, "请完整填写用印申请信息");
            }
            return;
        }
        if (request.company() == null || request.amount() == null || request.amount().signum() < 0
                || !StringUtils.hasText(request.reason())) {
            throw new BusinessException(ErrorCodeEnum.PAYMENT_INFO_INCOMPLETE, "请完整填写付款申请信息");
        }
    }

    private void applyRequest(OaDocument document, SubmitDocumentRequest request, BusinessTypeEnum type) {
        document.setBusinessType(type);
        document.setProjectName(request.projectName());
        document.setCompany(request.company());
        document.setAmount(type == BusinessTypeEnum.SEAL_APPLICATION ? null : request.amount());
        document.setInvoiceSummary(request.invoiceSummary());
        document.setReason(request.reason());
        document.setNeedPostMaterial(request.needPostMaterial());
        document.setContractNo(request.contractNo());
        document.setSealProject(request.sealProject());
        document.setSealDepartment(request.sealDepartment());
        document.setSealTime(request.sealTime());
        document.setSealFileName(request.sealFileName());
        document.setSealType(request.sealType());
        document.setSealReason(request.sealReason());
    }

    private Map<String, Object> workflowVariables(
            OaDocument document, SubmitDocumentRequest request, SysUser applicant) {
        // 直属主管账号：审批流「直属主管」节点以 ${managerAccount} 动态指派；未设置汇报线时为空串（任务待管理员指派）
        String managerAccount = applicant.getManagerId() == null ? ""
                : userRepository.findById(applicant.getManagerId())
                        .map(SysUser::getAccount).orElse("");
        // 主办会计账号：「会计（按部门）」节点以 ${deptAccountant} 动态指派（核算分工表解析，无映射时为空串）
        String deptAccountant = deptAccountantResolver.resolve(applicant);
        return Map.ofEntries(
                Map.entry("amount", document.getAmount() == null ? BigDecimal.ZERO : document.getAmount()),
                Map.entry("involvesFunds", request.involvesFunds()),
                Map.entry("requiresAdminReview", request.requiresAdminReview()),
                Map.entry("businessMode", request.businessMode() == null ? "" : request.businessMode().name()),
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
