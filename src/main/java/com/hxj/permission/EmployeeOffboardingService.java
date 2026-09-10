package com.hxj.permission;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.ApprovalRecord;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.enums.ApprovalActionEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.ApprovalRecordRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.workflow.WorkflowPort;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 离职交接服务（钉钉式）：员工离职时不自动猜测任务去向，而是集中列出其名下
 * 全部在途待办，由管理员指定交接人批量转交（留痕）或退回提交人重新提交；
 * 存在待办时离职状态不予生效，避免审批任务悬空卡死。
 */
@Service
public class EmployeeOffboardingService {

    private final WorkflowPort workflowPort;
    private final SysUserRepository userRepository;
    private final OaDocumentRepository documentRepository;
    private final ApprovalRecordRepository approvalRecordRepository;

    public EmployeeOffboardingService(
            WorkflowPort workflowPort,
            SysUserRepository userRepository,
            OaDocumentRepository documentRepository,
            ApprovalRecordRepository approvalRecordRepository) {
        this.workflowPort = workflowPort;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.approvalRecordRepository = approvalRecordRepository;
    }

    /** 在途待办条目视图。 */
    public record PendingTask(
            String taskId, Long documentId, String docCode,
            String projectName, String nodeName, String applicantName) {
    }

    /** 员工名下全部在途待办（含以其为审批人的任务与发起人回办任务）。 */
    @Transactional(readOnly = true)
    public List<PendingTask> pendingTasks(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "员工不存在"));
        List<String> roleNames = user.getRoles().stream().map(SysRole::getName).toList();
        List<PendingTask> items = new ArrayList<>();
        for (Task task : workflowPort.pendingTasksForUser(user.getAccount(), roleNames)) {
            documentRepository.findByProcessInstanceId(task.getProcessInstanceId())
                    .ifPresent(document -> items.add(new PendingTask(
                            task.getId(), document.getId(), document.getDocCode(),
                            document.getProjectName(), task.getName(),
                            document.getApplicant() == null ? "" : document.getApplicant().getName())));
        }
        return items;
    }

    /** 在途待办数量（离职校验用）。 */
    @Transactional(readOnly = true)
    public int pendingCount(Long userId) {
        return pendingTasks(userId).size();
    }

    /**
     * 批量转交：将员工名下全部在途待办转给交接人（必须存在且非本人），逐单留痕。
     */
    @Transactional
    public int transferAll(Long userId, String transferToAccount, String operatorAccount) {
        SysUser transferTo = userRepository.findByAccount(transferToAccount)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.MANAGER_NOT_FOUND, "交接人不存在"));
        SysUser leaver = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "员工不存在"));
        if (transferToAccount.equals(leaver.getAccount())) {
            throw new BusinessException(ErrorCodeEnum.MANAGER_SELF_REFERENCE, "交接人不能是离职员工本人");
        }
        SysUser operator = userRepository.findByAccount(operatorAccount)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "当前用户不存在"));
        List<Task> tasks = workflowPort.pendingTasksForUser(
                leaver.getAccount(), leaver.getRoles().stream().map(SysRole::getName).toList());
        for (Task task : tasks) {
            workflowPort.setAssignee(task.getId(), transferTo.getAccount());
            documentRepository.findByProcessInstanceId(task.getProcessInstanceId())
                    .ifPresent(document -> leaveRecord(document, task.getName(), operator,
                            ApprovalActionEnum.TRANSFER,
                            "员工「" + leaver.getName() + "」离职，待办转交给「" + transferTo.getName() + "」"));
        }
        return tasks.size();
    }

    /**
     * 批量退回提交人：将员工名下全部在途待办对应单据退回（流程终止、单据驳回），逐单留痕。
     */
    @Transactional
    public int rejectAll(Long userId, String operatorAccount) {
        SysUser leaver = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "员工不存在"));
        SysUser operator = userRepository.findByAccount(operatorAccount)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "当前用户不存在"));
        List<Task> tasks = workflowPort.pendingTasksForUser(
                leaver.getAccount(), leaver.getRoles().stream().map(SysRole::getName).toList());
        for (Task task : tasks) {
            documentRepository.findByProcessInstanceId(task.getProcessInstanceId())
                    .ifPresent(document -> {
                        workflowPort.endProcess(task.getProcessInstanceId(), "离职退回");
                        document.setStatus(DocumentStatusEnum.REJECTED);
                        document.setCurrentNode("提交人");
                        documentRepository.save(document);
                        leaveRecord(document, task.getName(), operator,
                                ApprovalActionEnum.REJECT,
                                "员工「" + leaver.getName() + "」离职，单据退回提交人重新发起");
                    });
        }
        return tasks.size();
    }

    private void leaveRecord(OaDocument document, String nodeName, SysUser operator,
            ApprovalActionEnum action, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setDocument(document);
        record.setNodeName(nodeName);
        record.setApprover(operator);
        record.setAction(action);
        record.setComment(comment);
        approvalRecordRepository.save(record);
    }
}
