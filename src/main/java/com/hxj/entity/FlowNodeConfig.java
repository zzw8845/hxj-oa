package com.hxj.entity;

import com.hxj.enums.AssigneeScopeEnum;
import com.hxj.enums.AssigneeTypeEnum;
import com.hxj.enums.FlowNodeTypeEnum;
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
 * 路由行为由 {@link #nodeType} 与结构化指派协议 {@link #assigneeType} + {@link #assigneeValue} 决定
 * （替代历史"中文节点名/角色名字符串特判"协议，见 {@link AssigneeTypeEnum}）。
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

    /** 审批人解析协议（六种，见枚举说明）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "assignee_type", length = 30)
    private AssigneeTypeEnum assigneeType;

    /**
     * 审批人参数：仅 {@link AssigneeTypeEnum#ROLE} 使用——候选角色 ID 逗号分隔
     * （其余类型的解析数据源在人员档案/部门负责人树/提交请求中）。
     */
    @Column(name = "assignee_value", length = 500)
    private String assigneeValue;

    /**
     * 审批人范围（仅 {@link AssigneeTypeEnum#ROLE} 生效）：{@link AssigneeScopeEnum#GLOBAL}
     * 全部成员候选组 / {@link AssigneeScopeEnum#INITIATOR_DEPT} 仅服务发起人部门者（提交时解析）。
     * 为空按 GLOBAL 处理（兼容存量节点）。
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "assignee_scope", length = 30)
    private AssigneeScopeEnum assigneeScope;

    /** 抄送节点目标（JSON 数组 [{"type":"ROLE|DEPT|USER","value":"..."}]），仅 CC 节点使用。 */
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
    public AssigneeTypeEnum getAssigneeType() { return assigneeType; }
    public void setAssigneeType(AssigneeTypeEnum assigneeType) { this.assigneeType = assigneeType; }
    public String getAssigneeValue() { return assigneeValue; }
    public void setAssigneeValue(String assigneeValue) { this.assigneeValue = assigneeValue; }
    public AssigneeScopeEnum getAssigneeScope() { return assigneeScope; }
    public void setAssigneeScope(AssigneeScopeEnum assigneeScope) { this.assigneeScope = assigneeScope; }
    public String getCcTargets() { return ccTargets; }
    public void setCcTargets(String ccTargets) { this.ccTargets = ccTargets; }
    public int getSortOrder() { return sortOrder; }
    void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
