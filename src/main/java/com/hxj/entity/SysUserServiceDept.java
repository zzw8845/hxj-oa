package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 职能服务分工：某成员服务哪些部门的单据。
 *
 * <p>典型用途：核算会计集中坐席于财务条线，但按部门分工处理各业务条线的单据
 * （原型「会计（按部门）」节点与「按业务部门分配」数据范围的共同数据基础）。
 */
@Entity
@Table(name = "sys_user_service_dept",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_service_dept",
                columnNames = {"user_id", "department_id"}))
public class SysUserServiceDept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    protected SysUserServiceDept() {
    }

    public SysUserServiceDept(Long userId, Long departmentId) {
        this.userId = userId;
        this.departmentId = departmentId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }
}
