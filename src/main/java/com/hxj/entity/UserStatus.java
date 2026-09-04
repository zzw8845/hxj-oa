package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 用户状态枚举。 */
@Schema(description = "用户状态：ACTIVE在职/INACTIVE离职/DISABLED禁用")
public enum UserStatus {
    ACTIVE(0, "在职"),
    INACTIVE(1, "离职"),
    DISABLED(2, "禁用");

    private final int code;
    private final String label;

    UserStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static UserStatus fromCode(int code) {
        for (UserStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知用户状态 code: " + code);
    }
}
