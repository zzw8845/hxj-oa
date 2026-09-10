package com.hxj.approval;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.*;
import com.hxj.enums.*;
import com.hxj.exception.BusinessException;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.ArchiveLedgerRepository;
import com.hxj.repository.CcRecordRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.OaAttachmentRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.AuthenticatedUserResponse;
import com.hxj.security.CurrentUser;
import com.hxj.security.DocumentAccessPolicy;
import com.hxj.document.LocalAttachmentStorage;
import com.hxj.workflow.WorkflowHistoryItemResponse;
import com.hxj.workflow.WorkflowPort;
import org.flowable.task.api.Task;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 统一审批操作编排：任务授权（仅当前审批人或超管）、业务留痕、
 * 流程流转（含指定层级跳转、补材料暂停/恢复、加签委派）和单据状态同步。
 */
@Service
public class ApprovalActionService {

    /** 补充材料回传时附件标注的节点名。 */
    public static final String MATERIAL_NODE = "补充材料";
    /** 用印盖章文件回传时附件标注的节点名。 */
    public static final String STAMPED_FILE_NODE = "盖章文件";

    /** 超级审批权限点：持有者可审批任意节点（待我审批查询同样以此旁路任务过滤）。 */
    public static final String APPROVE_ALL_NODES = "APPROVE_ALL_NODES";

    private final OaDocumentRepository documentRepository;
    private final ApprovalRecordRepository approvalRepository;
    private final OaAttachmentRepository attachmentRepository;
    private final ArchiveLedgerRepository archiveLedgerRepository;
    private final FlowConfigRepository flowConfigRepository;
    private final CcRecordRepository ccRepository;
    private final SysUserRepository userRepository;
    private final WorkflowPort workflowPort;
    private final DocumentAccessPolicy accessPolicy;
    private final LocalAttachmentStorage attachmentStorage;

    public ApprovalActionService(
            OaDocumentRepository documentRepository,
            ApprovalRecordRepository approvalRepository,
            OaAttachmentRepository attachmentRepository,
            ArchiveLedgerRepository archiveLedgerRepository,
            FlowConfigRepository flowConfigRepository,
            CcRecordRepository ccRepository,
            SysUserRepository userRepository,
            WorkflowPort workflowPort,
            DocumentAccessPolicy accessPolicy,
            LocalAttachmentStorage attachmentStorage) {
        this.documentRepository = documentRepository;
        this.approvalRepository = approvalRepository;
        this.attachmentRepository = attachmentRepository;
        this.archiveLedgerRepository = archiveLedgerRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.ccRepository = ccRepository;
        this.userRepository = userRepository;
        this.workflowPort = workflowPort;
        this.accessPolicy = accessPolicy;
        this.attachmentStorage = attachmentStorage;
    }

