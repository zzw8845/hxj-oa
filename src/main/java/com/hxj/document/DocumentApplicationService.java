package com.hxj.document;

import com.hxj.approval.ApprovalActionService;
import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.*;
import com.hxj.enums.BusinessTypeEnum;
import com.hxj.common.PageResponse;
import com.hxj.enums.AssigneeSubjectEnum;
import com.hxj.enums.EmptyAssigneeStrategyEnum;
import com.hxj.enums.NodeApprovalModeEnum;
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
    private final com.hxj.workflow.AssigneeResolver assigneeResolver;
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
            com.hxj.workflow.AssigneeResolver assigneeResolver,
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
        this.assigneeResolver = assigneeResolver;
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
                .anyMatch(n -> n.getAssigneeSubject() == AssigneeSubjectEnum.INITIATOR_SELECT);
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
        // 审批人统一解析（六维正交模型）：解析 + 空策略落地；解析为空且未配空策略 → 提交即拒，
        // 把"卡单等管理员救"变成"提交时发现"
        com.hxj.workflow.AssigneeResolver.Context assigneeContext =
                new com.hxj.workflow.AssigneeResolver.Context(applicant, values, request.approverAccounts());
        AssigneeResolution resolution = resolveAssignees(flowConfig, assigneeContext);
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

        // 空策略自动通过的节点：引擎 skipExpression 不产生审批动作，审计留痕在业务侧补齐
        for (String nodeName : resolution.autoPassNodes()) {
            com.hxj.entity.ApprovalRecord autoPass = new com.hxj.entity.ApprovalRecord();
            autoPass.setDocument(document);
            autoPass.setNodeName(nodeName);
            autoPass.setApprover(applicant);
            autoPass.setAction(com.hxj.enums.ApprovalActionEnum.AUTO_PASS);
            autoPass.setComment("该节点未解析到审批人，按空策略自动通过并跳过");
            approvalRepository.save(autoPass);
        }
        String processInstanceId = workflowPort.startProcess(
                flowConfig.getId(), document.getId(),
                workflowVariables(values, applicant, fields, resolution.variables()));
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
     * 构建流程变量：模板启用字段全量提升（字段即变量）+ 系统字段 + 审批人解析产物。
     *
     * <p>保存关口已保证条件边变量 ⊆ 模板启用字段 ∪ 系统字段——「校验通过 ⟹ 运行时可求值」。
     */
    private Map<String, Object> workflowVariables(
            Map<String, Object> fieldValues, SysUser applicant, List<FormField> fields,
            Map<String, Object> assigneeVariables) {
        Map<String, Object> variables = new LinkedHashMap<>();
        // 字段即变量（钉钉同构两档）：模板全部启用字段提升为流程变量，
        // 类型按控件类型归一、缺失值给类型安全默认，网关求值不会遇到 null
        for (FormField field : fields) {
            variables.put(field.getFieldKey(),
                    conditionValue(field, fieldValues.get(field.getFieldKey())));
        }
        // 系统字段（钉钉同款）：发起人/发起人部门/岗位可直接作为条件判据，无需模板声明
        variables.put(WorkflowVariables.SYS_INITIATOR, applicant.getAccount());
        variables.put(WorkflowVariables.SYS_INITIATOR_DEPT_ID,
                applicant.getDepartmentId() == null ? -1L : applicant.getDepartmentId());
        variables.put(WorkflowVariables.SYS_INITIATOR_DEPT_NAME,
                applicant.getDepartment() == null ? "" : applicant.getDepartment());
        variables.put(WorkflowVariables.SYS_INITIATOR_POST,
                applicant.getPost() == null ? "" : applicant.getPost());
        // 审批人解析产物（节点级变量，同一流程多个同类节点各自独立）
        variables.putAll(assigneeVariables);
        // 空策略"自动通过"依赖 Flowable 原生 skipExpression，需显式开启开关
        variables.put(com.hxj.workflow.AssigneeResolver.SKIP_EXPRESSION_ENABLED_VARIABLE, Boolean.TRUE);
        return variables;
    }

    /**
     * 审批人统一解析（六维正交模型）+ 空策略落地。
     *
     * <p>逐个人工审批节点解析候选人：
     * <ul>
     *   <li>解析到人 → 写入节点候选变量（或签全局角色由 BPMN 侧用静态候选组，变量无害）；</li>
     *   <li>解析为空 + 已配空策略 → 自动通过（跳过变量）/ 自动拒绝（拒绝变量）/
     *       转模板管理员 / 转指定人（兜底人写回候选变量）；</li>
     *   <li>解析为空 + 未配空策略 → 汇总后提交即拒，错误在提交时暴露而非卡单等管理员救。</li>
     * </ul>
     */
    private AssigneeResolution resolveAssignees(
            FlowConfig flowConfig, com.hxj.workflow.AssigneeResolver.Context context) {
        Map<String, Object> variables = new LinkedHashMap<>();
        List<String> unresolvedNodes = new ArrayList<>();
        List<String> autoPassNodes = new ArrayList<>();
        for (FlowNodeConfig node : flowConfig.getNodes()) {
            if (node.getNodeType() != FlowNodeTypeEnum.APPROVAL
                    && node.getNodeType() != FlowNodeTypeEnum.HANDLER) {
                continue;
            }
            if (node.getAssigneeSubject() == null) {
                continue;
            }
            if (node.getApprovalMode() != null
                    && node.getApprovalMode() != NodeApprovalModeEnum.MANUAL) {
                continue; // 节点级自动通过/自动拒绝由 BPMN 服务任务处理，无需解析
            }
            // BPMN 的跳过/拒绝表达式总会求值：先兜底为 false，保证变量始终存在（否则引擎报未知属性）
            if (node.getEmptyStrategy() == EmptyAssigneeStrategyEnum.AUTO_PASS) {
                variables.put(WorkflowVariables.skipVariable(node.getId()), Boolean.FALSE);
            }
            if (node.getEmptyStrategy() == EmptyAssigneeStrategyEnum.AUTO_REJECT) {
                variables.put(WorkflowVariables.autoRejectVariable(node.getId()), Boolean.FALSE);
            }
            List<String> candidates = assigneeResolver.resolve(node, context);
            if (candidates.isEmpty()) {
                EmptyAssigneeStrategyEnum strategy = node.getEmptyStrategy();
                if (strategy == null) {
                    unresolvedNodes.add(node.getName());
                    continue;
                }
                switch (strategy) {
                    case AUTO_PASS -> {
                        variables.put(WorkflowVariables.skipVariable(node.getId()), Boolean.TRUE);
                        // 候选集合变量必须存在（BPMN 多实例的集合表达式会求值它）：空集合 + 跳过标记
                        variables.put(WorkflowVariables.candidatesVariable(node.getId()), List.of());
                        autoPassNodes.add(node.getName());
                    }
                    case AUTO_REJECT -> {
                        variables.put(WorkflowVariables.autoRejectVariable(node.getId()), Boolean.TRUE);
                        variables.put(WorkflowVariables.candidatesVariable(node.getId()), List.of());
                    }
                    case TO_ADMIN -> candidates = adminAccounts();
                    case TO_USER -> candidates = candidateAccounts(node.getEmptyFallback());
                }
                // 跳过/拒绝类策略本就没有候选人（由 BPMN 网关/skipExpression 承接），不参与"必须解析到人"判定
                if (strategy == EmptyAssigneeStrategyEnum.AUTO_PASS
                        || strategy == EmptyAssigneeStrategyEnum.AUTO_REJECT) {
                    continue;
                }
                if (candidates.isEmpty()) {
                    // 兜底对象本身不存在：仍拒绝提交，避免空列表多实例静默跳过造成"假审批"
                    unresolvedNodes.add(node.getName());
                    continue;
                }
            }
            variables.put(WorkflowVariables.candidatesVariable(node.getId()), candidates);
        }
        if (!unresolvedNodes.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FORM_FIELD_INVALID,
                    "无法解析下列审批节点的审批人：" + String.join("、", unresolvedNodes)
                            + "，请在组织架构/角色成员中补配，或在流程配置中为该节点设置空策略");
        }
        return new AssigneeResolution(variables, autoPassNodes);
    }

    /** 审批人解析结果：节点候选变量 + 空策略自动通过的节点（供业务侧补审计留痕）。 */
    private record AssigneeResolution(Map<String, Object> variables, List<String> autoPassNodes) {
    }

    /** 模板管理员 = 拥有全节点审批权限的账号（钉钉"转交管理员"的落地对象）。 */
    private List<String> adminAccounts() {
        return userRepository.findAccountsByPermission(
                com.hxj.approval.ApprovalActionService.APPROVE_ALL_NODES);
    }

    /** 空策略 TO_USER 的兜底账号（值不存在时返回空列表 → 交由上层拒绝提交）。 */
    private List<String> candidateAccounts(String account) {
        if (account == null || account.isBlank()) {
            return List.of();
        }
        return userRepository.findByAccount(account.trim()).map(user -> List.of(user.getAccount()))
                .orElse(List.of());
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
