package com.hxj.repository;

import com.hxj.entity.SysUser;
import com.hxj.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByAccount(String account);

    boolean existsByAccount(String account);

    boolean existsByJobNo(String jobNo);

    List<SysUser> findByDepartment(String department);

    List<SysUser> findDistinctByRolesId(Long roleId);

    List<SysUser> findByStatus(UserStatus status);
}