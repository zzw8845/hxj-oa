package com.hxj.entity;

import com.hxj.enums.UserStatusEnum;
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

/** 用户（员工）实体，一名员工可分配多个角色。 */
@Entity
@Table(name = "sys_user")
public class SysUser {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 姓名。 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 工号（唯一）。 */
    @Column(name = "job_no", unique = true, length = 50)
    private String jobNo;

    /** 登录账号（唯一）。 */
    @Column(nullable = false, unique = true, length = 50)
    private String account;

    /** 登录密码（BCrypt 加密）。 */
    @Column(nullable = false)
    private String password;

    /** 部门ID（sys_department 外键，管理入口的唯一事实来源）。 */
    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    /** 所属部门展示快照（提交单据时随单快照，由服务层与字典同步维护）。 */
    @Column(length = 100)
    private String department;

    /** 岗位ID（sys_post 外键）。 */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** 岗位展示快照。 */
    @Column(length = 100)
    private String post;

    /** 分配角色集合（经 sys_user_role 关联，权限由角色继承）。 */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<SysRole> roles = new LinkedHashSet<>();

    /** 在职状态（ACTIVE在职/RESIGNED离职），仅在职可登录。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private UserStatusEnum status = UserStatusEnum.ACTIVE;

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
    public String getJobNo() { return jobNo; }
    public void setJobNo(String jobNo) { this.jobNo = jobNo; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
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
    public UserStatusEnum getStatus() { return status; }
    public void setStatus(UserStatusEnum status) { this.status = status; }
    public boolean isLoginEnabled() { return status == UserStatusEnum.ACTIVE; }
    public boolean hasPermission(String permissionCode) {
        return roles.stream().anyMatch(role -> role.hasPermission(permissionCode));
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}