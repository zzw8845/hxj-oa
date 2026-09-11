package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 员工兼职部门关联：一人可同时归属多个部门（钉钉式）。
 * 主部门在 {@link SysUser#getDepartmentId()}，本表仅存兼职部门。
 */
@Entity
@Table(name = "sys_user_department", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "department_id"}))
public class SysUserDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private SysUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private SysDepartment department;

    /** 是否主部门成员（V20 存量回填的历史行；新增兼职行恒为 false）。 */
    @Column(nullable = false)
    private boolean primaryDepartment;

    public SysUser getUser() { return user; }
    public void setUser(SysUser user) { this.user = user; }
    public SysDepartment getDepartment() { return department; }
    public void setDepartment(SysDepartment department) { this.department = department; }
    public boolean isPrimaryDepartment() { return primaryDepartment; }
    public void setPrimaryDepartment(boolean primaryDepartment) { this.primaryDepartment = primaryDepartment; }
}
