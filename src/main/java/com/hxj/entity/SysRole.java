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

/**
 * 角色实体：权限点与数据范围的载体，可分配给多名员工。
 *
 * <p>V1 遗留的 data_scope、permissions、members 三个文本列已由本类的 dataScope、
 * permissions、members 关联关系取代，仅作兼容保留。
 */
@Entity
@Table(name = "sys_role")
public class SysRole {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色名称（唯一）。 */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** 对应部门。 */
    @Column(length = 100)
    private String department;

    /** 对应岗位。 */
    @Column(length = 100)
    private String post;

    /** 数据范围（决定该角色可查看的单据范围，code 为 ALL/OWN/DEPT/DEPT_AND_CHILD/CUSTOM）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_scope_id", nullable = false)
    private SysDataScope dataScope;

    /** 自定义数据范围部门集合（仅 dataScope 为 CUSTOM 时生效，经 sys_role_scope_department 关联）。 */
    @ManyToMany
    @JoinTable(
            name = "sys_role_scope_department",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private Set<SysDepartment> scopeDepartments = new LinkedHashSet<>();

    /** 权限点集合（经 sys_role_permission 关联）。 */
    @ManyToMany
    @JoinTable(
            name = "sys_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<SysPermission> permissions = new LinkedHashSet<>();

    /** 角色成员（经 sys_user_role 反向关联）。 */
    @ManyToMany(mappedBy = "roles")
    private Set<SysUser> members = new LinkedHashSet<>();

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
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPost() { return post; }
    public void setPost(String post) { this.post = post; }
    public SysDataScope getDataScope() { return dataScope; }
    public void setDataScope(SysDataScope dataScope) { this.dataScope = dataScope; }
    public Set<SysDepartment> getScopeDepartments() { return scopeDepartments; }
    public void setScopeDepartments(Set<SysDepartment> scopeDepartments) {
        this.scopeDepartments = scopeDepartments == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(scopeDepartments);
    }
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