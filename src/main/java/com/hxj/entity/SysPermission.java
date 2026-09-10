package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 可配置到角色上的权限点。 */
@Entity
@Table(name = "sys_permission")
public class SysPermission {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 权限点编码（唯一，如 DOCUMENT_VIEW）。
     *
     * <p>该编码是角色写接口 {@code SaveRoleRequest.permissions} 引用的业务标识，
     * 也是 Spring Security 权限比对依据，因此生成后不可变更。
     */
    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String code;

    /** 权限点名称。 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 权限点说明。 */
    @Column(length = 500)
    private String description;

    /** 创建时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SysPermission() {
    }

    public SysPermission(String code, String name) {
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