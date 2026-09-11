package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 表单模板：一类单据的字段清单定义（元数据驱动的提交表单）。 */
@Entity
@Table(name = "form_template", uniqueConstraints = @UniqueConstraint(name = "uk_form_template_business_type",
        columnNames = {"business_type"}))
public class FormTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;

    @Column(nullable = false, length = 100)
    private String name;

    /** 单号前缀（如 SP）；空则走默认生成规则。 */
    @Column(name = "doc_prefix", length = 10)
    private String docPrefix;

    /** 绑定的流程配置（显式 1:1，提交经模板取流程，摆脱按名字查找）。 */
    @Column(name = "flow_config_id")
    private Long flowConfigId;

    /** 字段定义变更时 +1；单据提交时冻结为 form_version。 */
    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false, length = 20)
    private String status = "ENABLED";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDocPrefix() { return docPrefix; }
    public void setDocPrefix(String docPrefix) { this.docPrefix = docPrefix; }
    public Long getFlowConfigId() { return flowConfigId; }
    public void setFlowConfigId(Long flowConfigId) { this.flowConfigId = flowConfigId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
