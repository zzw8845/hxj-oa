package com.hxj.workflow;

import org.flowable.task.api.Task;

import java.util.List;
import java.util.Map;

public interface WorkflowPort {

    String startProcess(Long configId, Long documentId, Map<String, Object> variables);

    void completeTask(String taskId, Map<String, Object> variables);

    List<Task> pendingTasksForUser(String account, List<String> roleNames);

    /** 系统当前全部活动中的用户任务（供超级审批视图反查单据）。 */
    List<Task> allActiveTasks();

    /** 处于加签委派中（未归还）的用户任务。 */
    List<Task> delegatedTasks();

    /** 流程实例当前所有活动中的用户任务。 */
    List<Task> tasksForProcess(String processInstanceId);

    /** 将当前任务所在活动跳转至目标节点活动（指定层级驳回）。 */
    void moveTaskToActivity(String processInstanceId, String taskId, String targetActivityId);

    /** 终止流程实例（驳回至提交人等无对应流程节点场景）。 */
    void endProcess(String processInstanceId, String reason);

    void setAssignee(String taskId, String account);

    /** 委派任务给加签人，加签人发表意见后归还原审批人。 */
    void delegateTask(String taskId, String account);

    void resolveTask(String taskId);

    List<WorkflowHistoryItemResponse> history(String processInstanceId);

    List<WorkflowNodeStatResponse> nodeStatistics();
}
