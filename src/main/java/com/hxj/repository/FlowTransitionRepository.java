package com.hxj.repository;

import com.hxj.entity.FlowTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 流程转移边仓储（图模型拓扑数据源）。 */
public interface FlowTransitionRepository extends JpaRepository<FlowTransition, Long> {

    List<FlowTransition> findByFlowConfigIdOrderBySortOrderAsc(Long flowConfigId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FlowTransition t where t.flowConfig.id = :configId")
    void deleteByConfigId(@Param("configId") Long configId);
}
