package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 审批人的<b>组织范围维度</b>——回答"在哪个部门范围内匹配人"（钉钉同构）。
 *
 * <p>与 {@link AssigneeSubjectEnum} 正交：主体回答"找哪一类人"，范围回答"在谁的部门里找"。
 * 钉钉的"角色管理范围"（按发起人部门精确匹配）与"部门控件对应角色/主管"即由此表达。
 *
 * <ul>
 *   <li>{@link #GLOBAL}：不限部门——角色的全部成员 / 主管链的起点即申请人本人；</li>
 *   <li>{@link #INITIATOR_DEPT}：发起人所在部门（钉钉"角色 + 管理范围"的语义）；</li>
 *   <li>{@link #FORM_DEPT}：表单中"部门控件"选中的部门，字段键存于 {@code assigneeScopeValue}
 *       （钉钉"部门控件对应角色/主管"）。</li>
 * </ul>
 */
public enum AssigneeScopeEnum {

    /** 全局（不限部门）。 */
    GLOBAL,

    /** 按发起人所在部门。 */
    INITIATOR_DEPT,

    /** 按表单部门控件选中的部门。 */
    FORM_DEPT;

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
