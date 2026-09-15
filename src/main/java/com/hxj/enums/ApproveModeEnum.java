package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 多人审批方式（钉钉同构）——回答"同一节点解析出多人时怎么审"。
 *
 * <ul>
 *   <li>{@link #OR_SIGN}：或签——多人共享同一待办，任意一人同意即通过（Flowable 候选组/候选人）；</li>
 *   <li>{@link #AND_SIGN}：会签——每人生成待办，全部同意才通过（并行多实例）；</li>
 *   <li>{@link #SEQUENTIAL}：依次审批——按顺序逐个生成待办，全部同意才通过（串行多实例）。</li>
 * </ul>
 */
public enum ApproveModeEnum {

    OR_SIGN,

    AND_SIGN,

    SEQUENTIAL;

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static ApproveModeEnum fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}
