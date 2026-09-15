package com.hxj.entity;

import com.hxj.enums.ApproveModeEnum;
import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeSubjectEnum;
import com.hxj.enums.EmptyAssigneeStrategyEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.NodeApprovalModeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 流程图中的一个节点。{@code name} 仅作展示；
 * 路由行为由<b>六维正交的审批人配置</b>决定（钉钉同构，替代"一种类型一个枚举值"的历史实现）：
 *
 * <pre>
 *   主体 subject  ×  范围 scope  ×  层级 level(+chain)  ×  多人方式 approveMode
 *                 ×  空策略 emptyStrategy  ×  审批类型 approvalMode
 * </pre>
 *
 * <p>钉钉的全部审批人类型都是这六维的组合（见 {@link AssigneeSubjectEnum} 枚举说明），
 * 新增业务场景只需组合既有维度，不需要改代码结构。
 */
@Entity
@Table(name = "flow_node_config")
public class FlowNodeConfig {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属流程配置。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_config_id", nullable = false)
    private FlowConfig flowConfig;

    /** 节点名称（纯展示，不参与路由）。 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 节点类型（START/APPROVAL/HANDLER/CC/END）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "node_type", nullable = false, length = 30)
    private FlowNodeTypeEnum nodeType;

    // ==================== 审批人配置：六维正交模型 ====================

    /**
     * 主体维度：从哪儿找人。{@code assigneeValue} 依主体解释——
     * MEMBER 为账号列表、ROLE 为角色 ID 列表、FORM_MEMBER 为人员控件字段键、
     * INITIATOR_SELECT 为可选范围（空=全公司）。
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "assignee_subject", length = 30)
    private AssigneeSubjectEnum assigneeSubject;

    /** 主体参数（账号 / 角色 ID / 字段键，逗号分隔，依 {@link #assigneeSubject} 解释）。 */
    @Column(name = "assignee_value", length = 500)
    private String assigneeValue;

    /** 组织范围维度：在哪个部门范围内匹配人（ROLE / DEPT_HEAD 生效；空按 GLOBAL）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "assignee_scope", length = 30)
    private AssigneeScopeEnum assigneeScope;

    /** FORM_DEPT 范围下，表单"部门控件"的字段键。 */
    @Column(name = "assignee_scope_value", length = 100)
    private String assigneeScopeValue;

    /** 层级维度：第 N 级主管（1-8，仅 SUPERIOR / DEPT_HEAD；空按 1）。 */
    @Column(name = "assignee_level")
    private Integer assigneeLevel;

    /** 连续多级：true 时从第 1 级**逐级**审到第 {@link #assigneeLevel} 级（钉钉"连续多级主管"）。 */
    @Column(name = "assignee_chain", nullable = false)
    private boolean assigneeChain;

    /** 多人方式：或签（候选组）/ 会签（并行多实例）/ 依次（串行多实例）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "approve_mode", nullable = false, length = 20)
    private ApproveModeEnum approveMode = ApproveModeEnum.OR_SIGN;

    /** 空策略：解析不到人时自动通过/自动拒绝/转管理员/转指定人（钉钉四选一）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "empty_strategy", length = 20)
    private EmptyAssigneeStrategyEnum emptyStrategy;

    /** TO_USER 策略的兜底账号。 */
    @Column(name = "empty_fallback", length = 100)
    private String emptyFallback;

    /** 审批类型：人工审批 / 自动通过 / 自动拒绝。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "approval_mode", nullable = false, length = 20)
    private NodeApprovalModeEnum approvalMode = NodeApprovalModeEnum.MANUAL;

    // ==================== 其他 ====================

    /** 抄送节点目标（JSON 数组 [{"type":"ROLE|DEPT|USER","value":"ID或账号"}]），仅 CC 节点使用。 */
    @Column(name = "cc_targets", columnDefinition = "TEXT")
    private String ccTargets;

    /** 同流程内的展示顺序（审批链在界面上的呈现顺序，拓扑由转移边决定）。 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected FlowNodeConfig() {
    }

    public FlowNodeConfig(String name, FlowNodeTypeEnum nodeType) {
        this.name = name;
        this.nodeType = nodeType;
    }

    public Long getId() { return id; }
    public FlowConfig getFlowConfig() { return flowConfig; }
    void setFlowConfig(FlowConfig flowConfig) { this.flowConfig = flowConfig; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FlowNodeTypeEnum getNodeType() { return nodeType; }
    public void setNodeType(FlowNodeTypeEnum nodeType) { this.nodeType = nodeType; }
    public AssigneeSubjectEnum getAssigneeSubject() { return assigneeSubject; }
    public void setAssigneeSubject(AssigneeSubjectEnum assigneeSubject) { this.assigneeSubject = assigneeSubject; }
    public String getAssigneeValue() { return assigneeValue; }
    public void setAssigneeValue(String assigneeValue) { this.assigneeValue = assigneeValue; }
    public AssigneeScopeEnum getAssigneeScope() { return assigneeScope; }
    public void setAssigneeScope(AssigneeScopeEnum assigneeScope) { this.assigneeScope = assigneeScope; }
    public String getAssigneeScopeValue() { return assigneeScopeValue; }
    public void setAssigneeScopeValue(String assigneeScopeValue) { this.assigneeScopeValue = assigneeScopeValue; }
    public Integer getAssigneeLevel() { return assigneeLevel; }
    public void setAssigneeLevel(Integer assigneeLevel) { this.assigneeLevel = assigneeLevel; }
    public boolean isAssigneeChain() { return assigneeChain; }
    public void setAssigneeChain(boolean assigneeChain) { this.assigneeChain = assigneeChain; }
    public ApproveModeEnum getApproveMode() { return approveMode; }
    public void setApproveMode(ApproveModeEnum approveMode) { this.approveMode = approveMode; }
    public EmptyAssigneeStrategyEnum getEmptyStrategy() { return emptyStrategy; }
    public void setEmptyStrategy(EmptyAssigneeStrategyEnum emptyStrategy) { this.emptyStrategy = emptyStrategy; }
    public String getEmptyFallback() { return emptyFallback; }
    public void setEmptyFallback(String emptyFallback) { this.emptyFallback = emptyFallback; }
    public NodeApprovalModeEnum getApprovalMode() { return approvalMode; }
    public void setApprovalMode(NodeApprovalModeEnum approvalMode) { this.approvalMode = approvalMode; }
    public String getCcTargets() { return ccTargets; }
    public void setCcTargets(String ccTargets) { this.ccTargets = ccTargets; }
    public int getSortOrder() { return sortOrder; }
    void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
