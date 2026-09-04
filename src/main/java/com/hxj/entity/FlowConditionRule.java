package com.hxj.entity;

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

/** 条件分支规则：以流程变量比较结果决定目标节点。 */
@Entity
@Table(name = "flow_condition_rule")
public class FlowConditionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_config_id", nullable = false)
    private FlowConfig flowConfig;

    @Column(name = "variable_name", nullable = false, length = 100)
    private String variableName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private ConditionOperator operator;

    @Column(name = "expected_value", nullable = false, length = 200)
    private String expectedValue;

    @Column(name = "target_node_name", nullable = false, length = 100)
    private String targetNodeName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected FlowConditionRule() {
    }

    public FlowConditionRule(
            String variableName,
            ConditionOperator operator,
            String expectedValue,
            String targetNodeName) {
        this.variableName = variableName;
        this.operator = operator;
        this.expectedValue = expectedValue;
        this.targetNodeName = targetNodeName;
    }

    public Long getId() { return id; }
    public FlowConfig getFlowConfig() { return flowConfig; }
    void setFlowConfig(FlowConfig flowConfig) { this.flowConfig = flowConfig; }
    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }
    public ConditionOperator getOperator() { return operator; }
    public void setOperator(ConditionOperator operator) { this.operator = operator; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
    public String getTargetNodeName() { return targetNodeName; }
    public void setTargetNodeName(String targetNodeName) { this.targetNodeName = targetNodeName; }
    public int getSortOrder() { return sortOrder; }
    void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}