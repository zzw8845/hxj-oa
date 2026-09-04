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

/** 流程配置中的一个有序节点。 */
@Entity
@Table(name = "flow_node_config")
public class FlowNodeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_config_id", nullable = false)
    private FlowConfig flowConfig;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "node_type", nullable = false, length = 30)
    private FlowNodeType nodeType;

    @Column(name = "assignee_role", length = 100)
    private String assigneeRole;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected FlowNodeConfig() {
    }

    public FlowNodeConfig(String name, FlowNodeType nodeType) {
        this.name = name;
        this.nodeType = nodeType;
    }

    public Long getId() { return id; }
    public FlowConfig getFlowConfig() { return flowConfig; }
    void setFlowConfig(FlowConfig flowConfig) { this.flowConfig = flowConfig; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FlowNodeType getNodeType() { return nodeType; }
    public void setNodeType(FlowNodeType nodeType) { this.nodeType = nodeType; }
    public String getAssigneeRole() { return assigneeRole; }
    public void setAssigneeRole(String assigneeRole) { this.assigneeRole = assigneeRole; }
    public int getSortOrder() { return sortOrder; }
    void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}