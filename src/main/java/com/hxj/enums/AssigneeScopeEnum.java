package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 审批人的<b>范围维度</b>——与 {@link AssigneeTypeEnum}（机制维度）正交，仅 {@link AssigneeTypeEnum#ROLE} 使用。
 *
 * <p>钉钉同构的关键：机制层只回答"用哪种方式找人"，范围层回答"在多大范围内找"。
 * 这样"各部门会计""各部门HR""各部门出纳"等业务概念全部退化为
 * <b>角色数据 + 范围参数</b>，不需要新增审批人类型，也不需要写代码。
 *
 * <ul>
 *   <li>{@link #GLOBAL}：角色全部成员——静态候选组，全员可见可领（如出纳、公司领导、内控）；</li>
 *   <li>{@link #INITIATOR_DEPT}：仅服务/归属发起人所在部门的角色成员——提交时按部门解析
 *       （如核算会计、部门HR；服务关系见 {@code sys_user_service_dept}，缺省退化为成员所属部门）。</li>
 * </ul>
 */
public enum AssigneeScopeEnum {

    /** 角色全部成员（静态候选组）。 */
    GLOBAL,

    /** 按发起人所在部门过滤的角色成员（提交时解析）。 */
    INITIATOR_DEPT;

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static AssigneeScopeEnum fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}
