package com.hxj.repository;

import com.hxj.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    Optional<SysPermission> findByCode(String code);

    List<SysPermission> findByCodeIn(Collection<String> codes);
}