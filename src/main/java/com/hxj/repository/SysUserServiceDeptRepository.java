package com.hxj.repository;

import com.hxj.entity.SysUserServiceDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SysUserServiceDeptRepository extends JpaRepository<SysUserServiceDept, Long> {

    List<SysUserServiceDept> findByUserIdOrderByIdAsc(Long userId);

    void deleteByUserId(Long userId);

    /**
     * 服务该部门、且承担指定角色之一的成员账号（按账号升序取首个为主办）。
     * 「按发起人部门」范围的首选数据源——显式分工优先级高于成员所属部门。
     */
    @Query("select distinct u.account from SysUser u join u.roles r "
            + "where r.id in :roleIds and u.id in "
            + " (select s.userId from SysUserServiceDept s where s.departmentId = :departmentId) "
            + "order by u.account")
    List<String> findServedAccountsByRoleIds(@Param("departmentId") Long departmentId,
                                             @Param("roleIds") Collection<Long> roleIds);

    /** 服务该部门的全部成员账号（不带角色过滤，供分工一览与流程排障）。 */
    @Query("select u.account from SysUser u where u.id in "
            + " (select s.userId from SysUserServiceDept s where s.departmentId = :departmentId) "
            + "order by u.id")
    List<String> findServingAccounts(@Param("departmentId") Long departmentId);
}
