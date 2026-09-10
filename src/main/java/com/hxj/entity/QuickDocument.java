package com.hxj.entity;

import com.hxj.enums.BusinessTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/** 快捷单据目录：提交单据时可选的常用单据名称。 */
@Entity
@Table(name = "quick_document")
public class QuickDocument {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务类型（日常付款/业务付款/用印申请）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "business_type", nullable = false, length = 50)
    private BusinessTypeEnum businessType;

    /** 单据名称。 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 排序号。 */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** 创建时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BusinessTypeEnum getBusinessType() { return businessType; }
    public void setBusinessType(BusinessTypeEnum businessType) { this.businessType = businessType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}