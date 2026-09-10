package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 抄送记录的来源。 */
public enum CcSourceEnum {
    /** 流程：流程节点自动抄送。 */
    FLOW(1,"流程"),
    /** 自选：申请人在页面自选的抄送人。 */
    SELF_SELECTED(2,"自选");

    private final Integer code;
    private final String text;

    CcSourceEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static CcSourceEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
