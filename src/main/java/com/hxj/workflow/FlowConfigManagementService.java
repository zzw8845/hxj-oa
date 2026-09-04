package com.hxj.workflow;

import com.hxj.common.ErrorCode;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowConditionRule;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.FlowNodeType;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 5.11/5.12 流程配置管理：列表/详情查询、新增与修改节点链和条件分支；
 * 修改保存后重新部署 BPMN 新版本，保证新提交单据按新流程流转。
 */
@Service
public class FlowConfigManagementService {

    private final FlowConfigRepository flowConfigRepository;
    private final ConfigDrivenProcessDefinitionService definitionService;
    public FlowConfigManagementService(
            FlowConfigRepository flowConfigRepository,
            ConfigDrivenProcessDefinitionService definitionService) {
        this.flowConfigRepository = flowConfigRepository;
        this.definitionService = definitionService;
    }

    @Transactional(readOnly = true)
    public List<FlowConfigItems.FlowConfigSummary> list() {
        return flowConfigRepository.findAll().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(config -> new FlowConfigItems.FlowConfigSummary(
                        config.getId(), config.getType(), config.getCategory(), config.getNodes().size()))
                .toList();
    }

    /** 5.12 按业务单据类型返回完整节点链（可视化链条数据）。 */
    @Transactional(readOnly = true)
    public FlowConfigItems.FlowConfigDetail detailByType(String type) {
        return toDetail(findByType(type));
    }

    @Transactional(readOnly = true)
    public FlowConfigItems.FlowConfigDetail detail(Long id) {
        return toDetail(findById(id));
    }

    @Transactional
    public FlowConfigItems.FlowConfigDetail create(FlowConfigItems.SaveFlowConfigRequest request) {
        validate(request);
        if (flowConfigRepository.findByType(request.type()).isPresent()) {
            throw new BusinessException(ErrorCode.FLOW_CONFIG_EXISTS, "同名流程配置已存在");
        }
        FlowConfig config = new FlowConfig();
        config.setType(request.type());
        config.setCategory(request.category());
        applyPayload(config, request);
        FlowConfig saved = flowConfigRepository.save(config);
        definitionService.deploy(saved.getId());
        return toDetail(saved);
    }

    /** 修改后重新部署流程定义：新提交单据按新流程流转，在途单据不受影响。 */
    @Transactional
    public FlowConfigItems.FlowConfigDetail update(Long id, FlowConfigItems.SaveFlowConfigRequest request) {
        validate(request);
        FlowConfig config = findById(id);
        if (!config.getType().equals(request.type())
                && flowConfigRepository.findByType(request.type()).isPresent()) {
            throw new BusinessException(ErrorCode.FLOW_CONFIG_EXISTS, "同名流程配置已存在");
        }
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
            throw new BusinessException(ErrorCode.FLOW_CONFIG_INFO_REQUIRED, "流程类型与分类不能为空");
        }
        if (request.nodes() == null || request.nodes().isEmpty()) {
            throw new BusinessException(ErrorCode.FLOW_NODES_REQUIRED, "流程节点链不能为空");
        }
        boolean hasApprovalNode = request.nodes().stream()
                .anyMatch(node -> node.nodeType() == FlowNodeType.APPROVAL);
        if (!hasApprovalNode) {
            throw new BusinessException(ErrorCode.FLOW_APPROVAL_NODE_REQUIRED, "流程至少需要一个审批节点");
        }
        for (FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node : request.nodes()) {
            if (!StringUtils.hasText(node.name()) || node.nodeType() == null) {
                throw new BusinessException(ErrorCode.FLOW_NODE_INVALID, "节点名称与类型不能为空");
            }
            if (node.nodeType() == FlowNodeType.APPROVAL && !StringUtils.hasText(node.assigneeRole())) {
                throw new BusinessException(ErrorCode.FLOW_ASSIGNEE_REQUIRED, "审批节点需指定审批角色");
            }
        }
        if (request.conditionRules() != null) {
            for (FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload rule : request.conditionRules()) {
                if (!StringUtils.hasText(rule.variableName()) || rule.operator() == null
                        || !StringUtils.hasText(rule.expectedValue())
                        || !StringUtils.hasText(rule.targetNodeName())) {
                    throw new BusinessException(ErrorCode.FLOW_RULE_INVALID, "条件分支规则不完整");
                }
                boolean targetExists = request.nodes().stream()
                        .anyMatch(node -> node.name().equals(rule.targetNodeName()));
                if (!targetExists) {
                    throw new BusinessException(ErrorCode.FLOW_RULE_TARGET_INVALID, "条件规则目标节点不在节点链中");
                }
            }
        }
    }

    private FlowConfigItems.FlowConfigDetail toDetail(FlowConfig config) {
        List<FlowConfigItems.FlowNodeItem> nodes = config.getNodes().stream()
                .map(node -> new FlowConfigItems.FlowNodeItem(
                        node.getName(), node.getNodeType(), node.getAssigneeRole()))
                .toList();
        List<FlowConfigItems.FlowConditionRuleItem> rules = config.getConditionRules().stream()
                .map(rule -> new FlowConfigItems.FlowConditionRuleItem(
                        rule.getVariableName(), rule.getOperator(),
                        rule.getExpectedValue(), rule.getTargetNodeName()))
                .toList();
        return new FlowConfigItems.FlowConfigDetail(
                config.getId(), config.getType(), config.getCategory(), nodes, rules);
    }

    private FlowConfig findByType(String type) {
        return flowConfigRepository.findByType(type)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
    }

    private FlowConfig findById(Long id) {
        return flowConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLOW_CONFIG_NOT_FOUND, "流程配置不存在"));
    }
}
