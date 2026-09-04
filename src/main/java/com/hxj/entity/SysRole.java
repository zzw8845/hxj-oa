package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** 角色实体 */
@Entity
@Table(name = "sys_role")
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_scope_id", nullable = false)
    private SysDataScope dataScope;

    @ManyToMany
    @JoinTable(
            name = "sys_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<SysPermission> permissions = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "roles")
    private Set<SysUser> members = new LinkedHashSet<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPost() { return post; }
    public void setPost(String post) { this.post = post; }
    public SysDataScope getDataScope() { return dataScope; }
    public void setDataScope(SysDataScope dataScope) { this.dataScope = dataScope; }
    public Set<SysPermission> getPermissions() { return permissions; }
    public void setPermissions(Set<SysPermission> permissions) {
        this.permissions = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
    }
    public void addPermission(SysPermission permission) { permissions.add(permission); }
    public void removePermission(SysPermission permission) { permissions.remove(permission); }
    public boolean hasPermission(String permissionCode) {
        return permissions.stream().anyMatch(permission -> permission.getCode().equals(permissionCode));
    }
    public Set<SysUser> getMembers() { return members; }
    void addMember(SysUser member) { members.add(member); }
    void removeMember(SysUser member) { members.remove(member); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}