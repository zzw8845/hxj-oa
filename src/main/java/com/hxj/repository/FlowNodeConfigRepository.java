package com.hxj.repository;

import com.hxj.entity.FlowNodeConfig;
import com.hxj.enums.AssigneeTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlowNodeConfigRepository extends JpaRepository<FlowNodeConfig, Long> {

    /**
     * 按流程配置删除全部节点。
     *
     * <p>flow_node_config 上有 (flow_config_id, sort_order) 唯一键，
     * update 场景先删后插，避开唯一键的 flush 顺序冲突。
     */
    @Modifying
    @Query("delete from FlowNodeConfig n where n.flowConfig.id = :configId")
    void deleteByConfigId(@Param("configId") Long configId);

    /** 指派协议为角色候选组的节点（角色删除守卫按 assignee_value 中的角色 ID 精确匹配）。 */
    List<FlowNodeConfig> findByAssigneeType(AssigneeTypeEnum assigneeType);
}
