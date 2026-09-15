package com.hxj.document;

import com.hxj.approval.ApprovalActionService;
import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.*;
import com.hxj.enums.BusinessTypeEnum;
import com.hxj.common.PageResponse;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeTypeEnum;
import com.hxj.enums.CcSourceEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.FlowStatusEnum;
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
import com.hxj.workflow.WorkflowVariables;
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
import java.util.LinkedHashMap;
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
    private final com.hxj.workflow.DeptScopedRoleResolver deptScopedRoleResolver;
    private final com.hxj.workflow.SupervisorChainResolver supervisorChainResolver;
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
            com.hxj.workflow.DeptScopedRoleResolver deptScopedRoleResolver,
            com.hxj.workflow.SupervisorChainResolver supervisorChainResolver,
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
        this.deptScopedRoleResolver = deptScopedRoleResolver;
        this.supervisorChainResolver = supervisorChainResolver;
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
        // 发布两态：只有已发布流程可提交（草稿修改不生效，防止改错配置污染新单据）
        if (flowConfig.getStatus() != FlowStatusEnum.PUBLISHED) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND,
                    "流程「" + flowConfig.getType() + "」未发布，请联系管理员发布后重试");
        }
        // 「发起人自选」节点需要申请人在提交时指定审批人（防提交后任务无主/空集自动跳过）
        boolean hasSelfSelect = flowConfig.getNodes().stream()
                .anyMatch(n -> n.getAssigneeType() == AssigneeTypeEnum.SELF_SELECT);
        if (hasSelfSelect && request.approverAccounts().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "该流程含「发起人自选」节点，请指定审批人");
        }
        for (String account : request.approverAccounts()) {
            if (userRepository.findByAccount(account).isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                        "自选审批人不存在：" + account);
            }
        }
        // 动态指派前置校验（钉钉同构）：所有动态寻人机制在提交时先解析，解析为空即拒绝提交——
        // 把"卡单等管理员救"变成"提交时发现"；与保存关口的静态校验共同覆盖两类坏配置
        if (flowConfig.getNodes().stream().anyMatch(n -> n.getAssigneeType() == AssigneeTypeEnum.MANAGER
                || n.getAssigneeType() == AssigneeTypeEnum.MANAGER_CHAIN)
                && supervisorChainResolver.resolveChain(applicant).isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "申请人未设置直属主管，且其部门及上级部门均未设置负责人，无法路由「直属主管」审批节点，请联系管理员在组织架构中补配");
        }
        // 「角色 + 按发起人部门」节点（核算会计、部门HR 等）：逐节点解析，任一为空即拒绝
        List<String> unresolvedScopedNodes = flowConfig.getNodes().stream()
                .filter(node -> node.getAssigneeType() == AssigneeTypeEnum.ROLE
                        && node.getAssigneeScope() == AssigneeScopeEnum.INITIATOR_DEPT)
                .filter(node -> deptScopedRoleResolver
                        .resolve(applicant, roleIds(node.getAssigneeValue())).isEmpty())
                .map(FlowNodeConfig::getName)
                .toList();
        if (!unresolvedScopedNodes.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "申请人所在部门未配置下列节点的审批角色分工，无法路由："
                            + String.join("、", unresolvedScopedNodes)
                            + "，请联系管理员在部门服务分工或角色成员中补配");
        }
        BusinessTypeEnum businessType = BusinessTypeEnum.valueOf(template.getCategory());

        OaDocument document = new OaDocument();
        document.setBusinessType(businessType);
        document.setDocCode(codeGenerator.generate(template.getDocPrefix()));
        document.setProjectName(template.getName());
        document.setApplicant(applicant);
        document.setDepartmentId(applicant.getDepartmentId());
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
                flowConfig.getId(), document.getId(),
                workflowVariables(values, applicant, request.approverAccounts(), flowConfig, fields));
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
                // 普通审批人：按其在 Flowable 中持有的任务（处理人/候选）反查单据。
                // 任务持有本身即访问依据——不再叠加数据范围过滤，否则候选组审批人
                // 会陷入"收得到待办、待审列表却看不见"的死锁
                Set<String> processInstanceIds = workflowPort
                        .pendingTasksForUser(currentUser.account(), currentUser.roleIds()).stream()
                        .map(task -> task.getProcessInstanceId())
                        .collect(Collectors.toSet());
                if (processInstanceIds.isEmpty()) {
                    return PageResponse.of(new PageImpl<OaDocument>(List.of(), request.toPageable(sort), 0).map(this::toSummary));
                }
                spec = criteriaSpecification(request.toCriteria())
                        .and((root, query, builder) -> root.get("processInstanceId").in(processInstanceIds));
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
        // 再次提交不自动带「发起人自选」审批人（需重新指定；流程含自选节点而未指定时提交会被明确拒绝）
        return submit(new SubmitDocumentRequest(
                source.getFormTemplateId(),
                formTemplateService.parseValues(source.getFieldValues()),
                List.of(), List.of(), null));
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

    /**
     * 构建流程变量（字段即变量 + 系统变量 + 节点级动态指派）：
     * 模板声明"参与流程条件"的字段全量提升（类型按控件类型归一，缺失值给类型安全默认），
     * 保证条件表达式在网关求值时不会遇到 null。
     */
    private Map<String, Object> workflowVariables(
            Map<String, Object> fieldValues, SysUser applicant, List<String> approverAccounts,
            FlowConfig flowConfig, List<FormField> fields) {
        Map<String, Object> variables = new LinkedHashMap<>();
        // 保留键兜底值：与保存关口的可用条件变量集合保持一致——「校验通过 ⟹ 运行时可求值」，
        // 否则流程配了 amount 条件而模板无该字段时，网关求值会因变量缺失异常导致单据卡死
        variables.put("amount", BigDecimal.ZERO);
        variables.put("involvesFunds", Boolean.FALSE);
        variables.put("requiresAdminReview", Boolean.FALSE);
        variables.put("businessMode", "");
        variables.put("needPostMaterial", Boolean.FALSE);
        // 字段即变量：模板声明参与流程条件的字段按控件类型归一后覆盖兜底值
        for (FormField field : fields) {
            if (field.isProcessVariable()) {
                variables.put(field.getFieldKey(),
                        conditionValue(field, fieldValues.get(field.getFieldKey())));
            }
        }
        // 主管链（钉钉同构）：人工指定的直属主管优先，未设则沿部门负责人树自近及远兜底——
        // 「直属主管」节点取链首 ${managerAccount}，「逐级主管」节点消费整条链串行审批（上限 10 级）
        List<String> managerChain = supervisorChainResolver.resolveChain(applicant);
        variables.put(WorkflowVariables.INITIATOR, applicant.getAccount());
        variables.put(WorkflowVariables.MANAGER_ACCOUNT, managerChain.isEmpty() ? "" : managerChain.get(0));
        variables.put(WorkflowVariables.MANAGER_CHAIN, managerChain);
        // 「发起人自选」节点串行多实例的审批人集合（提交时申请人指定）
        variables.put(WorkflowVariables.APPROVER_CHAIN, List.copyOf(approverAccounts));
        // 「角色 + 按发起人部门」节点：逐节点解析主办账号，写入节点级变量（同一流程多个此类节点各自独立）
        for (FlowNodeConfig node : flowConfig.getNodes()) {
            if (node.getAssigneeType() == AssigneeTypeEnum.ROLE
                    && node.getAssigneeScope() == AssigneeScopeEnum.INITIATOR_DEPT) {
                variables.put(WorkflowVariables.scopedAssigneeVariable(node.getId()),
                        deptScopedRoleResolver.resolveFirst(applicant, roleIds(node.getAssigneeValue())));
            }
        }
        return variables;
    }

    /** 字段值按控件类型归一为条件变量值：数值缺失给 0、布尔缺失给 false、文本缺失给空串。 */
    private Object conditionValue(FormField field, Object raw) {
        return switch (WorkflowVariables.valueTypeOfControlType(field.getControlType())) {
            case NUMERIC -> raw == null || String.valueOf(raw).isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(String.valueOf(raw));
            case BOOLEAN -> Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
            case STRING -> raw == null ? "" : String.valueOf(raw);
        };
    }

    /** 角色 ID 串（逗号分隔）→ ID 列表。 */
    private List<Long> roleIds(String assigneeValue) {
        if (assigneeValue == null || assigneeValue.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(assigneeValue.split(","))
                .map(String::trim)
                .filter(piece -> !piece.isEmpty())
                .map(Long::valueOf)
                .toList();
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
        // 统一访问语义（数据范围 ∪ 流程参与人）下沉在 DocumentAccessPolicy——
        // 审批人/被抄送人即使数据范围不覆盖该单据，也能查看、上传凭证、再次提交
        Specification<OaDocument> spec = accessPolicy.accessibleTo(currentUser)
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
