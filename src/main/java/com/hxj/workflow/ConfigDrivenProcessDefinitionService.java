package com.hxj.workflow;
import com.hxj.common.ErrorCode;
import com.hxj.entity.FlowConditionRule;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.FlowNodeType;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** 将数据库中的有序流程配置编译并部署为 BPMN 2.0 定义。 */
@Service
public class ConfigDrivenProcessDefinitionService {

    private final FlowConfigRepository flowConfigRepository;
    private final RepositoryService repositoryService;

    public ConfigDrivenProcessDefinitionService(
            FlowConfigRepository flowConfigRepository,
            RepositoryService repositoryService) {
        this.flowConfigRepository = flowConfigRepository;
        this.repositoryService = repositoryService;
    }

    @Transactional(readOnly = true)
    public String deploy(Long configId) {
        FlowConfig config = flowConfigRepository.findById(configId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
        String processKey = processKey(configId);
        BpmnModel model = buildModel(config, processKey);
        Deployment deployment = repositoryService.createDeployment()
                .name("OA流程-" + config.getType())
                .key(processKey)
                .addBpmnModel(processKey + ".bpmn20.xml", model)
                .deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        return definition.getId();
    }

    @Transactional(readOnly = true)
    public String latestDefinitionId(Long configId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey(configId))
                .latestVersion()
                .singleResult();
        return definition == null ? null : definition.getId();
    }

    /** 事务边界：保证懒加载流程节点配置时有可用 Session（自调用 deploy 时事务传播）。 */
    @Transactional(readOnly = true)
    public String ensureDeployed(Long configId) {
        String definitionId = latestDefinitionId(configId);
        return definitionId == null ? deploy(configId) : definitionId;
    }

    public String processKey(Long configId) {
        return "oa_flow_config_" + configId;
    }

    BpmnModel buildModel(FlowConfig config, String processKey) {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId(processKey);
        process.setName(config.getType());
        process.setExecutable(true);
        model.addProcess(process);

        StartEvent start = new StartEvent();
        start.setId("start");
        start.setName("发起人");
        process.addFlowElement(start);

        EndEvent end = new EndEvent();
        end.setId("end");
        end.setName("流程结束");
        process.addFlowElement(end);

        List<FlowNodeConfig> executableNodes = config.getNodes().stream()
                .filter(node -> node.getNodeType() != FlowNodeType.START)
                .filter(node -> node.getNodeType() != FlowNodeType.CONDITION)
                .filter(node -> node.getNodeType() != FlowNodeType.END)
                .toList();
        Map<String, FlowElement> elements = new LinkedHashMap<>();
        for (int index = 0; index < executableNodes.size(); index++) {
            FlowNodeConfig node = executableNodes.get(index);
            FlowElement element = createElement(node, "node_" + index);
            elements.put(node.getName(), element);
            process.addFlowElement(element);
        }

        Map<String, List<FlowConditionRule>> rulesByTarget = config.getConditionRules().stream()
                .collect(Collectors.groupingBy(
                        FlowConditionRule::getTargetNodeName,
                        LinkedHashMap::new,
                        Collectors.toList()));

        String previousId = start.getId();
        for (int index = 0; index < executableNodes.size(); index++) {
            FlowNodeConfig node = executableNodes.get(index);
            FlowElement element = elements.get(node.getName());
            List<FlowConditionRule> rules = rulesByTarget.getOrDefault(node.getName(), List.of());
            if (rules.isEmpty()) {
                addFlow(process, previousId, element.getId(), null, false);
                previousId = element.getId();
                continue;
            }

            ExclusiveGateway decision = new ExclusiveGateway();
            decision.setId("decision_" + index);
            decision.setName("判断是否进入" + node.getName());
            process.addFlowElement(decision);
            addFlow(process, previousId, decision.getId(), null, false);

            ExclusiveGateway merge = new ExclusiveGateway();
            merge.setId("merge_" + index);
            merge.setName("合并" + node.getName());
            process.addFlowElement(merge);

            addFlow(process, decision.getId(), element.getId(), conditionExpression(rules), false);
            String defaultFlowId = addFlow(process, decision.getId(), merge.getId(), null, true);
            decision.setDefaultFlow(defaultFlowId);
            addFlow(process, element.getId(), merge.getId(), null, false);
            previousId = merge.getId();
        }
        addFlow(process, previousId, end.getId(), null, false);
        return model;
    }

    private FlowElement createElement(FlowNodeConfig node, String id) {
        if (node.getNodeType() == FlowNodeType.CC) {
            ServiceTask task = new ServiceTask();
            task.setId(id);
            task.setName(node.getName());
            task.setImplementationType("delegateExpression");
            task.setImplementation("${oaCcNodeDelegate}");
            return task;
        }
        UserTask task = new UserTask();
        task.setId(id);
        task.setName(node.getName());
        if (node.getAssigneeRole() != null && !node.getAssigneeRole().isBlank()) {
            task.setCandidateGroups(splitGroups(node.getAssigneeRole()));
        }
        return task;
    }

    private List<String> splitGroups(String groups) {
        String normalized = groups.replace("&", "_");
        String[] pieces = normalized.split("[/、]");
        List<String> result = new ArrayList<>();
        for (String piece : pieces) {
            if (!piece.isBlank()) {
                result.add(piece.trim());
            }
        }
        return result.isEmpty() ? List.of(groups) : result;
    }

    private String addFlow(
            Process process,
            String source,
            String target,
            String condition,
            boolean defaultPath) {
        SequenceFlow flow = new SequenceFlow(source, target);
        flow.setId("flow_" + process.getFlowElements().stream()
                .filter(SequenceFlow.class::isInstance).count());
        if (condition != null) {
            flow.setConditionExpression(condition);
        }
        if (defaultPath) {
            flow.setName("默认跳过");
        }
        process.addFlowElement(flow);
        return flow.getId();
    }

    private String conditionExpression(List<FlowConditionRule> rules) {
        return "${" + rules.stream().map(this::conditionClause).collect(Collectors.joining(" && ")) + "}";
    }

    private String conditionClause(FlowConditionRule rule) {
        String operator = switch (rule.getOperator()) {
            case EQUAL -> "==";
            case NOT_EQUAL -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
        };
        return rule.getVariableName() + " " + operator + " " + expressionLiteral(rule.getExpectedValue());
    }

    private String expressionLiteral(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("false") || value.matches("-?\\d+(\\.\\d+)?")) {
            return normalized;
        }
        return "'" + value.replace("'", "\\'") + "'";
    }
}
