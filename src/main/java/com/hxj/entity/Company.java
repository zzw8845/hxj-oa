package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 公司枚举。 */
@Schema(description = "公司：HAIXIAJIN海峡金/HAIXIAJIN_SUPPLY_CHAIN海峡金供应链")
public enum Company {
    HAIXIAJIN(0, "海峡金"),
    HAIXIAJIN_SUPPLY_CHAIN(1, "海峡金供应链");

    private final int code;
    private final String label;

    Company(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static Company fromCode(int code) {
        for (Company value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知公司 code: " + code);
    }
}
