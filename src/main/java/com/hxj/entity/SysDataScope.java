package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 控制角色可查看单据范围的数据范围定义。 */
@Entity
@Table(name = "sys_data_scope")
public class SysDataScope {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 数据范围编码（唯一）。
     *
     * <p>该编码是角色写接口 {@code SaveRoleRequest.dataScope} 引用的业务标识，
     * 因此生成后不可变更，与 OaDocument.docCode 同一原则。
     */
    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String code;

    /** 数据范围名称。 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 数据范围说明。 */
    @Column(length = 500)
    private String description;

    /** 创建时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SysDataScope() {
    }

    public SysDataScope(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}