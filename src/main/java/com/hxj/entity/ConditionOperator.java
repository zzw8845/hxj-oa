package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 条件操作符枚举。 */
@Schema(description = "条件操作符：EQUAL等于/NOT_EQUAL不等于/GREATER_THAN大于/GREATER_THAN_OR_EQUAL大于等于/LESS_THAN小于/LESS_THAN_OR_EQUAL小于等于/CONTAINS包含/IN在集合中")
public enum ConditionOperator {
    EQUAL(0, "等于"),
    NOT_EQUAL(1, "不等于"),
    GREATER_THAN(2, "大于"),
    GREATER_THAN_OR_EQUAL(3, "大于等于"),
    LESS_THAN(4, "小于"),
    LESS_THAN_OR_EQUAL(5, "小于等于"),
    CONTAINS(6, "包含"),
    IN(7, "在集合中");

    private final int code;
    private final String label;

    ConditionOperator(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ConditionOperator fromCode(int code) {
        for (ConditionOperator value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知条件操作符 code: " + code);
    }
}
