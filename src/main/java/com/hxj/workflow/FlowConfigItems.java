package com.hxj.workflow;

import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 流程配置管理接口的请求/响应数据结构（5.11 / 5.12）。 */
public final class FlowConfigItems {

    private FlowConfigItems() {
    }

    /** 流程配置列表项。 */
    public record Brief(
            @Schema(description = "流程配置ID") Long id,
            @Schema(description = "业务类型名称") String type,
            @Schema(description = "流程分类（枚举）") FlowCategoryEnum category,
            @Schema(description = "节点数量") int nodeCount,
            @Schema(description = "节点名称列表（按顺序）") List<String> nodeNames) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public Brief {
            nodeNames = nodeNames == null ? List.of() : List.copyOf(nodeNames);
        }
    }

    /** 可视化节点链条目。 */
    public record NodeConfig(
            @Schema(description = "节点名称") String name,
            @Schema(description = "节点类型（枚举）") FlowNodeTypeEnum nodeType,
            @Schema(description = "处理角色") String assigneeRole) {
    }

    /** 条件分支规则条目。 */
    public record ConditionRule(
            @Schema(description = "判断变量名") String variableName,
            @Schema(description = "比较操作符（枚举）") ConditionOperatorEnum operator,
            @Schema(description = "期望值") String expectedValue,
            @Schema(description = "命中后跳转的目标节点名") String targetNodeName) {
    }

    /** 流程配置详情：完整节点链 + 条件分支，供“流程管理”页面展示。 */
    public record Config(
            @Schema(description = "流程配置ID") Long id,
            @Schema(description = "业务类型名称") String type,
            @Schema(description = "流程分类（枚举）") FlowCategoryEnum category,
            @Schema(description = "节点链") List<NodeConfig> nodes,
            @Schema(description = "条件分支规则") List<ConditionRule> conditionRules) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public Config {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            conditionRules = conditionRules == null ? List.of() : List.copyOf(conditionRules);
        }
    }

    /** 新增/修改流程配置请求。 */
    public record SaveFlowConfigRequest(
            @Schema(description = "业务类型名称") String type,
            @Schema(description = "流程分类（枚举）") FlowCategoryEnum category,
            @Schema(description = "节点链") List<FlowNodePayload> nodes,
            @Schema(description = "条件分支规则") List<FlowConditionRulePayload> conditionRules) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public SaveFlowConfigRequest {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            conditionRules = conditionRules == null ? List.of() : List.copyOf(conditionRules);
        }

        public record FlowNodePayload(
                @Schema(description = "节点名称") String name,
                @Schema(description = "节点类型（枚举）") FlowNodeTypeEnum nodeType,
                @Schema(description = "处理角色") String assigneeRole) {
        }

        public record FlowConditionRulePayload(
                @Schema(description = "判断变量名") String variableName,
                @Schema(description = "比较操作符（枚举）") ConditionOperatorEnum operator,
                @Schema(description = "期望值") String expectedValue,
                @Schema(description = "命中后跳转的目标节点名") String targetNodeName) {
        }
    }
}
