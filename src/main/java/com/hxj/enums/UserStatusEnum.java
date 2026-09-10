package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 员工在职状态。 */
public enum UserStatusEnum {
    /** 在职：正常使用状态。 */
    ACTIVE(1,"在职"),
    /** 离职：员工已离职。 */
    RESIGNED(2,"离职");




    private final Integer code;
    private final String text;

    UserStatusEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static UserStatusEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
