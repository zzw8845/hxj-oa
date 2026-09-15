package com.hxj.workflow;

import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeTypeEnum;
import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.FlowStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 流程配置管理接口的请求/响应数据结构（图模型：节点 + 转移边 + 发布状态）。 */
public final class FlowConfigItems {

    private FlowConfigItems() {
    }

    /** 流程配置列表项。 */
    public record Brief(
            @Schema(description = "流程配置ID") Long id,
            @Schema(description = "业务类型名称") String type,
            @Schema(description = "流程分类（枚举）") FlowCategoryEnum category,
            @Schema(description = "发布状态") FlowStatusEnum status,
            @Schema(description = "发布版本号") int version,
            @Schema(description = "节点数量") int nodeCount,
            @Schema(description = "节点名称列表（展示顺序）") List<String> nodeNames,
            @Schema(description = "分支摘要（条件边，如「会计（按部门）: amount≥20000 → 财务经理」）") List<String> branches) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public Brief {
            nodeNames = nodeNames == null ? List.of() : List.copyOf(nodeNames);
            branches = branches == null ? List.of() : List.copyOf(branches);
        }
    }

    /** 节点条目（展示 + 配置）。 */
    public record NodeConfig(
            @Schema(description = "节点名称（纯展示，不参与路由）") String name,
            @Schema(description = "节点类型（枚举）") FlowNodeTypeEnum nodeType,
            @Schema(description = "审批人解析协议（枚举）") AssigneeTypeEnum assigneeType,
            @Schema(description = "审批人参数（仅 ROLE：候选角色ID逗号分隔）") String assigneeValue,
            @Schema(description = "审批人范围（仅 ROLE：GLOBAL 全部成员 / INITIATOR_DEPT 按发起人部门）") AssigneeScopeEnum assigneeScope,
            @Schema(description = "候选角色名（展示用，由角色ID解析）") String assigneeRoleNames,
            @Schema(description = "抄送目标（JSON 数组，仅抄送节点）") String ccTargets) {
    }

    /** 转移边条目：从源节点出发的出边，条件边按优先级求值、无条件边为默认兜底。 */
    public record Transition(
            @Schema(description = "源节点名称") String fromNodeName,
            @Schema(description = "目标节点名称；空表示转移至流程结束") String toNodeName,
            @Schema(description = "条件变量（须为该流程绑定模板中标记“参与流程条件”的字段）；空表示无条件边") String variableName,
            @Schema(description = "比较操作符（枚举）") ConditionOperatorEnum operator,
            @Schema(description = "期望值") String expectedValue,
            @Schema(description = "优先级（同源多条条件边的求值顺序）") int sortOrder) {
    }

    /** 流程配置详情：完整节点 + 转移边 + 发布状态。 */
    public record Config(
            @Schema(description = "流程配置ID") Long id,
            @Schema(description = "业务类型名称") String type,
            @Schema(description = "流程分类（枚举）") FlowCategoryEnum category,
            @Schema(description = "发布状态") FlowStatusEnum status,
            @Schema(description = "发布版本号") int version,
            @Schema(description = "节点") List<NodeConfig> nodes,
            @Schema(description = "转移边") List<Transition> transitions) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public Config {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
        }
    }

    /** 新增/修改流程配置请求（保存 = 校验 + 落库为草稿；发布走独立端点）。 */
    public record SaveFlowConfigRequest(
            @Schema(description = "业务类型名称") String type,
            @Schema(description = "流程分类（枚举）") FlowCategoryEnum category,
            @Schema(description = "节点") List<FlowNodePayload> nodes,
            @Schema(description = "转移边") List<FlowTransitionPayload> transitions) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public SaveFlowConfigRequest {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
        }

        public record FlowNodePayload(
                @Schema(description = "节点名称（全流程唯一）") String name,
                @Schema(description = "节点类型（枚举）") FlowNodeTypeEnum nodeType,
                @Schema(description = "审批人解析协议（枚举）") AssigneeTypeEnum assigneeType,
                @Schema(description = "审批人参数（仅 ROLE：候选角色ID逗号分隔）") String assigneeValue,
                @Schema(description = "审批人范围（仅 ROLE：GLOBAL / INITIATOR_DEPT，空按 GLOBAL）") AssigneeScopeEnum assigneeScope,
                @Schema(description = "抄送目标（JSON 数组，仅抄送节点）") String ccTargets) {

            /** 兼容无范围、无抄送（非 ROLE、非抄送节点）。 */
            public FlowNodePayload(String name, FlowNodeTypeEnum nodeType,
                                   AssigneeTypeEnum assigneeType, String assigneeValue) {
                this(name, nodeType, assigneeType, assigneeValue, null, null);
            }

            /** 兼容无范围但有抄送（抄送节点）。 */
            public FlowNodePayload(String name, FlowNodeTypeEnum nodeType,
                                   AssigneeTypeEnum assigneeType, String assigneeValue, String ccTargets) {
                this(name, nodeType, assigneeType, assigneeValue, null, ccTargets);
            }
        }

        public record FlowTransitionPayload(
                @Schema(description = "源节点名称") String fromNodeName,
                @Schema(description = "目标节点名称；空表示转移至流程结束") String toNodeName,
                @Schema(description = "条件变量；空表示无条件边（默认兜底）") String variableName,
                @Schema(description = "比较操作符（枚举）") ConditionOperatorEnum operator,
                @Schema(description = "期望值") String expectedValue) {
        }
    }
}
