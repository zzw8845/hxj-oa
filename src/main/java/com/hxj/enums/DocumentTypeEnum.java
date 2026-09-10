package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 业务类型在列表和台账中的展示单据类型。 */
public enum DocumentTypeEnum {
    /** 日常申请单。 */
    DAILY_APPLICATION(1,"日常申请单"),
    /** 付款申请单。 */
    PAYMENT_APPLICATION(2,"付款申请单"),
    /** 用印申请单。 */
    SEAL_APPLICATION(3,"用印申请单");


    private final Integer code;
    private final String text;

    DocumentTypeEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static DocumentTypeEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
