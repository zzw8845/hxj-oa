package com.hxj.entity;

import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.FlowStatusEnum;
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
    private FlowCategoryEnum category;

    /** 发布状态：草稿（保存≠生效）与已发布（部署完成、可提交）两态。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FlowStatusEnum status = FlowStatusEnum.DRAFT;

    /** 发布版本号：每次发布递增，供审计与问题回溯。 */
    @Column(nullable = false)
    private int version = 0;

    /** 有序节点链（展示顺序；拓扑由转移边决定）。 */
    @OneToMany(mappedBy = "flowConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<FlowNodeConfig> nodes = new ArrayList<>();

    /** 转移边（图模型）：审批流的真实拓扑，见 {@link FlowTransition}。 */
    @OneToMany(mappedBy = "flowConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<FlowTransition> transitions = new ArrayList<>();

    /** 创建时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public FlowCategoryEnum getCategory() { return category; }
    public void setCategory(FlowCategoryEnum category) { this.category = category; }
    public List<FlowNodeConfig> getNodes() { return nodes; }
    public FlowNodeConfig firstActionNode() {
        return nodes.stream()
                .filter(node -> node.getNodeType() != FlowNodeTypeEnum.START)
                .filter(node -> node.getNodeType() != FlowNodeTypeEnum.CONDITION)
                .filter(node -> node.getNodeType() != FlowNodeTypeEnum.CC)
                .filter(node -> node.getNodeType() != FlowNodeTypeEnum.END)
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
    public List<FlowTransition> getTransitions() { return transitions; }
    public void addTransition(FlowTransition transition) {
        transition.setFlowConfig(this);
        transition.setSortOrder(transitions.size());
        transitions.add(transition);
    }
    public void removeTransition(FlowTransition transition) {
        transitions.remove(transition);
        transition.setFlowConfig(null);
        resequenceTransitions();
    }
    public FlowStatusEnum getStatus() { return status; }
    public void setStatus(FlowStatusEnum status) { this.status = status; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private void resequenceNodes() {
        for (int index = 0; index < nodes.size(); index++) {
            nodes.get(index).setSortOrder(index);
        }
    }

    private void resequenceTransitions() {
        for (int index = 0; index < transitions.size(); index++) {
            transitions.get(index).setSortOrder(index);
        }
    }
}