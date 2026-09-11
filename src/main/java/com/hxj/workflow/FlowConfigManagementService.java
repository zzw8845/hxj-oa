package com.hxj.workflow;

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
import com.hxj.repository.SysRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 5.11/5.12 流程配置管理：列表/详情查询、新增与修改节点链和条件分支；
 * 修改保存后重新部署 BPMN 新版本，保证新提交单据按新流程流转。
 */
@Service
public class FlowConfigManagementService {

    private final FlowConfigRepository flowConfigRepository;
    private final FlowNodeConfigRepository nodeRepository;
    private final FlowConditionRuleRepository conditionRuleRepository;
    private final SysRoleRepository sysRoleRepository;
    private final OaDocumentRepository oaDocumentRepository;
    private final ConfigDrivenProcessDefinitionService definitionService;
    public FlowConfigManagementService(
            FlowConfigRepository flowConfigRepository,
            FlowNodeConfigRepository nodeRepository,
            FlowConditionRuleRepository conditionRuleRepository,
            SysRoleRepository sysRoleRepository,
            OaDocumentRepository oaDocumentRepository,
            ConfigDrivenProcessDefinitionService definitionService) {
        this.flowConfigRepository = flowConfigRepository;
        this.nodeRepository = nodeRepository;
        this.conditionRuleRepository = conditionRuleRepository;
        this.sysRoleRepository = sysRoleRepository;
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
                boolean targetExists = request.nodes().stream()
                        .anyMatch(node -> node.name().equals(rule.targetNodeName()));
                if (!targetExists) {
                    throw new BusinessException(ErrorCodeEnum.FLOW_RULE_TARGET_INVALID, "条件规则目标节点不在节点链中");
                }
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
