package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 审批人解析为空时的处理策略（钉钉同构的四种）——消除"卡单等管理员救"。
 *
 * <ul>
 *   <li>{@link #AUTO_PASS}：自动通过（流程跳过该节点）——由提交关口写入跳过变量，BPMN 前置网关旁路；</li>
 *   <li>{@link #AUTO_REJECT}：自动拒绝（终止流程并标记拒绝）——由提交关口写入拒绝变量，BPMN 前置网关旁路；</li>
 *   <li>{@link #TO_ADMIN}：转交模板管理员——提交关口把兜底人写入指派变量（无需网关）；</li>
 *   <li>{@link #TO_USER}：转交指定成员（{@code emptyFallback} 存账号）——提交关口写入指派变量。</li>
 * </ul>
 */
public enum EmptyAssigneeStrategyEnum {

    AUTO_PASS,

    AUTO_REJECT,

    TO_ADMIN,

    TO_USER;

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static EmptyAssigneeStrategyEnum fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}
