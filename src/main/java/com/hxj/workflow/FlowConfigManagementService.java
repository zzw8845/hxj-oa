package com.hxj.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.common.ErrorCodeEnum;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowConditionRule;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConditionRuleRepository;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.FlowNodeConfigRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 5.11/5.12 流程配置管理：列表/详情查询、新增与修改节点链和条件分支；
 * 修改保存后重新部署 BPMN 新版本，保证新提交单据按新流程流转。
 */
@Service
public class FlowConfigManagementService {

    /** 条件规则可引用的流程变量白名单——与提交链路 {@code workflowVariables} 写入的变量一一对应。 */
    private static final Set<String> CONDITION_VARIABLES =
            Set.of("amount", "involvesFunds", "requiresAdminReview", "businessMode");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FlowConfigRepository flowConfigRepository;
    private final FlowNodeConfigRepository nodeRepository;
    private final FlowConditionRuleRepository conditionRuleRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysUserRepository sysUserRepository;
    private final SysDepartmentRepository sysDepartmentRepository;
    private final OaDocumentRepository oaDocumentRepository;
    private final ConfigDrivenProcessDefinitionService definitionService;
    public FlowConfigManagementService(
            FlowConfigRepository flowConfigRepository,
            FlowNodeConfigRepository nodeRepository,
            FlowConditionRuleRepository conditionRuleRepository,
            SysRoleRepository sysRoleRepository,
            SysUserRepository sysUserRepository,
            SysDepartmentRepository sysDepartmentRepository,
            OaDocumentRepository oaDocumentRepository,
            ConfigDrivenProcessDefinitionService definitionService) {
        this.flowConfigRepository = flowConfigRepository;
        this.nodeRepository = nodeRepository;
        this.conditionRuleRepository = conditionRuleRepository;
        this.sysRoleRepository = sysRoleRepository;
        this.sysUserRepository = sysUserRepository;
        this.sysDepartmentRepository = sysDepartmentRepository;
        this.oaDocumentRepository = oaDocumentRepository;
        this.definitionService = definitionService;
    }

    @Transactional(readOnly = true)
    public List<FlowConfigItems.Brief> list() {
        return flowConfigRepository.findAll().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(config -> new FlowConfigItems.Brief(
                        config.getId(),
                        config.getType(),
                        config.getCategory(),
                        config.getNodes().size(),
                        config.getNodes().stream().map(FlowNodeConfig::getName).toList()))
                .toList();
    }

    /** 5.12 按业务单据类型返回完整节点链（可视化链条数据）。 */
    @Transactional(readOnly = true)
    public FlowConfigItems.Config detailByType(String type) {
        return toDetail(findByType(type));
    }

    @Transactional(readOnly = true)
    public FlowConfigItems.Config detail(Long id) {
        return toDetail(findById(id));
    }

