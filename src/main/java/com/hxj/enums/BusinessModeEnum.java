package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 业务模式（闭店类业务的条件分支变量）。
 *
 * <p>提交单据时由前端表单选择，注入 Flowable 流程变量 {@code businessMode}，
 * 供流程配置的条件分支规则（如"直销模式跳过直属主管"）比对。
 */
public enum BusinessModeEnum {
    /** 直销。 */
    DIRECT_SALES(1, "直销"),
    /** 代销。 */
    CONSIGNMENT(2, "代销");


    private final Integer code;
    private final String text;

    BusinessModeEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    /**
     * 反序列化：接受枚举名（DIRECT_SALES）、中文（直销）或编码。
     * 空白输入归一化为 null（业务模式是可选字段，仅闭店类业务需要）。
     */
    @JsonCreator
    public static BusinessModeEnum fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
