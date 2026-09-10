package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 通过但补材料的补充时点。 */
public enum SupplementModeEnum {
    /** 即补 */
    BEFORE_PAY(1,"即补"),
    /** 后补 */
    AFTER_PAY(2,"后补");



    private final Integer code;
    private final String text;

    SupplementModeEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static SupplementModeEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