    /** 5.4 通过审批：凭证必填、意见留痕，末节点通过后归档（受闭环阻断约束）。 */
    @Transactional
    public ApprovalResultResponse approve(Long documentId, ApprovalRequest request) {
        if (request == null || request.evidenceFileId() == null) {
            throw new BusinessException(ErrorCodeEnum.EVIDENCE_REQUIRED, "请上传当前节点凭证");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        Task task = currentTask(document, currentUser);
        ApprovalRecord record = record(document, task, ApprovalActionEnum.APPROVE, currentUser);
        record.setComment(request.comment());
        record.setEvidenceFile(attachmentRepository.findById(request.evidenceFileId())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.EVIDENCE_NOT_FOUND, "审批凭证不存在")));
        approvalRepository.save(record);
        workflowPort.completeTask(task.getId(), Map.of());
        return advance(document);
    }

    /** 5.5 指定层级驳回：层级与原因必填，可选填需补充材料；流程跳转至目标层级。 */
    @Transactional
    public ApprovalResultResponse reject(Long documentId, ApprovalRequest request) {
        if (request == null || !StringUtils.hasText(request.rejectTarget())) {
            throw new BusinessException(ErrorCodeEnum.REJECT_TARGET_REQUIRED, "请选择驳回层级");
        }
        if (!StringUtils.hasText(request.comment())) {
            throw new BusinessException(ErrorCodeEnum.REJECT_REASON_REQUIRED, "驳回原因不能为空");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        Task task = currentTask(document, currentUser);
        ApprovalRecord record = record(document, task, ApprovalActionEnum.REJECT, currentUser);
        record.setComment(request.comment());
        record.setRejectTarget(request.rejectTarget());
        record.setRejectMaterials(request.rejectMaterials());
        approvalRepository.save(record);

        String targetActivity = resolveActivityId(document, request.rejectTarget());
        if (targetActivity == null) {
            // 驳回至提交人：流程终止，提交人通过“再次提交”重新发起
            if (document.getProcessInstanceId() != null) {
                workflowPort.endProcess(document.getProcessInstanceId(), "驳回至" + request.rejectTarget());
            }
        } else {
            workflowPort.moveTaskToActivity(document.getProcessInstanceId(), task.getId(), targetActivity);
        }
        document.setStatus(DocumentStatusEnum.REJECTED);
        document.setCurrentNode(request.rejectTarget());
        return state(document);
    }

    /**
     * 5.6 通过但补材料：付款前补充保持当前任务挂起等待材料；
     * 付款后补充流程继续流转，未补齐材料前不可闭环完结。
     */
    @Transactional
    public ApprovalResultResponse supplement(Long documentId, ApprovalRequest request) {
        if (request == null || request.supplementMode() == null
                || !StringUtils.hasText(request.supplementTarget())
                || !StringUtils.hasText(request.supplementMaterials())) {
            throw new BusinessException(ErrorCodeEnum.SUPPLEMENT_INFO_REQUIRED, "请完整填写补充材料信息");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        Task task = currentTask(document, currentUser);
        ApprovalRecord record = record(document, task, ApprovalActionEnum.SUPPLEMENT, currentUser);
        record.setComment(request.comment());
        record.setSupplementMode(request.supplementMode());
        record.setSupplementTarget(request.supplementTarget());
        record.setSupplementMaterials(request.supplementMaterials());
        record.setResolved(false);
        approvalRepository.save(record);

        if (request.supplementMode() == SupplementModeEnum.BEFORE_PAY) {
            document.setStatus(DocumentStatusEnum.SUPPLEMENT_REQUIRED);
            return state(document);
        }
        workflowPort.completeTask(task.getId(), Map.of());
        return advance(document);
    }

    /** 补充材料回传：解决未满足的补充要求；付款前补充恢复时跳过当前审批人直接进入下一节点。 */
    @Transactional
    public ApprovalResultResponse submitSupplementMaterials(
            Long documentId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.MATERIAL_FILE_REQUIRED, "请上传补充材料文件");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        boolean beforePayPending = false;
        for (ApprovalRecord pending : approvalRepository
                .findByDocumentIdAndResolvedFalseOrderByIdAsc(document.getId())) {
            if (pending.getAction() == ApprovalActionEnum.SUPPLEMENT
                    && pending.getSupplementMode() == SupplementModeEnum.BEFORE_PAY) {
                beforePayPending = true;
            }
            pending.setResolved(true);
        }
        SysUser uploader = user(currentUser);
        for (MultipartFile file : files) {
            LocalAttachmentStorage.StoredFile stored = attachmentStorage.store(file);
            OaAttachment attachment = new OaAttachment(
                    stored.originalName(), stored.path(), stored.contentType(), stored.size());
            attachment.setNodeName(MATERIAL_NODE);
            attachment.setUploader(uploader);
            document.addAttachment(attachment);
        }
        documentRepository.saveAndFlush(document);

        if (beforePayPending && document.getProcessInstanceId() != null) {
            // 付款前补充完成：跳过当前审批人，自动完成任务进入下一节点
            List<Task> active = workflowPort.tasksForProcess(document.getProcessInstanceId());
            if (!active.isEmpty()) {
                workflowPort.completeTask(active.get(0).getId(), Map.of());
            }
        }
        return advance(document);
    }

    /** 5.7 加签：任务委派给加签人，加签人可查看单据并发表意见后归还原审批人。 */
    @Transactional
    public ApprovalResultResponse sign(Long documentId, SignRequest request) {
        if (request == null || !StringUtils.hasText(request.signUserAccount())
                || !StringUtils.hasText(request.reason())) {
            throw new BusinessException(ErrorCodeEnum.SIGN_INFO_REQUIRED, "加签人员和原因不能为空");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        Task task = currentTask(document, currentUser);
        SysUser signUser = userRepository.findByAccount(request.signUserAccount())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.SIGN_USER_NOT_FOUND, "加签人员不存在"));
        ApprovalRecord record = record(document, task, ApprovalActionEnum.SIGN, currentUser);
        record.setSignUser(signUser);
        record.setSignReason(request.reason());
        approvalRepository.save(record);

        workflowPort.setAssignee(task.getId(), currentUser.account());
        workflowPort.delegateTask(task.getId(), signUser.getAccount());
        return state(document);
    }

    /** 加签意见：加签人对委派任务发表意见后归还任务。 */
    @Transactional
    public ApprovalResultResponse signComment(Long documentId, String comment) {
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException(ErrorCodeEnum.SIGN_COMMENT_REQUIRED, "请填写加签意见");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        Task task = workflowPort.tasksForProcess(document.getProcessInstanceId()).stream()
                .filter(candidate -> currentUser.account().equals(candidate.getAssignee()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.SIGN_TASK_NOT_FOUND, "当前用户没有该单据的加签任务"));
        ApprovalRecord record = record(document, task, ApprovalActionEnum.SIGN, currentUser);
        record.setComment(comment);
        approvalRepository.save(record);
        workflowPort.resolveTask(task.getId());
        return state(document);
    }

    /** 5.8 用印盖章文件回传与归档：未回传盖章文件前用印流程不可完结。 */
    @Transactional
    public ApprovalResultResponse returnStampedFile(Long documentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.STAMPED_FILE_REQUIRED, "请上传盖章文件");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        if (document.getBusinessType() != BusinessTypeEnum.SEAL_APPLICATION) {
            throw new BusinessException(ErrorCodeEnum.NOT_SEAL_APPLICATION, "仅用印申请可回传盖章文件");
        }
        LocalAttachmentStorage.StoredFile stored = attachmentStorage.store(file);
        OaAttachment attachment = new OaAttachment(
                stored.originalName(), stored.path(), stored.contentType(), stored.size());
        attachment.setNodeName(STAMPED_FILE_NODE);
        attachment.setUploader(user(currentUser));
        document.addAttachment(attachment);
        documentRepository.saveAndFlush(document);
        return advance(document);
    }

    /**
     * 5.10 撤回：申请人撤回尚无任何审批动作的单据，流程终止、单据转为已作废留痕。
     */
    @Transactional
    public ApprovalResultResponse withdraw(Long documentId) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DOCUMENT_NOT_FOUND, "单据不存在或无权查看"));
        if (currentUser == null || currentUser.userId() == null
                || document.getApplicant() == null
                || !currentUser.userId().equals(document.getApplicant().getId())) {
            throw new BusinessException(ErrorCodeEnum.DOCUMENT_WITHDRAW_FORBIDDEN, "仅申请人本人可撤回单据");
        }
        if (!approvalRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()).isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.DOCUMENT_WITHDRAW_STARTED, "审批已开始，无法撤回");
        }
        return voidInternal(document, "撤回申请", null, currentUser);
    }

    /**
     * 5.11 作废：管理员（配置流程与权限）作废未办结单据，原因必填留痕。
     */
    @Transactional
    public ApprovalResultResponse voidDocument(Long documentId, VoidDocumentRequest request) {
        if (request == null || !StringUtils.hasText(request.comment())) {
            throw new BusinessException(ErrorCodeEnum.VOID_REASON_REQUIRED, "请填写作废原因");
        }
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        if (currentUser == null || !isConfigurator(currentUser)) {
            throw new BusinessException(ErrorCodeEnum.ACCESS_DENIED, "仅管理员可作废单据");
        }
        return voidInternal(document(documentId), "作废", request.comment(), currentUser);
    }

    /** 撤回/作废共用编排：状态校验、流程终止、留痕与状态落库。 */
    private ApprovalResultResponse voidInternal(
            OaDocument document, String nodeLabel, String comment, AuthenticatedUserResponse currentUser) {
        if (document.getStatus() == DocumentStatusEnum.APPROVED
                || document.getStatus() == DocumentStatusEnum.VOIDED) {
            throw new BusinessException(ErrorCodeEnum.DOCUMENT_VOID_STATE_INVALID, "单据已办结或已作废，不可再作废");
        }
        if (document.getProcessInstanceId() != null) {
            workflowPort.endProcess(document.getProcessInstanceId(), nodeLabel);
        }
        ApprovalRecord record = new ApprovalRecord();
        record.setDocument(document);
        record.setNodeName(nodeLabel);
        record.setApprover(user(currentUser));
        record.setAction(ApprovalActionEnum.VOID);
        record.setComment(comment);
        approvalRepository.save(record);
        document.setStatus(DocumentStatusEnum.VOIDED);
        document.setCurrentNode(null);
        return state(document);
    }

    /** 按ID取单据（管理员作废场景，不套数据范围过滤）。 */
    private OaDocument document(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DOCUMENT_NOT_FOUND, "单据不存在或无权查看"));
    }

    /** 管理员判定：持有流程与权限配置权限点。 */
    private boolean isConfigurator(AuthenticatedUserResponse currentUser) {
        return currentUser.permissions() != null
                && currentUser.permissions().contains("CONFIGURE_FLOW_PERMISSION");
    }

    /** 5.9 流程历史查询：Flowable 活动与业务审批记录合并后的统一时间线。 */
    @Transactional(readOnly = true)
    public List<ApprovalHistoryItemResponse> history(Long documentId) {
        AuthenticatedUserResponse currentUser = CurrentUser.require();
        OaDocument document = visibleDocument(documentId, currentUser);
        List<ApprovalHistoryItemResponse> merged = new ArrayList<>();
        for (ApprovalRecord record : approvalRepository
                .findByDocumentIdOrderByCreatedAtAsc(document.getId())) {
            merged.add(new ApprovalHistoryItemResponse(
                    "BUSINESS",
                    record.getNodeName(),
                    record.getApprover().getName(),
                    record.getAction().name(),
                    record.getComment(),
                    record.getEvidenceFile() == null ? null : record.getEvidenceFile().getId(),
                    record.getCreatedAt(),
                    record.getCreatedAt()));
        }
        if (document.getProcessInstanceId() != null) {
            for (WorkflowHistoryItemResponse item : workflowPort.history(document.getProcessInstanceId())) {
                merged.add(new ApprovalHistoryItemResponse(
                        "FLOWABLE",
                        item.nodeName(),
                        item.assignee(),
                        item.activityType(),
                        null,
                        null,
                        item.startedAt(),
                        item.endedAt()));
            }
        }
        return merged.stream()
                .sorted(Comparator.comparing(ApprovalHistoryItemResponse::startedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 任务完成后的状态同步：仍有任务则进入下一节点；流程完结时按
     * 未解决补充要求 / 付款后置材料 / 用印未回传判定是否闭环归档。
     */
    private ApprovalResultResponse advance(OaDocument document) {
        List<Task> active = document.getProcessInstanceId() == null
                ? List.of() : workflowPort.tasksForProcess(document.getProcessInstanceId());
        if (!active.isEmpty()) {
            document.setStatus(DocumentStatusEnum.APPROVING);
            document.setCurrentNode(active.get(0).getName());
            return state(document);
        }
        if (closureBlocked(document)) {
            document.setStatus(DocumentStatusEnum.SUPPLEMENT_REQUIRED);
            return state(document);
        }
        return archive(document);
    }

    private boolean closureBlocked(OaDocument document) {
        if (approvalRepository.existsByDocumentIdAndResolvedFalse(document.getId())) {
            return true;
        }
        if (document.isNeedPostMaterial() && !hasAttachment(document, MATERIAL_NODE)) {
            return true;
        }
        return document.getBusinessType() == BusinessTypeEnum.SEAL_APPLICATION
                && !hasAttachment(document, STAMPED_FILE_NODE);
    }

    private ApprovalResultResponse archive(OaDocument document) {
        document.setStatus(DocumentStatusEnum.APPROVED);
        if (archiveLedgerRepository.findByDocumentId(document.getId()).isEmpty()) {
            archiveLedgerRepository.save(ArchiveLedger.from(document));
        }
        return state(document);
    }

    private boolean hasAttachment(OaDocument document, String nodeName) {
        return document.getAttachments().stream()
                .anyMatch(attachment -> nodeName.equals(attachment.getNodeName()));
    }

    /** 审批权限：仅当前任务审批人可操作；超管（审批全部节点）可审批任意节点。 */
    private Task currentTask(OaDocument document, AuthenticatedUserResponse currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCodeEnum.APPROVAL_NOT_ALLOWED, "当前用户不是该单据的审批人");
        }
        List<Task> candidates = isSuperApprover(currentUser)
                ? workflowPort.tasksForProcess(document.getProcessInstanceId())
                : workflowPort.pendingTasksForUser(currentUser.account(), currentUser.roles());
        Task task = candidates.stream()
                .filter(t -> document.getProcessInstanceId() != null
                        && document.getProcessInstanceId().equals(t.getProcessInstanceId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.APPROVAL_NOT_ALLOWED, "当前用户不是该单据的审批人"));
        // 加签委派中的任务只能由加签人 resolve 归还，其他人（含超管旁路）直接 complete 会被 Flowable 拒绝
        if (task.getDelegationState() == org.flowable.task.api.DelegationState.PENDING) {
            String delegatee = userRepository.findByAccount(task.getAssignee())
                    .map(com.hxj.entity.SysUser::getName).orElse(task.getAssignee());
            throw new BusinessException(ErrorCodeEnum.APPROVAL_NOT_ALLOWED,
                    "单据已加签给「" + delegatee + "」，等待其发表意见后继续处理");
        }
        return task;
    }

    private boolean isSuperApprover(AuthenticatedUserResponse currentUser) {
        return currentUser.permissions() != null
                && currentUser.permissions().contains(APPROVE_ALL_NODES);
    }

    /** 驳回层级 → BPMN 活动ID：按单据提交时的流程配置节点名精确/包含匹配，“提交人”无对应节点返回 null。 */
    private String resolveActivityId(OaDocument document, String rejectTarget) {
        if (rejectTarget.contains("提交人") || rejectTarget.contains("发起人")) {
            return null;
        }
        FlowConfig config = document.getFlowConfigId() != null
                ? flowConfigRepository.findById(document.getFlowConfigId())
                    .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"))
                : flowConfigRepository.findByType(document.getProjectName())
                    .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
        int index = 0;
        String containsMatch = null;
        for (FlowNodeConfig node : config.getNodes()) {
            if (node.getNodeType() == FlowNodeTypeEnum.START || node.getNodeType() == FlowNodeTypeEnum.CONDITION
                    || node.getNodeType() == FlowNodeTypeEnum.CC || node.getNodeType() == FlowNodeTypeEnum.END) {
                continue;
            }
            if (node.getName().equals(rejectTarget)) {
                return "node_" + index;
            }
            if (containsMatch == null
                    && (node.getName().contains(rejectTarget) || rejectTarget.contains(node.getName()))) {
                containsMatch = "node_" + index;
            }
            index++;
        }
        if (containsMatch != null) {
            return containsMatch;
        }
        throw new BusinessException(ErrorCodeEnum.REJECT_TARGET_INVALID, "驳回层级不在该单据的流程节点中");
    }

    private ApprovalRecord record(
            OaDocument document, Task task, ApprovalActionEnum action, AuthenticatedUserResponse currentUser) {
        ApprovalRecord record = new ApprovalRecord();
        record.setDocument(document);
        record.setNodeName(task.getName());
        record.setApprover(user(currentUser));
        record.setAction(action);
        return record;
    }

    private OaDocument visibleDocument(Long id, AuthenticatedUserResponse currentUser) {
        Specification<OaDocument> spec = accessPolicy.visibleTo(currentUser)
                .and((root, query, builder) -> builder.equal(root.get("id"), id));
        return documentRepository.findOne(spec)
                .orElseGet(() -> approverAccessibleDocument(id, currentUser));
    }

    /** 数据范围之外的兜底：当前任务持有人、超管、已审批人或被抄送人可访问。 */
    private OaDocument approverAccessibleDocument(Long id, AuthenticatedUserResponse currentUser) {
        OaDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.DOCUMENT_NOT_FOUND, "单据不存在或无权查看"));
        // 候选组任务的 assignee 为空，需按“待办人+候选角色”查询才能命中审批人
        boolean taskHolder = currentUser != null && document.getProcessInstanceId() != null
                && workflowPort.pendingTasksForUser(currentUser.account(), currentUser.roles()).stream()
                    .anyMatch(task -> document.getProcessInstanceId().equals(task.getProcessInstanceId()));
        boolean acted = currentUser != null && currentUser.userId() != null
                && approvalRepository.existsByDocumentIdAndApproverId(document.getId(), currentUser.userId());
        boolean cc = currentUser != null && currentUser.userId() != null
                && ccRepository.existsByDocumentIdAndTargetUserId(document.getId(), currentUser.userId());
        if (taskHolder || acted || cc || (currentUser != null && isSuperApprover(currentUser))) {
            return document;
        }
        throw new BusinessException(ErrorCodeEnum.DOCUMENT_NOT_FOUND, "单据不存在或无权查看");
    }

    private SysUser user(AuthenticatedUserResponse currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "当前用户不存在");
        }
        return userRepository.findById(currentUser.userId())
                .orElseGet(() -> userRepository.findByAccount(currentUser.account())
                        .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "当前用户不存在")));
    }

    private ApprovalResultResponse state(OaDocument document) {
        return new ApprovalResultResponse(document.getId(), document.getStatus(), document.getCurrentNode(),
                document.getStatus() == DocumentStatusEnum.APPROVED);
    }
}
