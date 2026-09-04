package com.hxj.repository;

import com.hxj.entity.SysDataScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysDataScopeRepository extends JpaRepository<SysDataScope, Long> {

    Optional<SysDataScope> findByCode(String code);
}