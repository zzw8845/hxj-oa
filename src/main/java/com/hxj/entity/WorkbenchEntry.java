package com.hxj.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 工作台事项快捷入口：员工视角的"事项"（差旅费/快递费/加工费……）到承接表单模板的映射。
 *
 * <p>原型工作台的快捷按钮是事项级的——事项只是预填事由的快捷方式，费用/付款类事项
 * 共用报销与应付款模板，靠事由区分（原型 classifyType 设计）。事项→模板的映射是
 * 业务配置数据，归属后端管理，不进前端代码。
 */
@Entity
@Table(name = "workbench_entry")
public class WorkbenchEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属工作台分区：DAILY / BUSINESS / SEAL。 */
    private String zone;

    /** 事项名（按钮主文案，如"差旅费"）。 */
    private String label;

    /** 职责小字（按钮副文案，如"交通、住宿、餐费"）。 */
    private String hint;

    /** 承接的表单模板。 */
    private Long templateId;

    private Integer sortOrder;

    /** 是否建模板时自动生成（自动事项的文案跟随模板名，模板删除时级联清除）。 */
    private Boolean autoCreated;

    public Long getId() { return id; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getAutoCreated() { return autoCreated; }
    public void setAutoCreated(Boolean autoCreated) { this.autoCreated = autoCreated; }
}
