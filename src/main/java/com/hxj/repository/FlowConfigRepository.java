package com.hxj.repository;

import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowConfigRepository extends JpaRepository<FlowConfig, Long> {
    Optional<FlowConfig> findByType(String type);
    List<FlowConfig> findByCategory(FlowCategory category);
}