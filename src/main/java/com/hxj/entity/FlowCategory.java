package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 流程分类枚举。 */
@Schema(description = "流程分类：PURCHASE采购/SALES销售/CONTRACT合同/PAYMENT付款/REIMBURSEMENT报销/OTHER其他")
public enum FlowCategory {
    PURCHASE(0, "采购"),
    SALES(1, "销售"),
    CONTRACT(2, "合同"),
    PAYMENT(3, "付款"),
    REIMBURSEMENT(4, "报销"),
    OTHER(5, "其他");

    private final int code;
    private final String label;

    FlowCategory(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static FlowCategory fromCode(int code) {
        for (FlowCategory value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知流程分类 code: " + code);
    }
}