    @Transactional
    public FlowConfigItems.Config create(FlowConfigItems.SaveFlowConfigRequest request) {
        validate(request);
        if (flowConfigRepository.findByType(request.type()).isPresent()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_EXISTS, "同名流程配置已存在");
        }
        FlowConfig config = new FlowConfig();
        config.setType(request.type());
        config.setCategory(request.category());
        applyPayload(config, request);
        FlowConfig saved = flowConfigRepository.save(config);
        definitionService.deploy(saved.getId());
        return toDetail(saved);
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
        nodeRepository.deleteByConfigId(id);
        conditionRuleRepository.deleteByConfigId(id);
        flowConfigRepository.delete(config);
    }

    /** 修改后重新部署流程定义：新提交单据按新流程流转，在途单据不受影响。 */
    @Transactional
    public FlowConfigItems.Config update(Long id, FlowConfigItems.SaveFlowConfigRequest request) {
        validate(request);
        FlowConfig config = findById(id);
        if (!config.getType().equals(request.type())
                && flowConfigRepository.findByType(request.type()).isPresent()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_EXISTS, "同名流程配置已存在");
        }
        // 两张子表都有 (flow_config_id, sort_order) 唯一键：必须先删后插，
        // 否则 clear + 重加的 flush 顺序可能先插后删，在 MySQL 上触发 Duplicate entry
        nodeRepository.deleteByConfigId(id);
        conditionRuleRepository.deleteByConfigId(id);
        config.setType(request.type());
        config.setCategory(request.category());
        applyPayload(config, request);
        FlowConfig saved = flowConfigRepository.save(config);
        definitionService.deploy(saved.getId());
        return toDetail(saved);
    }

    private void applyPayload(FlowConfig config, FlowConfigItems.SaveFlowConfigRequest request) {
        config.getNodes().clear();
        config.getConditionRules().clear();
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            FlowNodeConfig entity = new FlowNodeConfig(node.name(), node.nodeType());
            entity.setAssigneeRole(node.assigneeRole());
            entity.setCcTargets(node.ccTargets());
            config.addNode(entity);
        }
        for (FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload rule : request.conditionRules()) {
            config.addConditionRule(new FlowConditionRule(
                    rule.variableName(), rule.operator(), rule.expectedValue(), rule.targetNodeName()));
        }
    }

    private void validate(FlowConfigItems.SaveFlowConfigRequest request) {
        if (request == null || !StringUtils.hasText(request.type()) || request.category() == null) {
            throw new BusinessException(ErrorCodeEnum.FLOW_CONFIG_INFO_REQUIRED, "流程类型与分类不能为空");
        }
        if (request.nodes() == null || request.nodes().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.FLOW_NODES_REQUIRED, "流程节点链不能为空");
        }
        boolean hasApprovalNode = request.nodes().stream()
                .anyMatch(node -> node.nodeType() == FlowNodeTypeEnum.APPROVAL);
        if (!hasApprovalNode) {
            throw new BusinessException(ErrorCodeEnum.FLOW_APPROVAL_NODE_REQUIRED, "流程至少需要一个审批节点");
        }
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            if (!StringUtils.hasText(node.name()) || node.nodeType() == null) {
                throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "节点名称与类型不能为空");
            }
            if (node.nodeType() == FlowNodeTypeEnum.APPROVAL && !StringUtils.hasText(node.assigneeRole())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_ASSIGNEE_REQUIRED, "审批节点需指定审批角色");
            }
            // 审批人必须引用真实存在的角色名（多角色用 / 、 分隔），
            // 否则会部署出任何用户都无法命中的候选组，节点任务将无人可审
            // 仅静态角色节点校验"审批人引用的角色必须存在"；动态指派节点不引用 RBAC 角色
            boolean dynamicAssignee = !NodeAssigneeRuleEnum
                    .of(node.name(), node.assigneeRole()).isStaticRole();
            if (node.nodeType() == FlowNodeTypeEnum.APPROVAL && StringUtils.hasText(node.assigneeRole())
                    && !dynamicAssignee) {
                for (String role : node.assigneeRole().split("[/、]")) {
                    String roleName = role.trim();
                    if (!StringUtils.hasText(roleName)) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                                "审批人角色名不能为空：" + node.assigneeRole());
                    }
                    if (!sysRoleRepository.findByName(roleName).isPresent()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                                "审批人引用的角色不存在：" + roleName);
                    }
                }
            }
            // 抄送目标在保存关口做完整校验：坏配置若流入运行时，会在流程走到抄送节点时
            // 才解析失败，单据卡死在最后一步且无管理端修复手段（错误延迟爆炸）
            if (node.nodeType() == FlowNodeTypeEnum.CC && StringUtils.hasText(node.ccTargets())) {
                validateCcTargets(node.ccTargets());
            }
        }
        // 节点名是 BPMN 编译期与驳回层级匹配的唯一标识，重名会导致节点被静默覆盖、审批环节丢失
        Set<String> seenNodeNames = new HashSet<>();
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            if (!seenNodeNames.add(node.name())) {
                throw new BusinessException(ErrorCodeEnum.FLOW_NODE_DUPLICATE, "流程节点名称不能重复：" + node.name());
            }
        }
        if (request.conditionRules() != null) {
            for (FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload rule : request.conditionRules()) {
                if (!StringUtils.hasText(rule.variableName()) || rule.operator() == null
                        || !StringUtils.hasText(rule.expectedValue())
                        || !StringUtils.hasText(rule.targetNodeName())) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID, "条件分支规则不完整");
                }
                // 变量白名单：条件表达式为裸 JUEL，引用不存在的变量会在网关求值时抛异常，
                // 流程实例永久卡死且无修复手段——必须在保存关口拒绝
                if (!CONDITION_VARIABLES.contains(rule.variableName())) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                            "条件变量不可用：" + rule.variableName()
                                    + "（可用变量：amount / involvesFunds / requiresAdminReview / businessMode）");
                }
                // 比较值与变量类型匹配：数值变量配非数字、布尔变量配非布尔，
                // 同样会在网关求值时抛类型转换异常卡死流程
                if ("amount".equals(rule.variableName())) {
                    try {
                        new BigDecimal(rule.expectedValue());
                    } catch (NumberFormatException e) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                                "amount 条件的比较值必须是数字：" + rule.expectedValue());
                    }
                }
                if (("involvesFunds".equals(rule.variableName())
                        || "requiresAdminReview".equals(rule.variableName()))
                        && !"true".equalsIgnoreCase(rule.expectedValue())
                        && !"false".equalsIgnoreCase(rule.expectedValue())) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_INVALID,
                            rule.variableName() + " 条件的比较值只能是 true / false");
                }
                boolean targetExists = request.nodes().stream()
                        .anyMatch(node -> node.name().equals(rule.targetNodeName()));
                if (!targetExists) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_TARGET_INVALID, "条件规则目标节点不在节点链中");
                }
            }
        }
    }

    /**
     * 抄送目标校验：合法 JSON 数组，每项 type ∈ {ROLE, DEPT, USER} 且 value 真实存在
     * （与运行时 {@code OaCcNodeDelegate} 的解析语义一致：ROLE 按角色名、DEPT 按部门名、USER 按账号）。
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
                    if (!sysRoleRepository.findByName(value).isPresent()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "抄送角色不存在：" + value);
                    }
                }
                case "USER" -> {
                    if (!sysUserRepository.findByAccount(value).isPresent()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "抄送账号不存在：" + value);
                    }
                }
                case "DEPT" -> {
                    if (!sysDepartmentRepository.findByName(value).isPresent()) {
                        throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID, "抄送部门不存在：" + value);
                    }
                }
                default -> throw new BusinessException(ErrorCodeEnum.FLOW_NODE_INVALID,
                        "抄送目标类型必须是 ROLE / DEPT / USER：" + type);
            }
        }
    }

    private FlowConfigItems.Config toDetail(FlowConfig config) {
        List<FlowConfigItems.NodeConfig> nodes = config.getNodes().stream()
                .map(node -> new FlowConfigItems.NodeConfig(
                        node.getName(), node.getNodeType(), node.getAssigneeRole()))
                .toList();
        List<FlowConfigItems.ConditionRule> rules = config.getConditionRules().stream()
                .map(rule -> new FlowConfigItems.ConditionRule(
                        rule.getVariableName(), rule.getOperator(),
                        rule.getExpectedValue(), rule.getTargetNodeName()))
                .toList();
        return new FlowConfigItems.Config(
                config.getId(), config.getType(), config.getCategory(), nodes, rules);
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
