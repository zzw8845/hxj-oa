package com.hxj.workflow;

import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.FlowTransition;
import com.hxj.enums.ApproveModeEnum;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeSubjectEnum;
import com.hxj.enums.EmptyAssigneeStrategyEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.NodeApprovalModeEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
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

/**
 * 将数据库中的流程图（节点 + 转移边 + 六维审批人配置）编译并部署为 BPMN 2.0 定义。
 *
 * <p>编译规则（钉钉同构的正交映射）：
 * <ul>
 *   <li>START → StartEvent；END → EndEvent；CC → ServiceTask（{@code oaCcNodeDelegate}）；</li>
 *   <li><b>审批类型</b>：{@code AUTO_PASS} → ServiceTask（{@code oaAutoPassDelegate}，写留痕后继续）；
 *       {@code AUTO_REJECT} → ServiceTask（{@code oaAutoRejectDelegate}，驳回并终止）；</li>
 *   <li><b>主体 + 范围</b>：{@code ROLE + GLOBAL + 或签} → 静态候选组（角色 ID，成员实时生效）；
 *       其余主体组合 → 提交时解析为账号列表，以节点级变量交引擎消费；</li>
 *   <li><b>多人方式</b>：或签 → 并行多实例 + {@code nrOfCompletedInstances>=1}（任一通过即过、其余实例取消）；
 *       会签 → 并行多实例（全部通过）；依次 → 串行多实例；</li>
 *   <li><b>空策略</b>：{@code AUTO_PASS} → {@code skipExpression}（引擎原生跳过，不生成待办）；
 *       {@code AUTO_REJECT} → 节点入口排他网关（无人时转入拒绝处理器）；
 *       {@code TO_ADMIN}/{@code TO_USER} → 提交关口把兜底人写入候选变量，编译期无需特殊处理；</li>
 *   <li>某节点出边多于一条时插入排他网关：条件边按优先级带表达式，无条件边为默认流兜底。</li>
 * </ul>
 */
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

    /** 部署是写操作，不能标 readOnly：只读事务会让 Flowable 复用只读连接执行部署写入。 */
    @Transactional
    public String deploy(Long configId) {
        FlowConfig config = flowConfigRepository.findById(configId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
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

    /** 已发布流程返回其定义（发布动作负责部署）；草稿流程无定义——提交入口会拒绝。 */
    @Transactional(readOnly = true)
    public String ensureDeployed(Long configId) {
        String definitionId = latestDefinitionId(configId);
        if (definitionId != null) {
            return definitionId;
        }
        return deploy(configId);
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

        List<FlowNodeConfig> nodes = config.getNodes();
        Map<Long, FlowNodeConfig> startNode = nodes.stream()
                .filter(node -> node.getNodeType() == FlowNodeTypeEnum.START)
                .collect(LinkedHashMap::new, (map, node) -> map.put(node.getId(), node), Map::putAll);
        if (startNode.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "流程缺少开始节点");
        }
        Map<Long, String> elementIdByNodeId = new LinkedHashMap<>();
        elementIdByNodeId.put(startNode.keySet().iterator().next(), start.getId());

        int nodeCounter = 0;
        Map<Long, String> rejectGatewayByNodeId = new LinkedHashMap<>();
        int gatewayCounter = 0;
        int flowCounter = 0;
        for (FlowNodeConfig node : nodes) {
            if (node.getNodeType() == FlowNodeTypeEnum.START
                    || node.getNodeType() == FlowNodeTypeEnum.END
                    || node.getNodeType() == FlowNodeTypeEnum.CONDITION) {
                continue;
            }
            String elementId = "node_" + nodeCounter++;
            FlowElement element = createElement(node, elementId);
            elementIdByNodeId.put(node.getId(), elementId);
            process.addFlowElement(element);

            // 空策略=自动拒绝：节点入口排他网关——无人时转入拒绝处理器并终止
            if (requiresRejectGateway(node)) {
                String gatewayId = "rejectGateway_" + gatewayCounter++;
                ExclusiveGateway gateway = new ExclusiveGateway();
                gateway.setId(gatewayId);
                gateway.setName(node.getName() + "-审批人缺失判断");
                SequenceFlow toTask = new SequenceFlow(gatewayId, elementId);
                toTask.setId("flow_" + flowCounter++);
                gateway.setDefaultFlow(toTask.getId());
                process.addFlowElement(gateway);
                process.addFlowElement(toTask);

                ServiceTask rejectHandler = new ServiceTask();
                rejectHandler.setId("rejectHandler_" + gatewayCounter);
                rejectHandler.setName(node.getName());
                rejectHandler.setImplementationType("delegateExpression");
                rejectHandler.setImplementation("${oaAutoRejectDelegate}");
                process.addFlowElement(rejectHandler);

                SequenceFlow toReject = new SequenceFlow(gatewayId, rejectHandler.getId());
                toReject.setId("flow_" + flowCounter++);
                toReject.setConditionExpression("${" + WorkflowVariables.autoRejectVariable(node.getId()) + "}");
                process.addFlowElement(toReject);

                SequenceFlow rejectToEnd = new SequenceFlow(rejectHandler.getId(), end.getId());
                rejectToEnd.setId("flow_" + flowCounter++);
                process.addFlowElement(rejectToEnd);

                rejectGatewayByNodeId.put(node.getId(), gatewayId);
            }
        }

        // 转移边 → 序列流；多出边源节点插排他网关分发
        Map<Long, List<FlowTransition>> outBySource = new LinkedHashMap<>();
        for (FlowTransition transition : config.getTransitions()) {
            outBySource.computeIfAbsent(transition.getFromNode().getId(),
                    key -> new ArrayList<>()).add(transition);
        }
        for (Map.Entry<Long, List<FlowTransition>> entry : outBySource.entrySet()) {
            String sourceElementId = elementIdByNodeId.get(entry.getKey());
            if (sourceElementId == null) {
                continue;
            }
            List<FlowTransition> outEdges = entry.getValue();
            boolean multiOut = outEdges.size() > 1;
            String distributeFrom = sourceElementId;
            ExclusiveGateway gateway = null;
            if (multiOut) {
                gateway = new ExclusiveGateway();
                gateway.setId("gateway_" + gatewayCounter++);
                gateway.setName("分支判断");
                process.addFlowElement(gateway);
                SequenceFlow toGateway = new SequenceFlow(sourceElementId, gateway.getId());
                toGateway.setId("flow_" + flowCounter++);
                process.addFlowElement(toGateway);
                distributeFrom = gateway.getId();
            }
            FlowTransition defaultEdge = outEdges.stream()
                    .filter(edge -> !edge.isConditional())
                    .findFirst().orElse(null);
            for (FlowTransition edge : outEdges) {
                String targetElementId = resolveTargetElement(edge, elementIdByNodeId, end.getId());
                // 目标节点若有入口拒绝网关，入边改连网关（网关默认流再到任务）
                FlowNodeConfig toNode = edge.getToNode();
                if (toNode != null && rejectGatewayByNodeId.containsKey(toNode.getId())) {
                    targetElementId = rejectGatewayByNodeId.get(toNode.getId());
                }
                SequenceFlow flow = new SequenceFlow(distributeFrom, targetElementId);
                flow.setId("flow_" + flowCounter++);
                if (edge.isConditional()) {
                    flow.setConditionExpression(conditionExpression(edge));
                }
                process.addFlowElement(flow);
                if (multiOut && edge.equals(defaultEdge)) {
                    gateway.setDefaultFlow(flow.getId());
                }
            }
        }
        return model;
    }

    /** 是否需要"审批人缺失 → 自动拒绝"入口网关：人工审批 + 空策略为 AUTO_REJECT。 */
    private boolean requiresRejectGateway(FlowNodeConfig node) {
        return isManualApproval(node)
                && node.getEmptyStrategy() == EmptyAssigneeStrategyEnum.AUTO_REJECT;
    }

    private boolean isManualApproval(FlowNodeConfig node) {
        if (node.getNodeType() != FlowNodeTypeEnum.APPROVAL
                && node.getNodeType() != FlowNodeTypeEnum.HANDLER) {
            return false;
        }
        NodeApprovalModeEnum approvalMode = node.getApprovalMode() == null
                ? NodeApprovalModeEnum.MANUAL : node.getApprovalMode();
        return approvalMode == NodeApprovalModeEnum.MANUAL;
    }

    /** 解析转移边目标元素：无目标或 END 节点 → end event，否则目标节点元素。 */
    private String resolveTargetElement(
            FlowTransition edge, Map<Long, String> elementIdByNodeId, String endElementId) {
        FlowNodeConfig toNode = edge.getToNode();
        if (toNode == null || toNode.getNodeType() == FlowNodeTypeEnum.END) {
            return endElementId;
        }
        String elementId = elementIdByNodeId.get(toNode.getId());
        if (elementId == null) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                    "转移边目标节点无法编译：" + toNode.getName());
        }
        return elementId;
    }

    private FlowElement createElement(FlowNodeConfig node, String id) {
        if (node.getNodeType() == FlowNodeTypeEnum.CC) {
            return serviceTask(id, node.getName(), "${oaCcNodeDelegate}");
        }
        // 节点审批类型：自动通过/自动拒绝编译为服务任务，不经人工
        NodeApprovalModeEnum approvalMode = node.getApprovalMode() == null
                ? NodeApprovalModeEnum.MANUAL : node.getApprovalMode();
        if (approvalMode == NodeApprovalModeEnum.AUTO_PASS) {
            return serviceTask(id, node.getName(), "${oaAutoPassDelegate}");
        }
        if (approvalMode == NodeApprovalModeEnum.AUTO_REJECT) {
            return serviceTask(id, node.getName(), "${oaAutoRejectDelegate}");
        }
        UserTask task = new UserTask();
        task.setId(id);
        task.setName(node.getName());
        configureAssignee(task, node);
        return task;
    }

    private ServiceTask serviceTask(String id, String name, String delegateExpression) {
        ServiceTask task = new ServiceTask();
        task.setId(id);
        task.setName(name);
        task.setImplementationType("delegateExpression");
        task.setImplementation(delegateExpression);
        return task;
    }

    /** 按六维模型配置审批人：主体 × 范围 → 候选组或节点级候选人；多人方式 → 单任务或多实例。 */
    private void configureAssignee(UserTask task, FlowNodeConfig node) {
        AssigneeSubjectEnum subject = node.getAssigneeSubject();
        if (subject == null) {
            return;
        }
        ApproveModeEnum mode = node.getApproveMode() == null ? ApproveModeEnum.OR_SIGN : node.getApproveMode();
        AssigneeScopeEnum scope = node.getAssigneeScope() == null
                ? AssigneeScopeEnum.GLOBAL : node.getAssigneeScope();

        // 或签 + 角色 + 全局：静态候选组（组语义：成员变动实时生效，无需提交时解析）
        boolean staticCandidateGroups = mode == ApproveModeEnum.OR_SIGN
                && subject == AssigneeSubjectEnum.ROLE
                && scope == AssigneeScopeEnum.GLOBAL;
        if (staticCandidateGroups) {
            List<String> groupIds = split(node.getAssigneeValue());
            if (!groupIds.isEmpty()) {
                task.setCandidateGroups(groupIds);
            }
        } else {
            // 其余情况：候选人在提交时解析写入节点级变量，引擎按多人方式消费
            String candidatesVar = WorkflowVariables.candidatesVariable(node.getId());
            switch (mode) {
                case OR_SIGN -> multiInstance(task, candidatesVar, "orSignApprover", true,
                        "${nrOfCompletedInstances >= 1}");
                case AND_SIGN -> multiInstance(task, candidatesVar, "andSignApprover", true, null);
                case SEQUENTIAL -> multiInstance(task, candidatesVar, "seqApprover", false, null);
            }
        }
        // 空策略=自动通过：引擎原生跳过（对候选组与多实例同样生效）
        if (node.getEmptyStrategy() == EmptyAssigneeStrategyEnum.AUTO_PASS) {
            task.setSkipExpression("${" + WorkflowVariables.skipVariable(node.getId()) + "}");
        }
    }

    private void multiInstance(UserTask task, String inputDataItem, String elementVariable,
                               boolean parallel, String completionCondition) {
        MultiInstanceLoopCharacteristics multi = new MultiInstanceLoopCharacteristics();
        multi.setSequential(!parallel);
        // 集合以表达式形式引用流程变量（提交时写入的候选账号列表）
        multi.setInputDataItem("${" + inputDataItem + "}");
        multi.setElementVariable(elementVariable);
        if (completionCondition != null) {
            multi.setCompletionCondition(completionCondition);
        }
        task.setLoopCharacteristics(multi);
        task.setAssignee("${" + elementVariable + "}");
    }

    private static List<String> split(String value) {
        List<String> pieces = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return pieces;
        }
        for (String piece : value.split(",")) {
            if (!piece.isBlank()) {
                pieces.add(piece.trim());
            }
        }
        return pieces;
    }

    /** 条件表达式：单值比较走运算符；集合判断（IN / NOT_IN）展开为等值或链。 */
    private String conditionExpression(FlowTransition edge) {
        String variable = edge.getConditionVariable();
        return switch (edge.getOperator()) {
            case IN -> orChain(variable, edge.getExpectedValue(), false);
            case NOT_IN -> orChain(variable, edge.getExpectedValue(), true);
            default -> "${" + variable + " " + operatorSymbol(edge) + " " + expressionLiteral(edge.getExpectedValue()) + "}";
        };
    }

    /**
     * 集合判断展开：{@code IN} → {@code (v == a || v == b)}；{@code NOT_IN} → {@code (v != a && v != b)}。
     * 避免依赖引擎对集合迭代的原生支持，语义与钉钉"属于某几个部门"一致。
     */
    private String orChain(String variable, String expectedValue, boolean negated) {
        List<String> values = split(expectedValue);
        if (values.isEmpty()) {
            return "${false}";
        }
        String joiner = negated ? " && " : " || ";
        String operator = negated ? " != " : " == ";
        StringBuilder expression = new StringBuilder("${");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                expression.append(joiner);
            }
            expression.append(variable).append(operator).append(expressionLiteral(values.get(index)));
        }
        return expression.append("}").toString();
    }

    private String operatorSymbol(FlowTransition edge) {
        return switch (edge.getOperator()) {
            case EQUAL -> "==";
            case NOT_EQUAL -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case IN, NOT_IN -> "==";
        };
    }

    private String expressionLiteral(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("false") || value.matches("-?\\d+(\\.\\d+)?")) {
            return normalized;
        }
        return "'" + value.replace("'", "\\'") + "'";
    }
}
