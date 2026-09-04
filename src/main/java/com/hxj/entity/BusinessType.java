package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 业务类型枚举。 */
@Schema(description = "业务类型：PURCHASE采购类/SALES销售类/CONTRACT合同类/PAYMENT付款类/REIMBURSEMENT报销类/OTHER其他/SEAL_APPLICATION用印申请/BUSINESS_PAYMENT业务付款/DAILY_PAYMENT日常报销")
public enum BusinessType {
    PURCHASE(0, "采购类"),
    SALES(1, "销售类"),
    CONTRACT(2, "合同类"),
    PAYMENT(3, "付款类"),
    REIMBURSEMENT(4, "报销类"),
    OTHER(5, "其他"),
    SEAL_APPLICATION(6, "用印申请"),
    BUSINESS_PAYMENT(7, "业务付款"),
    DAILY_PAYMENT(8, "日常报销");

    private final int code;
    private final String label;

    BusinessType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static BusinessType fromCode(int code) {
        for (BusinessType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知业务类型 code: " + code);
    }

    /** 单据编号前缀：与 DocumentCodeGenerator 的编号规则一致。 */
    public String getCodePrefix() {
        return switch (this) {
            case PURCHASE -> "CG";
            case SALES -> "XS";
            case CONTRACT -> "HT";
            case PAYMENT -> "ZF";
            case REIMBURSEMENT -> "BM";
            case OTHER -> "QT";
            case SEAL_APPLICATION -> "YY";
            case BUSINESS_PAYMENT -> "FK";
            case DAILY_PAYMENT -> "BX";
        };
    }
}
