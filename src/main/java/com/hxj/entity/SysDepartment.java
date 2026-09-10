package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 部门实体（闭包表模型，支持任意层级）。
 *
 * <p>祖先-后代路径存于 {@code sys_department_closure}（见
 * {@link SysDepartmentClosure}），部门树查询与子树移动均基于闭包表完成。
 */
@Entity
@Table(name = "sys_department")
public class SysDepartment {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 部门名称（全公司唯一）。 */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** 上级部门ID，根部门为空。 */
    @Column(name = "parent_id")
    private Long parentId;

    /** 同级排序号。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 创建时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
