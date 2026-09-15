package com.hxj.entity;

import com.hxj.enums.ConditionOperatorEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 流程转移边（图模型）：审批流的真实拓扑。
 *
 * <p>语义：从 {@code fromNode} 出发有多条出边时按 {@code sortOrder} 优先级依次求值，
 * 第一条命中条件的边被采纳；全部不命中则走无条件边（{@code conditionVariable == null}）。
 * 每个节点必须存在一条无条件边或默认边兜底，保证流程永不悬挂。
 * {@code toNode == null} 表示转移至流程结束。
 *
 * <p>线性链是本模型的特例（每节点恰一条无条件出边）——
 * 替代历史"线性节点链 + 条件规则命中跳转"的 goto 语义。
 */
@Entity
@Table(name = "flow_transition")
public class FlowTransition {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属流程配置。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_config_id", foreignKey = @ForeignKey(name = "fk_transition_flow"))
    private FlowConfig flowConfig;

    /**
     * 源节点（流程开始节点也作为 START 元素参与转移）。
     * 列允许临时 NULL：集合重建（orphanRemoval）时 Hibernate 先置空外键再删除行，
     * 业务非空性由保存关口与 {@code applyPayload} 保证。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", foreignKey = @ForeignKey(name = "fk_transition_from"))
    private FlowNodeConfig fromNode;

    /** 目标节点；空表示转移至流程结束。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", foreignKey = @ForeignKey(name = "fk_transition_to"))
    private FlowNodeConfig toNode;

    /** 条件变量（白名单见 WorkflowVariables.CONDITION_VARIABLES）；空表示无条件边。 */
    @Column(name = "condition_variable", length = 50)
    private String conditionVariable;

    /** 比较操作符。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "operator", length = 30)
    private ConditionOperatorEnum operator;

    /** 比较期望值（存储为字符串，按变量契约类型校验与解析）。 */
    @Column(name = "expected_value", length = 200)
    private String expectedValue;

    /** 优先级：同源多条条件边的求值顺序（小的先 evaluated）。 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    public Long getId() { return id; }
    public FlowConfig getFlowConfig() { return flowConfig; }
    void setFlowConfig(FlowConfig flowConfig) { this.flowConfig = flowConfig; }
    public FlowNodeConfig getFromNode() { return fromNode; }
    public void setFromNode(FlowNodeConfig fromNode) { this.fromNode = fromNode; }
    public FlowNodeConfig getToNode() { return toNode; }
    public void setToNode(FlowNodeConfig toNode) { this.toNode = toNode; }
    public String getConditionVariable() { return conditionVariable; }
    public void setConditionVariable(String conditionVariable) { this.conditionVariable = conditionVariable; }
    public ConditionOperatorEnum getOperator() { return operator; }
    public void setOperator(ConditionOperatorEnum operator) { this.operator = operator; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    /** 是否条件边（否即无条件边，承担默认兜底职责）。 */
    public boolean isConditional() {
        return conditionVariable != null && !conditionVariable.isBlank();
    }
}
