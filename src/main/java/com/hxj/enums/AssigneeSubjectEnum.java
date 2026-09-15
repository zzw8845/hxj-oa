package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 审批人<b>主体维度</b>——回答"从哪儿找人"（钉钉同构的机制层，与业务无关）。
 *
 * <p>钉钉的 10 种审批人类型 = 本枚举 × {@link AssigneeScopeEnum} × 层级 × {@link ApproveModeEnum}
 * 的正交组合，不新增类型即可覆盖全部业务场景：
 * <ul>
 *   <li>指定成员 = {@link #MEMBER}；</li>
 *   <li>发起人自己 = {@link #INITIATOR}；发起人自选 = {@link #INITIATOR_SELECT}；</li>
 *   <li>角色 = {@link #ROLE} + {@link AssigneeScopeEnum#INITIATOR_DEPT}；</li>
 *   <li>部门控件对应角色 = {@link #ROLE} + {@link AssigneeScopeEnum#FORM_DEPT}；</li>
 *   <li>直属主管 = {@link #SUPERIOR} + 层级；部门主管 = {@link #DEPT_HEAD} + 层级；</li>
 *   <li>连续多级主管 = {@link #DEPT_HEAD} + 层级 + 链式；</li>
 *   <li>部门控件对应主管 = {@link #DEPT_HEAD} + {@link AssigneeScopeEnum#FORM_DEPT}；</li>
 *   <li>表单内联系人 = {@link #FORM_MEMBER}。</li>
 * </ul>
 */
public enum AssigneeSubjectEnum {

    /** 指定成员：{@code assigneeValue} 为账号列表（逗号分隔）。 */
    MEMBER,

    /** 发起人自己：指派单据申请人（签收/归还/确认结果等回环节点）。 */
    INITIATOR,

    /** 发起人自选：提交时由申请人指定，{@code assigneeValue} 限定可选范围（空=全公司）。 */
    INITIATOR_SELECT,

    /** 角色成员：{@code assigneeValue} 为角色 ID 列表，范围由 {@link #assigneeScope} 决定。 */
    ROLE,

    /** 直属主管：沿人员档案的业务汇报线（{@code sys_user.manager_id}）向上取第 N 级。 */
    SUPERIOR,

    /** 部门主管：沿部门负责人树（{@code sys_department.leader_user_id}）向上取第 N 级。 */
    DEPT_HEAD,

    /** 表单联系人：{@code assigneeValue} 为表单中人员控件的字段键。 */
    FORM_MEMBER;

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static AssigneeSubjectEnum fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}
