package com.hxj.workflow;
import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.FlowTransition;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeTypeEnum;
import com.hxj.enums.FlowNodeTypeEnum;
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

/**
 * 将数据库中的流程图（节点 + 转移边）编译并部署为 BPMN 2.0 定义。
 *
 * <p>编译规则（图模型，替代历史"线性链 + 条件规则命中跳转"）：
 * <ul>
 *   <li>START 节点编译为 StartEvent，其余节点各建一个元素（CC → ServiceTask，其余 → UserTask）；</li>
 *   <li>转移边编译为序列流：{@code toNode == null} 或指向 END 节点 → 连接 EndEvent；</li>
 *   <li>某节点出边多于一条时，在它之后插入排他网关：条件边按优先级带表达式，
 *       无条件边（每源至多一条，保存关口已校验）作为默认流兜底；</li>
 *   <li>条件表达式为裸 JUEL，变量与比较值合法性已由保存关口按 {@link WorkflowVariables} 契约校验。</li>
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

        // 节点元素表：START → start event；END 类型节点不建元素（出边直达 end）；
        // 其余按展示顺序建 node_<sortOrder>（按下标取用，避免早期重名覆盖丢节点的问题）
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
        for (FlowNodeConfig node : nodes) {
            if (node.getNodeType() == FlowNodeTypeEnum.START
                    || node.getNodeType() == FlowNodeTypeEnum.END
                    || node.getNodeType() == FlowNodeTypeEnum.CONDITION) {
                continue;
            }
            // 活动 id 按可执行节点顺序编号（node_0 起）——驳回目标定位等按此约定引用
            FlowElement element = createElement(node, "node_" + nodeCounter++);
            elementIdByNodeId.put(node.getId(), element.getId());
            process.addFlowElement(element);
        }

        // 转移边 → 序列流；多出边源节点插排他网关分发
        List<FlowTransition> transitions = config.getTransitions();
        Map<Long, List<FlowTransition>> outBySource = new LinkedHashMap<>();
        for (FlowTransition transition : transitions) {
            outBySource.computeIfAbsent(transition.getFromNode().getId(),
                    key -> new ArrayList<>()).add(transition);
        }
        int gatewayCounter = 0;
        int flowCounter = 0;
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
                SequenceFlow flow = new SequenceFlow(distributeFrom, targetElementId);
                flow.setId("flow_" + flowCounter++);
                if (edge.isConditional()) {
                    flow.setConditionExpression(conditionExpression(edge));
                }
                process.addFlowElement(flow);
                if (multiOut && edge.equals(defaultEdge)) {
                    // 无条件边作为网关默认流：全部条件不命中时走它
                    gateway.setDefaultFlow(flow.getId());
                }
            }
        }
        return model;
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
        // 结构化指派协议（AssigneeTypeEnum）：节点名仅展示，路由行为由协议决定
        AssigneeTypeEnum assigneeType = node.getAssigneeType();
        if (assigneeType == null) {
            return task;
        }
        switch (assigneeType) {
            case INITIATOR ->
                    // 发起人回环节点（签收/归还/上传归档附件等）：动态指派给单据申请人
                    task.setAssignee("${" + WorkflowVariables.INITIATOR + "}");
            case MANAGER ->
                    // 直属主管：主管链解析器产出链首（人工指定优先→部门负责人树兜底）
                    task.setAssignee("${" + WorkflowVariables.MANAGER_ACCOUNT + "}");
            case MANAGER_CHAIN -> {
                    // 逐级主管：沿主管链自下而上串行逐级审批（提交时写入链集合）
                    task.setAssignee("${chainManager}");
                    org.flowable.bpmn.model.MultiInstanceLoopCharacteristics multi =
                            new org.flowable.bpmn.model.MultiInstanceLoopCharacteristics();
                    multi.setSequential(true);
                    multi.setInputDataItem(WorkflowVariables.MANAGER_CHAIN);
                    multi.setElementVariable("chainManager");
                    task.setLoopCharacteristics(multi);
            }
            case SELF_SELECT -> {
                    // 发起人自选：申请人提交时指定审批人，按选择顺序串行审批
                    task.setAssignee("${selectedApprover}");
                    org.flowable.bpmn.model.MultiInstanceLoopCharacteristics multi =
                            new org.flowable.bpmn.model.MultiInstanceLoopCharacteristics();
                    multi.setSequential(true);
                    multi.setInputDataItem(WorkflowVariables.APPROVER_CHAIN);
                    multi.setElementVariable("selectedApprover");
                    task.setLoopCharacteristics(multi);
            }
            case ROLE -> {
                if (node.getAssigneeScope() == AssigneeScopeEnum.INITIATOR_DEPT) {
                    // 按发起人部门过滤的角色成员（如核算会计、部门HR）：提交时解析主办账号，
                    // 写入节点级变量动态指派——同一流程多个此类节点各自独立
                    task.setAssignee("${" + WorkflowVariables.scopedAssigneeVariable(node.getId()) + "}");
                } else if (node.getAssigneeValue() != null && !node.getAssigneeValue().isBlank()) {
                    // 全局角色：静态候选组（候选组 = 角色 ID，角色改名不影响路由）
                    List<String> groupIds = new ArrayList<>();
                    for (String piece : node.getAssigneeValue().split(",")) {
                        if (!piece.isBlank()) {
                            groupIds.add(piece.trim());
                        }
                    }
                    task.setCandidateGroups(groupIds);
                }
            }
        }
        return task;
    }

    private String conditionExpression(FlowTransition edge) {
        return "${" + edge.getConditionVariable() + " "
                + operatorSymbol(edge) + " " + expressionLiteral(edge.getExpectedValue()) + "}";
    }

    private String operatorSymbol(FlowTransition edge) {
        return switch (edge.getOperator()) {
            case EQUAL -> "==";
            case NOT_EQUAL -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
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
