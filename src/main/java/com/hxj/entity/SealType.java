package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 印章类型枚举。 */
@Schema(description = "印章类型：COMPANY_SEAL公章/CONTRACT_SEAL合同章/FINANCIAL_SEAL财务章/LEGAL_PERSON_SEAL法人章/INVOICE_SEAL发票章")
public enum SealType {
    COMPANY_SEAL(0, "公章"),
    CONTRACT_SEAL(1, "合同章"),
    FINANCIAL_SEAL(2, "财务章"),
    LEGAL_PERSON_SEAL(3, "法人章"),
    INVOICE_SEAL(4, "发票章");

    private final int code;
    private final String label;

    SealType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SealType fromCode(int code) {
        for (SealType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知印章类型 code: " + code);
    }
}
