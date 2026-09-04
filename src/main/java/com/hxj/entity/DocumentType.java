package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 单据类型枚举。 */
@Schema(description = "单据类型：PURCHASE_CONTRACT采购合同/SALES_CONTRACT销售合同/PAYMENT付款单/REIMBURSEMENT报销单/SEAL_APPLICATION用印申请/OTHER其他")
public enum DocumentType {
    PURCHASE_CONTRACT(0, "采购合同"),
    SALES_CONTRACT(1, "销售合同"),
    PAYMENT(2, "付款单"),
    REIMBURSEMENT(3, "报销单"),
    SEAL_APPLICATION(4, "用印申请"),
    OTHER(5, "其他");

    private final int code;
    private final String label;

    DocumentType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static DocumentType fromCode(int code) {
        for (DocumentType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知单据类型 code: " + code);
    }
}
