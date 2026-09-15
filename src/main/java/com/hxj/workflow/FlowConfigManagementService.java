package com.hxj.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.FlowTransition;
import com.hxj.entity.SysRole;
import com.hxj.enums.ApproveModeEnum;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeSubjectEnum;
import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.EmptyAssigneeStrategyEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.FlowStatusEnum;
import com.hxj.enums.NodeApprovalModeEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.FlowNodeConfigRepository;
import com.hxj.repository.FlowTransitionRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 5.11/5.12 流程配置管理（图模型 + 发布两态）：
 * <ul>
 *   <li><b>保存</b>：完整校验（节点协议 / 转移边拓扑 / 图可达性）后落库为<b>草稿</b>——
 *       保存不等于生效，改错不污染任何单据；</li>
 *   <li><b>发布</b>：显式动作，完成后部署 BPMN 并推进版本号；仅已发布流程可被模板绑定提交，
 *       在途实例由 Flowable 定义版本隔离，天然按旧版本继续流转。</li>
 * </ul>
 */
@Service
public class FlowConfigManagementService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FlowConfigRepository flowConfigRepository;
    private final FlowNodeConfigRepository nodeRepository;
    private final FlowTransitionRepository transitionRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysUserRepository sysUserRepository;
    private final SysDepartmentRepository sysDepartmentRepository;
    private final OaDocumentRepository oaDocumentRepository;
    private final ConfigDrivenProcessDefinitionService definitionService;
    private final ConditionVariableCatalog conditionVariableCatalog;

    public FlowConfigManagementService(
            FlowConfigRepository flowConfigRepository,
            FlowNodeConfigRepository nodeRepository,
            FlowTransitionRepository transitionRepository,
            SysRoleRepository sysRoleRepository,
            SysUserRepository sysUserRepository,
            SysDepartmentRepository sysDepartmentRepository,
            OaDocumentRepository oaDocumentRepository,
            ConfigDrivenProcessDefinitionService definitionService,
            ConditionVariableCatalog conditionVariableCatalog) {
        this.flowConfigRepository = flowConfigRepository;
        this.nodeRepository = nodeRepository;
        this.transitionRepository = transitionRepository;
        this.sysRoleRepository = sysRoleRepository;
        this.sysUserRepository = sysUserRepository;
        this.sysDepartmentRepository = sysDepartmentRepository;
        this.oaDocumentRepository = oaDocumentRepository;
        this.definitionService = definitionService;
        this.conditionVariableCatalog = conditionVariableCatalog;
    }

    @Transactional(readOnly = true)
    public List<FlowConfigItems.Brief> list() {
        return flowConfigRepository.findAll().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(config -> new FlowConfigItems.Brief(
                        config.getId(),
                        config.getType(),
                        config.getCategory(),
                        config.getStatus(),
                        config.getVersion(),
                        config.getNodes().size(),
                        config.getNodes().stream().map(FlowNodeConfig::getName).toList(),
                        config.getTransitions().stream()
                                .filter(FlowTransition::isConditional)
                                .map(this::conditionHint)
                                .toList()))
                .toList();
    }

    /** 条件边摘要（列表页分支提示，如「会计（按部门）: amount≥20000 → 财务经理（大额）」）。 */
    private String conditionHint(FlowTransition transition) {
        String operator = switch (transition.getOperator()) {
            case EQUAL -> "=";
            case NOT_EQUAL -> "≠";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> "≥";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "≤";
            case IN -> "属于";
            case NOT_IN -> "不属于";
        };
        String target = transition.getToNode() == null ? "流程结束" : transition.getToNode().getName();
        return transition.getFromNode().getName() + ": " + transition.getConditionVariable()
                + operator + transition.getExpectedValue() + " → " + target;
    }

    /** 5.12 按业务单据类型返回完整节点与转移边（可视化流程图数据）。 */
    @Transactional(readOnly = true)
    public FlowConfigItems.Config detailByType(String type) {
        return toDetail(findByType(type));
    }

    @Transactional(readOnly = true)
    public FlowConfigItems.Config detail(Long id) {
        return toDetail(findById(id));
    }

    /** 新增流程配置：校验后落库为草稿（不部署——生效必须显式发布）。 */
    @Transactional
    public FlowConfigItems.Config create(FlowConfigItems.SaveFlowConfigRequest request) {
        // 新建时尚未绑定模板：条件变量按保留键兜底校验，绑定模板后自动获得该模板声明的字段
        validate(request, null);
        if (flowConfigRepository.findByType(request.type()).isPresent()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_EXISTS, "同名流程配置已存在");
        }
        FlowConfig config = new FlowConfig();
        config.setType(request.type());
        config.setCategory(request.category());
        config.setStatus(FlowStatusEnum.DRAFT);
        config.setVersion(0);
        applyPayload(config, request);
        return toDetail(flowConfigRepository.save(config));
    }

    /** 删除流程配置：已被单据引用的禁止删除（单据回溯审批链依赖 flow_config_id 关联）。 */
    @Transactional
    public void delete(Long id) {
        FlowConfig config = findById(id);
        long refs = oaDocumentRepository.countByFlowConfigId(id);
        if (refs > 0) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_IN_USE,
                    "该流程已被 " + refs + " 张单据引用，无法删除");
        }
        transitionRepository.deleteByConfigId(id);
        nodeRepository.deleteByConfigId(id);
        flowConfigRepository.delete(config);
    }

    /**
     * 修改流程配置：校验后整体重建节点与转移边，状态回退为草稿。
     * 已发布流程被修改后必须重新发布才能影响新单据；在途单据不受影响。
     */
    @Transactional
    public FlowConfigItems.Config update(Long id, FlowConfigItems.SaveFlowConfigRequest request) {
        validate(request, id);
        FlowConfig config = findById(id);
        if (!config.getType().equals(request.type())
                && flowConfigRepository.findByType(request.type()).isPresent()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_EXISTS, "同名流程配置已存在");
        }
        // 删除顺序：先删转移边（外键引用节点）再删节点；
        // bulk delete 的 clearAutomatically 会清空持久化上下文，必须重新加载实体再操作集合，
        // 否则集合操作发生在脱离会话的实例上（LazyInitializationException）
        transitionRepository.deleteByConfigId(id);
        nodeRepository.deleteByConfigId(id);
        FlowConfig managed = flowConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
        managed.getTransitions().clear();
        managed.getNodes().clear();
        managed.setType(request.type());
        managed.setCategory(request.category());
        managed.setStatus(FlowStatusEnum.DRAFT);
        applyPayload(managed, request);
        flowConfigRepository.saveAndFlush(managed);
        // 用原托管实体组装详情：save 对存在实体的 merge 返回副本，其集合为不可初始化代理
        return toDetail(managed);
    }

    /**
     * 发布流程：部署 BPMN 定义并推进版本号，状态置为已发布。
     * 已发布状态下重复发布为幂等操作（避免无意义的版本膨胀）。
     */
    @Transactional
    public FlowConfigItems.Config publish(Long id) {
        FlowConfig config = findById(id);
        if (config.getStatus() == FlowStatusEnum.PUBLISHED) {
            return toDetail(config);
        }
        definitionService.deploy(config.getId());
        config.setStatus(FlowStatusEnum.PUBLISHED);
        config.setVersion(config.getVersion() + 1);
        return toDetail(flowConfigRepository.save(config));
    }

    private void applyPayload(FlowConfig config, FlowConfigItems.SaveFlowConfigRequest request) {
        Map<String, FlowNodeConfig> nodeByName = new LinkedHashMap<>();
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload payload : request.nodes()) {
            FlowNodeConfig entity = new FlowNodeConfig(payload.name(), payload.nodeType());
            entity.setAssigneeSubject(payload.assigneeSubject());
            entity.setAssigneeValue(trimToNull(payload.assigneeValue()));
            entity.setAssigneeScope(payload.assigneeScope());
            entity.setAssigneeScopeValue(trimToNull(payload.assigneeScopeValue()));
            entity.setAssigneeLevel(payload.assigneeLevel());
            entity.setAssigneeChain(Boolean.TRUE.equals(payload.assigneeChain()));
            entity.setApproveMode(payload.approveMode() == null
                    ? ApproveModeEnum.OR_SIGN : payload.approveMode());
            entity.setEmptyStrategy(payload.emptyStrategy());
            entity.setEmptyFallback(trimToNull(payload.emptyFallback()));
            entity.setApprovalMode(payload.approvalMode() == null
                    ? NodeApprovalModeEnum.MANUAL : payload.approvalMode());
            entity.setCcTargets(payload.ccTargets());
            config.addNode(entity);
            nodeByName.put(payload.name(), entity);
        }
        for (FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload payload : request.transitions()) {
            FlowTransition transition = new FlowTransition();
            transition.setFromNode(nodeByName.get(payload.fromNodeName()));
            transition.setToNode(StringUtils.hasText(payload.toNodeName())
                    ? nodeByName.get(payload.toNodeName()) : null);
            transition.setConditionVariable(StringUtils.hasText(payload.variableName())
                    ? payload.variableName().trim() : null);
            transition.setOperator(payload.operator());
            transition.setExpectedValue(payload.expectedValue());
            config.addTransition(transition);
        }
    }

    /** 保存关口完整校验：节点协议、转移边拓扑、图可达性。坏配置在此被拒，绝不流入运行时。 */
    private void validate(FlowConfigItems.SaveFlowConfigRequest request, Long flowConfigId) {
        if (request == null || !StringUtils.hasText(request.type()) || request.category() == null) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_INFO_REQUIRED, "流程类型与分类不能为空");
        }
        if (request.nodes() == null || request.nodes().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODES_REQUIRED, "流程节点不能为空");
        }
        if (request.transitions() == null || request.transitions().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODES_REQUIRED, "流程转移边不能为空");
        }
        boolean hasApprovalNode = request.nodes().stream()
                .anyMatch(node -> node.nodeType() == FlowNodeTypeEnum.APPROVAL);
        if (!hasApprovalNode) {
            throw new BusinessException(ErrorCodeEnum.FLOW_APPROVAL_NODE_REQUIRED, "流程至少需要一个审批节点");
        }

        // —— 节点校验：名称唯一 + 结构化指派协议 ——
        Set<String> seenNodeNames = new HashSet<>();
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            if (!StringUtils.hasText(node.name()) || node.nodeType() == null) {
                throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "节点名称与类型不能为空");
            }
            if (!seenNodeNames.add(node.name())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_NODE_DUPLICATE, "流程节点名称不能重复：" + node.name());
            }
            // 人工审批节点必须指定审批主体；自动通过/自动拒绝节点由服务任务处理，无需主体
            boolean manualApproval = (node.nodeType() == FlowNodeTypeEnum.APPROVAL
                    || node.nodeType() == FlowNodeTypeEnum.HANDLER)
                    && (node.approvalMode() == null
                    || node.approvalMode() == NodeApprovalModeEnum.MANUAL);
            if (manualApproval && node.assigneeSubject() == null) {
                throw new BusinessException(ErrorCodeEnum.FLOW_ASSIGNEE_REQUIRED,
                        "审批节点需指定审批主体：" + node.name());
            }
            if (node.assigneeSubject() != null) {
                validateAssignee(node);
            }
            // 抄送目标在保存关口做完整校验：坏配置若流入运行时，会在流程走到抄送节点时
            // 才解析失败，单据卡死在最后一步且无管理端修复手段（错误延迟爆炸）
            if (node.nodeType() == FlowNodeTypeEnum.CC && StringUtils.hasText(node.ccTargets())) {
                validateCcTargets(node.ccTargets());
            }
        }

        // —— 转移边校验：端点存在、条件合法、默认边唯一 ——
        // 可用条件变量 = 该流程绑定模板中声明"参与流程条件"的字段 ∪ 保留键兜底（字段即变量）
        Map<String, WorkflowVariables.ValueTypeEnum> availableVariables =
                conditionVariableCatalog.available(flowConfigId);
        Map<String, List<FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload>> outBySource =
                new LinkedHashMap<>();
        for (FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload edge : request.transitions()) {
            if (!StringUtils.hasText(edge.fromNodeName())
                    || !seenNodeNames.contains(edge.fromNodeName())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_RULE_TARGET_INVALID,
                        "转移边的源节点不在节点列表中：" + edge.fromNodeName());
            }
            if (StringUtils.hasText(edge.toNodeName())
                    && !seenNodeNames.contains(edge.toNodeName())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_RULE_TARGET_INVALID,
                        "转移边的目标节点不在节点列表中：" + edge.toNodeName());
            }
            boolean conditional = StringUtils.hasText(edge.variableName());
            if (conditional) {
                if (edge.operator() == null || !StringUtils.hasText(edge.expectedValue())) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                            "条件转移不完整（需操作符与期望值）：" + edge.fromNodeName());
                }
                validateConditionValue(edge.variableName().trim(), edge.operator(),
                        edge.expectedValue(), availableVariables);
            }
            outBySource.computeIfAbsent(edge.fromNodeName(), key -> new ArrayList<>()).add(edge);
        }

        // —— 图拓扑校验：每节点有出边、无条件出边至多一条、START 可达全部节点且全部节点可达结束 ——
        Set<String> endNodeNames = request.nodes().stream()
                .filter(node -> node.nodeType() == FlowNodeTypeEnum.END)
                .map(FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload::name)
                .collect(java.util.stream.Collectors.toSet());
        String startName = request.nodes().stream()
                .filter(node -> node.nodeType() == FlowNodeTypeEnum.START)
                .map(FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload::name)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "流程缺少开始节点"));
        for (Map.Entry<String, List<FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload>> entry
                : outBySource.entrySet()) {
            long unconditional = entry.getValue().stream()
                    .filter(edge -> !StringUtils.hasText(edge.variableName()))
                    .count();
            if (unconditional > 1) {
                throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                        "同一节点的无条件出边至多一条：" + entry.getKey());
            }
            if (entry.getValue().stream().anyMatch(edge -> StringUtils.hasText(edge.variableName()))
                    && unconditional == 0) {
                throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                        "存在条件出边的节点必须有一条无条件兜底出边：" + entry.getKey());
            }
        }
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            // END 节点自身是结束标记，无需出边；其余节点必须有出边
            if (!endNodeNames.contains(node.name())
                    && !outBySource.containsKey(node.name())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                        "节点缺少出边（流程会在该节点后悬挂）：" + node.name());
            }
        }
        validateReachability(request, startName, endNodeNames);
    }

    /**
     * 审批人六维配置校验：主体必填、主体参数按主体解释、范围/层级/空策略各自合法。
     * 保存关口是唯一入口——校验通过即保证编译与运行期不会求值失败。
     */
    private void validateAssignee(FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node) {
        AssigneeSubjectEnum subject = node.assigneeSubject();
        if (subject == null) {
            throw new BusinessException(ErrorCodeEnum.FLOW_ASSIGNEE_REQUIRED,
                    "审批节点需指定审批主体：" + node.name());
        }
        switch (subject) {
            case MEMBER -> {
                if (!StringUtils.hasText(node.assigneeValue())) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_ASSIGNEE_REQUIRED,
                            "指定成员节点需提供账号：" + node.name());
                }
                for (String account : split(node.assigneeValue())) {
                    if (sysUserRepository.findByAccount(account).isEmpty()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                                "审批人账号不存在：" + account + "（节点 " + node.name() + "）");
                    }
                }
            }
            case ROLE -> {
                if (!StringUtils.hasText(node.assigneeValue())) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_ASSIGNEE_REQUIRED,
                            "角色节点需提供角色ID：" + node.name());
                }
                for (String roleId : split(node.assigneeValue())) {
                    if (!roleId.matches("\\d+")
                            || sysRoleRepository.findById(Long.valueOf(roleId)).isEmpty()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                                "审批人引用的角色不存在（ID=" + roleId + "）：" + node.name());
                    }
                }
            }
            case FORM_MEMBER -> requireFieldKey(node, "表单联系人节点需指定人员控件字段");
            case SUPERIOR, DEPT_HEAD -> validateLevel(node);
            case INITIATOR, INITIATOR_SELECT -> {
                // 无主体参数：自选节点可选配置范围，由前端约束
            }
        }
        // 组织范围：仅角色与部门主管支持，FORM_DEPT 需给出部门控件字段键
        if (node.assigneeScope() != null) {
            if (subject != AssigneeSubjectEnum.ROLE && subject != AssigneeSubjectEnum.DEPT_HEAD) {
                throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                        "该审批主体不支持组织范围：" + node.name());
            }
            if (node.assigneeScope() == AssigneeScopeEnum.FORM_DEPT) {
                requireFieldKey(node, "按表单部门范围需指定部门控件字段");
            }
        }
        // 空策略：转交指定成员必须给出真实存在的账号
        if (node.emptyStrategy() == EmptyAssigneeStrategyEnum.TO_USER
                && !StringUtils.hasText(node.emptyFallback())) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                    "空策略为「转交指定成员」时必须提供兜底账号：" + node.name());
        }
        if (StringUtils.hasText(node.emptyFallback())
                && sysUserRepository.findByAccount(node.emptyFallback().trim()).isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                    "空策略兜底账号不存在：" + node.emptyFallback());
        }
    }

    /** 主管层级校验：钉钉最高 8 级。 */
    private void validateLevel(FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node) {
        Integer level = node.assigneeLevel();
        if (level != null && (level < 1 || level > 8)) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                    "主管层级需在 1-8 之间：" + node.name());
        }
    }

    /** 表单字段键校验（表单联系人 / 部门控件范围）：保存流程时可早于模板绑定，故只校验非空。 */
    private void requireFieldKey(FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node, String message) {
        String fieldKey = node.assigneeSubject() == AssigneeSubjectEnum.FORM_MEMBER
                ? node.assigneeValue() : node.assigneeScopeValue();
        if (!StringUtils.hasText(fieldKey)) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, message + "：" + node.name());
        }
    }

    private static List<String> split(String value) {
        List<String> pieces = new ArrayList<>();
        if (value == null) {
            return pieces;
        }
        for (String piece : value.split(",")) {
            if (!piece.isBlank()) {
                pieces.add(piece.trim());
            }
        }
        return pieces;
    }

    /**
     * 条件比较值校验：变量须在可用目录内，比较值须与变量类型匹配，集合判断逐值校验。
     * 不匹配会在网关求值时抛异常导致流程永久卡死，故必须挡在保存关口。
     */
    private void validateConditionValue(String variableName, ConditionOperatorEnum operator,
                                        String expectedValue,
                                        Map<String, WorkflowVariables.ValueTypeEnum> availableVariables) {
        WorkflowVariables.ValueTypeEnum type = availableVariables.get(variableName);
        if (type == null) {
            throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                    "条件变量不可用：" + variableName
                            + "（可用变量：模板中勾选“参与流程条件”的字段、系统字段、保留键；当前可用："
                            + String.join(" / ", availableVariables.keySet()) + "）");
        }
        boolean collection = operator == ConditionOperatorEnum.IN || operator == ConditionOperatorEnum.NOT_IN;
        List<String> values = collection ? split(expectedValue) : List.of(expectedValue);
        if (values.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                    variableName + " 的集合条件至少需要一个比较值");
        }
        for (String value : values) {
            validateSingleValue(variableName, type, value);
        }
    }

    /** 单个比较值的类型匹配校验。 */
    private void validateSingleValue(String variableName, WorkflowVariables.ValueTypeEnum type, String value) {
        switch (type) {
            case NUMERIC -> {
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                            variableName + " 条件的比较值必须是数字：" + value);
                }
            }
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                            variableName + " 条件的比较值只能是 true / false");
                }
            }
            case STRING -> {
                // 字符串变量接受任意字面值
            }
        }
    }

    /**
     * 图可达性：START 能到达全部节点，且全部节点能到达「结束」（toNode=null 的边或 END 节点）。
     * 保证配置出的流程图不存在孤岛与死分支。
     */
    private void validateReachability(
            FlowConfigItems.SaveFlowConfigRequest request, String startName, Set<String> endNodeNames) {
        Map<String, List<String>> forward = new HashMap<>();
        Map<String, List<String>> backward = new HashMap<>();
        for (FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload edge : request.transitions()) {
            String target = StringUtils.hasText(edge.toNodeName()) ? edge.toNodeName() : null;
            forward.computeIfAbsent(edge.fromNodeName(), key -> new ArrayList<>());
            if (target != null) {
                forward.get(edge.fromNodeName()).add(target);
                backward.computeIfAbsent(target, key -> new ArrayList<>()).add(edge.fromNodeName());
            }
        }
        Set<String> reachableFromStart = collectReachable(startName, forward);
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            if (!reachableFromStart.contains(node.name())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                        "节点从开始节点不可达（孤岛）：" + node.name());
            }
        }
        Set<String> canReachEnd = new HashSet<>(endNodeNames);
        Deque<String> queue = new ArrayDeque<>(endNodeNames);
        for (Map.Entry<String, List<String>> entry : forward.entrySet()) {
            if (entry.getValue().isEmpty()
                    || entry.getValue().stream().anyMatch(target -> endNodeNames.contains(target))) {
                // 源节点的出边直接指向结束（toNode=null 的边不进 forward）也视为可达
            }
        }
        // 无条件直达结束：出边中含 toNodeName 为空的源节点
        for (FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload edge : request.transitions()) {
            if (!StringUtils.hasText(edge.toNodeName())) {
                canReachEnd.add(edge.fromNodeName());
                queue.add(edge.fromNodeName());
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String predecessor : backward.getOrDefault(current, List.of())) {
                if (canReachEnd.add(predecessor)) {
                    queue.add(predecessor);
                }
            }
        }
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            if (!canReachEnd.contains(node.name())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                        "节点无法到达流程结束（死分支）：" + node.name());
            }
        }
    }

    private Set<String> collectReachable(String start, Map<String, List<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (String next : adjacency.getOrDefault(queue.poll(), List.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    /**
     * 抄送目标校验：合法 JSON 数组，每项 type ∈ {ROLE, DEPT, USER} 且引用真实存在
     * （与运行时 {@code OaCcNodeDelegate} 语义一致：ROLE / DEPT 按<b>数字 ID</b>，USER 按账号）。
     * 名称匹配已退役——改名不再导致抄送静默失配。
     */
    private void validateCcTargets(String ccTargets) {
        List<Map<String, String>> targets;
        try {
            targets = JSON.readValue(ccTargets, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                    "抄送目标格式错误：应为 JSON 数组 [{\"type\":\"ROLE|DEPT|USER\",\"value\":\"...\"}]");
        }
        for (Map<String, String> target : targets) {
            String type = target.getOrDefault("type", "");
            String value = target.getOrDefault("value", "");
            if (!StringUtils.hasText(value)) {
                throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "抄送目标的 value 不能为空");
            }
            switch (type) {
                case "ROLE" -> {
                    if (!isNumericId(value) || sysRoleRepository.findById(Long.valueOf(value)).isEmpty()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                                "抄送角色不存在（应为角色ID）：" + value);
                    }
                }
                case "USER" -> {
                    if (!sysUserRepository.findByAccount(value).isPresent()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "抄送账号不存在：" + value);
                    }
                }
                case "DEPT" -> {
                    if (!isNumericId(value)
                            || sysDepartmentRepository.findById(Long.valueOf(value)).isEmpty()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                                "抄送部门不存在（应为部门ID）：" + value);
                    }
                }
                default -> throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                        "抄送目标类型必须是 ROLE / DEPT / USER：" + type);
            }
        }
    }

    /** 数字 ID 判定（抄送目标的 ROLE / DEPT 引用契约）。 */
    private static boolean isNumericId(String value) {
        return value != null && value.matches("\\d+");
    }

    private FlowConfigItems.Config toDetail(FlowConfig config) {
        List<FlowConfigItems.NodeConfig> nodes = config.getNodes().stream()
                .map(node -> new FlowConfigItems.NodeConfig(
                        node.getName(), node.getNodeType(),
                        node.getAssigneeSubject(), node.getAssigneeValue(),
                        resolveAssigneeText(node),
                        node.getAssigneeScope(), node.getAssigneeScopeValue(),
                        node.getAssigneeLevel(), node.isAssigneeChain(),
                        node.getApproveMode(), node.getEmptyStrategy(), node.getEmptyFallback(),
                        node.getApprovalMode(), node.getCcTargets()))
                .toList();
        List<FlowConfigItems.Transition> transitions = config.getTransitions().stream()
                .sorted(java.util.Comparator.comparingInt(FlowTransition::getSortOrder))
                .map(transition -> new FlowConfigItems.Transition(
                        transition.getFromNode().getName(),
                        transition.getToNode() == null ? null : transition.getToNode().getName(),
                        transition.getConditionVariable(), transition.getOperator(),
                        transition.getExpectedValue(), transition.getSortOrder()))
                .toList();
        return new FlowConfigItems.Config(
                config.getId(), config.getType(), config.getCategory(),
                config.getStatus(), config.getVersion(), nodes, transitions);
    }

    /** 主体参数展示文本：角色 ID → 角色名；成员账号 → 姓名；其余原样（解析不到时保留原值便于发现问题）。 */
    private String resolveAssigneeText(FlowNodeConfig node) {
        String value = node.getAssigneeValue();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (node.getAssigneeSubject() == AssigneeSubjectEnum.ROLE) {
            List<String> names = new ArrayList<>();
            for (String piece : split(value)) {
                names.add(piece.matches("\\d+")
                        ? sysRoleRepository.findById(Long.valueOf(piece))
                                .map(SysRole::getName).orElse(piece)
                        : piece);
            }
            return String.join("、", names);
        }
        if (node.getAssigneeSubject() == AssigneeSubjectEnum.MEMBER) {
            List<String> names = new ArrayList<>();
            for (String piece : split(value)) {
                names.add(sysUserRepository.findByAccount(piece)
                        .map(user -> user.getName()).orElse(piece));
            }
            return String.join("、", names);
        }
        return value;
    }

    /** 空白归一化为 null（避免空串落库后与"未配置"混淆）。 */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private FlowConfig findByType(String type) {
        return flowConfigRepository.findByType(type)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
    }

    private FlowConfig findById(Long id) {
        return flowConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
    }
}
