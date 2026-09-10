package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 付款类单据可选择的所属公司。 */
public enum CompanyEnum {
    /** 海峡金：主体公司。 */
    HAI_XIA_JIN(1,"海峡金"),
    /** 海峡金供应链：供应链子公司。 */
    HAI_XIA_JIN_SUPPLY_CHAIN(2,"海峡金供应链");


    private final Integer code;
    private final String text;

    CompanyEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static CompanyEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }


}
