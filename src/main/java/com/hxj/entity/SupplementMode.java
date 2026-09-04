package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 补材料模式枚举。 */
@Schema(description = "补材料模式：REPLACE替换/ADD追加")
public enum SupplementMode {
    REPLACE(0, "替换"),
    ADD(1, "追加");

    private final int code;
    private final String label;

    SupplementMode(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static SupplementMode fromCode(int code) {
        for (SupplementMode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知补材料模式 code: " + code);
    }
}
