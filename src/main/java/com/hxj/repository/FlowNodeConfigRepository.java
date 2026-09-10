package com.hxj.repository;

import com.hxj.entity.FlowNodeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlowNodeConfigRepository extends JpaRepository<FlowNodeConfig, Long> {

    /** 全部非空审批角色字符串（可能为 "角色A/角色B" 组合形式，由服务层按段拆分精确匹配）。 */
    @Query("select distinct n.assigneeRole from FlowNodeConfig n where n.assigneeRole is not null")
    List<String> findAllAssigneeRoles();

    /**
     * 按流程配置删除全部节点。
     *
     * <p>与 flow_condition_rule 同理：flow_node_config 上有 (flow_config_id, sort_order)
     * 唯一键，update 场景先删后插，避开唯一键的 flush 顺序冲突。
     */
    @Modifying
    @Query("delete from FlowNodeConfig n where n.flowConfig.id = :configId")
    void deleteByConfigId(@Param("configId") Long configId);

    /** 绑定了指定角色（子串匹配；组合串按段精确同步由服务层处理）的节点。 */
    List<FlowNodeConfig> findByAssigneeRoleContaining(String roleName);
}
