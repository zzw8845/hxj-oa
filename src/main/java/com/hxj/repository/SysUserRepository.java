package com.hxj.repository;

import com.hxj.entity.SysUser;
import com.hxj.enums.UserStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

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
}