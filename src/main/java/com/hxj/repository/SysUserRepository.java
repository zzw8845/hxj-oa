package com.hxj.repository;

import com.hxj.entity.SysUser;
import com.hxj.enums.UserStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    List<SysUser> findByStatus(UserStatusEnum status);

    List<SysUser> findByManagerId(Long managerId);

    /**
     * 承担指定角色之一的成员账号（按账号升序）——角色候选组展开的唯一入口。
     * 注意入参是<b>角色 ID</b>（非用户 ID）。
     */
    @Query("select distinct u.account from SysUser u join u.roles r "
            + "where r.id in :roleIds order by u.account")
    List<String> findAccountsByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    /**
     * 承担指定角色之一、且所属部门为指定部门的成员账号（按账号升序）——
     * 「按发起人部门」范围在无显式服务分工时的成员所属部门兜底。
     */
    @Query("select distinct u.account from SysUser u join u.roles r "
            + "where r.id in :roleIds and u.departmentId = :departmentId order by u.account")
    List<String> findAccountsByRoleIdsAndDepartmentId(@Param("roleIds") Collection<Long> roleIds,
                                                      @Param("departmentId") Long departmentId);

    /** 拥有指定权限点的成员账号（按账号升序）——空策略"转交管理员"的落地对象来源。 */
    @Query("select distinct u.account from SysUser u join u.roles r join r.permissions p "
            + "where p.code = :permissionCode order by u.account")
    List<String> findAccountsByPermission(@Param("permissionCode") String permissionCode);
}