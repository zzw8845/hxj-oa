package com.hxj.repository;

import com.hxj.entity.FlowConditionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlowConditionRuleRepository extends JpaRepository<FlowConditionRule, Long> {

    /**
     * 按流程配置删除全部条件规则。
     *
     * <p>update 场景必须"先删后插"：flow_condition_rule 上有 (flow_config_id, sort_order)
     * 唯一键，直接 clear + 重加时 Hibernate 的 flush 顺序可能先插后删，在 MySQL 上触发
     * Duplicate entry 冲突（修改带条件分支的流程会 500）。
     */
    @Modifying
    @Query("delete from FlowConditionRule r where r.flowConfig.id = :configId")
    void deleteByConfigId(@Param("configId") Long configId);
}
