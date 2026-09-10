package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 用印申请支持的用章类型。 */
public enum SealTypeEnum {
    /** 公章：公司公章。 */
    OFFICIAL_SEAL(1,"公章"),
    /** 合同章：合同专用章。 */
    CONTRACT_SEAL(2,"合同章"),
    /** 法人章：法定代表人名章。 */
    LEGAL_REPRESENTATIVE_SEAL(3,"法人章"),
    /** 财务章：财务专用章。 */
    FINANCIAL_SEAL(4,"财务章"),
    /** 发票章：发票专用章。 */
    INVOICE_SEAL(5,"发票章");






    private final Integer code;
    private final String text;

    SealTypeEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static SealTypeEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
