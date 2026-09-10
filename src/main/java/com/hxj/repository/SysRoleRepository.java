package com.hxj.repository;

import com.hxj.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByName(String name);

    List<SysRole> findByNameIn(Collection<String> names);

    boolean existsByName(String name);

    List<SysRole> findByDepartment(String department);

    List<SysRole> findByDepartmentId(Long departmentId);

    boolean existsByDepartmentId(Long departmentId);

    List<SysRole> findByPost(String post);
}