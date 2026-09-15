package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 节点审批类型（钉钉同构）——节点是"人工审"还是"系统自动判"。
 *
 * <p>自动通过与自动拒绝通常配合条件分支使用，实现全自动流转（如"金额小于 500 直接通过"）。
 *
 * <ul>
 *   <li>{@link #MANUAL}：人工审批（默认）；</li>
 *   <li>{@link #AUTO_PASS}：自动通过——BPMN 编译为旁路（不生成用户任务）；</li>
 *   <li>{@link #AUTO_REJECT}：自动拒绝——BPMN 编译为拒绝处理器（终止流程并标记拒绝）。</li>
 * </ul>
 */
public enum NodeApprovalModeEnum {

    MANUAL,

    AUTO_PASS,

    AUTO_REJECT;

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static NodeApprovalModeEnum fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}
