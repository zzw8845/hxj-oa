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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** 用户实体 */
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "job_no", unique = true, length = 50)
    private String jobNo;

    @Column(nullable = false, unique = true, length = 50)
    private String account;

    @Column(nullable = false)
    private String password;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String post;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<SysRole> roles = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getJobNo() { return jobNo; }
    public void setJobNo(String jobNo) { this.jobNo = jobNo; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPost() { return post; }
    public void setPost(String post) { this.post = post; }
    /** 兼容单角色调用方，返回分配顺序中的首个角色。 */
    public SysRole getRole() { return roles.stream().findFirst().orElse(null); }
    public void setRole(SysRole role) {
        clearRoles();
        if (role != null) {
            addRole(role);
        }
    }
    public Set<SysRole> getRoles() { return roles; }
    public void addRole(SysRole role) {
        if (roles.add(role)) {
            role.addMember(this);
        }
    }
    public void removeRole(SysRole role) {
        if (roles.remove(role)) {
            role.removeMember(this);
        }
    }
    public void clearRoles() {
        new LinkedHashSet<>(roles).forEach(this::removeRole);
    }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public boolean isLoginEnabled() { return status == UserStatus.ACTIVE; }
    public boolean hasPermission(String permissionCode) {
        return roles.stream().anyMatch(role -> role.hasPermission(permissionCode));
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}