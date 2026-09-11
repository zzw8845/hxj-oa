package com.hxj.repository;

import com.hxj.entity.SysUserDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysUserDepartmentRepository extends JpaRepository<SysUserDepartment, Long> {

    List<SysUserDepartment> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    boolean existsByDepartmentId(Long departmentId);
}
