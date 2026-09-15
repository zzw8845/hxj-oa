package com.hxj.workflow;

import com.hxj.enums.ApproveModeEnum;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeSubjectEnum;
import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.EmptyAssigneeStrategyEnum;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.FlowStatusEnum;
import com.hxj.enums.NodeApprovalModeEnum;
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

    /**
     * 条件变量目录项：管理界面「条件变量」下拉的选项来源。
     * 由 {@link ConditionVariableCatalog#describe} 生成——模板勾选字段在前，系统字段与保留键兜底在后。
     */
    public record VariableOption(
            @Schema(description = "变量名（写入转移边的 condition_variable）") String value,
            @Schema(description = "展示名") String label,
            @Schema(description = "值类型（NUMERIC/BOOLEAN/STRING）") String valueType,
            @Schema(description = "来源（FORM_FIELD 模板字段 / SYSTEM 系统字段 / RESERVED_KEY 保留键）")
            String source) {
    }

    /** 节点条目（六维审批人配置 + 展示辅助）。 */
    public record NodeConfig(
            @Schema(description = "节点名称（纯展示，不参与路由）") String name,
            @Schema(description = "节点类型（枚举）") FlowNodeTypeEnum nodeType,
            @Schema(description = "审批主体（枚举）") AssigneeSubjectEnum assigneeSubject,
            @Schema(description = "主体参数（账号/角色ID/字段键，逗号分隔）") String assigneeValue,
            @Schema(description = "主体参数展示文本（角色名/成员名，由 ID 解析）") String assigneeValueText,
            @Schema(description = "组织范围（枚举）") AssigneeScopeEnum assigneeScope,
            @Schema(description = "表单部门控件字段键（范围=FORM_DEPT 时）") String assigneeScopeValue,
            @Schema(description = "主管层级（1-8）") Integer assigneeLevel,
            @Schema(description = "连续多级（逐级审到第 N 级）") boolean assigneeChain,
            @Schema(description = "多人方式（或签/会签/依次）") ApproveModeEnum approveMode,
            @Schema(description = "空策略（找不到审批人时）") EmptyAssigneeStrategyEnum emptyStrategy,
            @Schema(description = "空策略兜底账号（TO_USER 时）") String emptyFallback,
            @Schema(description = "审批类型（人工/自动通过/自动拒绝）") NodeApprovalModeEnum approvalMode,
            @Schema(description = "抄送目标（JSON 数组，仅抄送节点）") String ccTargets) {
    }

    /** 转移边条目：从源节点出发的出边，条件边按优先级求值、无条件边为默认兜底。 */
    public record Transition(
            @Schema(description = "源节点名称") String fromNodeName,
            @Schema(description = "目标节点名称；空表示转移至流程结束") String toNodeName,
            @Schema(description = "条件变量（须为该流程可用变量：模板字段或系统字段）；空表示无条件边") String variableName,
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

        /** 节点载荷：六维审批人配置，除节点名与类型外全部可选（按主体解释）。 */
        public record FlowNodePayload(
                @Schema(description = "节点名称（全流程唯一）") String name,
                @Schema(description = "节点类型（枚举）") FlowNodeTypeEnum nodeType,
                @Schema(description = "审批主体（枚举）") AssigneeSubjectEnum assigneeSubject,
                @Schema(description = "主体参数（账号/角色ID/字段键，逗号分隔）") String assigneeValue,
                @Schema(description = "组织范围（枚举）") AssigneeScopeEnum assigneeScope,
                @Schema(description = "表单部门控件字段键（范围=FORM_DEPT 时）") String assigneeScopeValue,
                @Schema(description = "主管层级（1-8，仅 SUPERIOR / DEPT_HEAD）") Integer assigneeLevel,
                @Schema(description = "连续多级（逐级审到第 N 级，仅主管类）") Boolean assigneeChain,
                @Schema(description = "多人方式（或签/会签/依次，默认或签）") ApproveModeEnum approveMode,
                @Schema(description = "空策略（找不到审批人时；空=提交即拒）") EmptyAssigneeStrategyEnum emptyStrategy,
                @Schema(description = "空策略兜底账号（TO_USER 时必填）") String emptyFallback,
                @Schema(description = "审批类型（人工/自动通过/自动拒绝，默认人工）") NodeApprovalModeEnum approvalMode,
                @Schema(description = "抄送目标（JSON 数组，仅抄送节点）") String ccTargets) {

            /** 兼容最小载荷（仅主体，如指定成员/发起人）。 */
            public FlowNodePayload(String name, FlowNodeTypeEnum nodeType,
                                   AssigneeSubjectEnum assigneeSubject, String assigneeValue) {
                this(name, nodeType, assigneeSubject, assigneeValue, null, null, null, null,
                        null, null, null, null, null);
            }

            /** 兼容无主体但有抄送目标（抄送节点）。 */
            public FlowNodePayload(String name, FlowNodeTypeEnum nodeType,
                                   AssigneeSubjectEnum assigneeSubject, String assigneeValue,
                                   String ccTargets) {
                this(name, nodeType, assigneeSubject, assigneeValue, null, null, null, null,
                        null, null, null, null, ccTargets);
            }

            /** 兼容角色主体 + 范围（钉钉"角色 + 管理范围"）。 */
            public FlowNodePayload(String name, FlowNodeTypeEnum nodeType,
                                   AssigneeSubjectEnum assigneeSubject, String assigneeValue,
                                   AssigneeScopeEnum assigneeScope, Boolean assigneeChain) {
                this(name, nodeType, assigneeSubject, assigneeValue, assigneeScope, null, null,
                        assigneeChain, null, null, null, null, null);
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
