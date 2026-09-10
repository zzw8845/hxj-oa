package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 流程管理中的业务分类。 */
public enum FlowCategoryEnum {
    /** 日常：日常报销类流程。 */
    DAILY(1,"日常"),
    /** 业务：业务付款类流程。 */
    BUSINESS(2,"业务"),
    /** 用印：用印申请类流程。 */
    SEAL(3,"用印");


    private final Integer code;
    private final String text;

    FlowCategoryEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static FlowCategoryEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
