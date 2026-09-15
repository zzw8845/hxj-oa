package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 条件分支支持的比较运算符。 */
public enum ConditionOperatorEnum {
    /** 等于。 */
    EQUAL(1,"等于"),
    /** 不等于。 */
    NOT_EQUAL(2,"不等于"),
    /** 大于。 */
    GREATER_THAN(3,"大于"),
    /** 大于等于。 */
    GREATER_THAN_OR_EQUAL(4,"大于等于"),
    /** 小于。 */
    LESS_THAN(5,"小于"),
    /** 小于等于。 */
    LESS_THAN_OR_EQUAL(6,"小于等于"),
    /** 属于（集合判断，期望值为逗号分隔的多个值；编译为等值或链）。 */
    IN(7,"属于"),
    /** 不属于（集合判断；编译为不等值与链）。 */
    NOT_IN(8,"不属于");


    private final Integer code;
    private final String text;

    ConditionOperatorEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static ConditionOperatorEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
