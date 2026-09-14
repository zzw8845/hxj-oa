package com.hxj.repository;

import com.hxj.entity.SysUser;
import com.hxj.enums.UserStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByAccount(String account);

    boolean existsByAccount(String account);

    boolean existsByJobNo(String jobNo);

    List<SysUser> findByDepartment(String department);

    List<SysUser> findByDepartmentId(Long departmentId);

    boolean existsByDepartmentId(Long departmentId);

    boolean existsByPostId(Long postId);

    List<SysUser> findByPostId(Long postId);

    List<SysUser> findDistinctByRolesId(Long roleId);

    /** 挂指定角色的用户账号集合（审批人去重判定用）。 */
    @Query("select u.account from SysUser u join u.roles r where r.name = ?1")
    List<String> findAccountByRoleName(String roleName);

    List<SysUser> findByStatus(UserStatusEnum status);

    List<SysUser> findByManagerId(Long managerId);
}