package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 系统支持的三类业务单据（日常付款/业务付款/用印申请）。 */
public enum BusinessTypeEnum {
    /** 日常付款：日常费用类付款业务，含费用报销、差旅费、招待费、借款申请等。 */
    DAILY_PAYMENT(1,"日常付款","BX"),
    /** 业务付款：应付款申请、供应商货款、工资社保等业务侧付款。 */
    BUSINESS_PAYMENT(2,"业务付款","FK"),
    /** 用印申请：用印及证照类申请，不涉及资金收付。 */
    SEAL_APPLICATION(3,"用印申请","YY");

    private final Integer code;
    private final String text;
    private final String codePrefix;

    BusinessTypeEnum(Integer code, String text, String codePrefix) {
        this.code = code;
        this.text = text;
        this.codePrefix = codePrefix;
    }
    /** 单据编号前缀，仅供 DocumentCodeGenerator 内部使用，不作为 JSON 序列化值。 */
    public String getCodePrefix() {
        return codePrefix;
    }

    public DocumentTypeEnum toDocumentType() {
        return switch (this) {
            case DAILY_PAYMENT -> DocumentTypeEnum.DAILY_APPLICATION;
            case BUSINESS_PAYMENT -> DocumentTypeEnum.PAYMENT_APPLICATION;
            case SEAL_APPLICATION -> DocumentTypeEnum.SEAL_APPLICATION;
        };
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static BusinessTypeEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
