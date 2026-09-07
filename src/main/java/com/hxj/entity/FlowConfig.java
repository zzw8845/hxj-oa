package com.hxj.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 每种业务事项的审批节点链与条件分支配置。 */
@Entity
@Table(name = "flow_config")
public class FlowConfig {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务单据类型（唯一，如"费用报销""应付款申请"）。 */
    @Column(nullable = false, unique = true, length = 200)
    private String type;

    /** 分类（DAILY日常/BUSINESS业务/SEAL用印）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private FlowCategory category;

    /** 有序节点链。 */
    @OneToMany(mappedBy = "flowConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<FlowNodeConfig> nodes = new ArrayList<>();

    /** 条件分支规则。 */
    @OneToMany(mappedBy = "flowConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<FlowConditionRule> conditionRules = new ArrayList<>();

    /** 创建时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public FlowCategory getCategory() { return category; }
    public void setCategory(FlowCategory category) { this.category = category; }
    public List<FlowNodeConfig> getNodes() { return nodes; }
    public FlowNodeConfig firstActionNode() {
        return nodes.stream()
                .filter(node -> node.getNodeType() != FlowNodeType.START)
                .filter(node -> node.getNodeType() != FlowNodeType.CONDITION)
                .filter(node -> node.getNodeType() != FlowNodeType.CC)
                .filter(node -> node.getNodeType() != FlowNodeType.END)
                .findFirst()
                .orElse(null);
    }
    public void addNode(FlowNodeConfig node) {
        node.setFlowConfig(this);
        node.setSortOrder(nodes.size());
        nodes.add(node);
    }
    public void removeNode(FlowNodeConfig node) {
        nodes.remove(node);
        node.setFlowConfig(null);
        resequenceNodes();
    }
    public List<FlowConditionRule> getConditionRules() { return conditionRules; }
    public void addConditionRule(FlowConditionRule rule) {
        rule.setFlowConfig(this);
        rule.setSortOrder(conditionRules.size());
        conditionRules.add(rule);
    }
    public void removeConditionRule(FlowConditionRule rule) {
        conditionRules.remove(rule);
        rule.setFlowConfig(null);
        resequenceConditionRules();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private void resequenceNodes() {
        for (int index = 0; index < nodes.size(); index++) {
            nodes.get(index).setSortOrder(index);
        }
    }

    private void resequenceConditionRules() {
        for (int index = 0; index < conditionRules.size(); index++) {
            conditionRules.get(index).setSortOrder(index);
        }
    }
}