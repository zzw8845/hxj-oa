package com.hxj.workflow;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OaWorkflowService implements WorkflowPort {

    private final ConfigDrivenProcessDefinitionService definitionService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    public OaWorkflowService(
            ConfigDrivenProcessDefinitionService definitionService,
            RuntimeService runtimeService,
            TaskService taskService,
            HistoryService historyService) {
        this.definitionService = definitionService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
    }

    @Override
    public String startProcess(Long configId, Long documentId, Map<String, Object> variables) {
        String definitionId = definitionService.ensureDeployed(configId);
        Map<String, Object> allVariables = new HashMap<>(variables == null ? Map.of() : variables);
        allVariables.put("documentId", documentId);
        allVariables.put("flowConfigId", configId);
        ProcessInstance instance = runtimeService.startProcessInstanceById(
                definitionId, String.valueOf(documentId), allVariables);
        return instance.getId();
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables == null ? Map.of() : variables);
    }

    @Override
    public List<Task> pendingTasksForUser(String account, List<String> roleNames) {
        List<Task> result = new ArrayList<>(taskService.createTaskQuery().taskCandidateOrAssigned(account).list());
        for (String role : roleNames == null ? List.<String>of() : roleNames) {
            result.addAll(taskService.createTaskQuery().taskCandidateGroup(role).list());
        }
        return result.stream().distinct().toList();
    }

    @Override
    public List<Task> allActiveTasks() {
        return taskService.createTaskQuery().active().list();
    }

    @Override
    public List<Task> delegatedTasks() {
        return taskService.createTaskQuery()
                .taskDelegationState(org.flowable.task.api.DelegationState.PENDING)
                .list();
    }

    @Override
    public List<Task> tasksForProcess(String processInstanceId) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).list();
    }

    @Override
    public void moveTaskToActivity(String processInstanceId, String taskId, String targetActivityId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(task.getTaskDefinitionKey(), targetActivityId)
                .changeState();
    }

    @Override
    public void endProcess(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, reason);
    }

    @Override
    public void setAssignee(String taskId, String account) {
        taskService.setAssignee(taskId, account);
    }

    @Override
    public void delegateTask(String taskId, String account) {
        taskService.delegateTask(taskId, account);
    }

    @Override
    public void resolveTask(String taskId) {
        taskService.resolveTask(taskId);
    }

    @Override
    public List<WorkflowHistoryItemResponse> history(String processInstanceId) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list()
                .stream()
                .filter(activity -> activity.getActivityName() != null)
                .map(this::toHistoryItem)
                .sorted(Comparator.comparing(WorkflowHistoryItemResponse::startedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public List<WorkflowNodeStatResponse> nodeStatistics() {
        Map<String, List<HistoricTaskInstance>> completed = new LinkedHashMap<>();
        for (HistoricTaskInstance task : historyService.createHistoricTaskInstanceQuery().finished().list()) {
            if (task.getName() != null) {
                completed.computeIfAbsent(task.getName(), name -> new ArrayList<>()).add(task);
            }
        }
        Map<String, Long> active = new HashMap<>();
        for (Task task : taskService.createTaskQuery().active().list()) {
            if (task.getName() != null) {
                active.merge(task.getName(), 1L, Long::sum);
            }
        }
        List<WorkflowNodeStatResponse> stats = new ArrayList<>();
        for (Map.Entry<String, List<HistoricTaskInstance>> entry : completed.entrySet()) {
            double avgHours = entry.getValue().stream()
                    .filter(task -> task.getDurationInMillis() != null)
                    .mapToLong(HistoricTaskInstance::getDurationInMillis)
                    .average()
                    .orElse(0d) / 3_600_000d;
            stats.add(new WorkflowNodeStatResponse(
                    entry.getKey(), entry.getValue().size(), active.getOrDefault(entry.getKey(), 0L), avgHours));
        }
        for (Map.Entry<String, Long> entry : active.entrySet()) {
            if (!completed.containsKey(entry.getKey())) {
                stats.add(new WorkflowNodeStatResponse(entry.getKey(), 0L, entry.getValue(), 0d));
            }
        }
        return stats;
    }

    private WorkflowHistoryItemResponse toHistoryItem(HistoricActivityInstance activity) {
        return new WorkflowHistoryItemResponse(
                activity.getActivityId(),
                activity.getActivityName(),
                activity.getActivityType(),
                activity.getAssignee(),
                activity.getStartTime() == null ? null : activity.getStartTime().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                activity.getEndTime() == null ? null : activity.getEndTime().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime());
    }
}
