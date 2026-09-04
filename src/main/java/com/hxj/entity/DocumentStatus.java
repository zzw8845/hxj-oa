package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 单据生命周期状态。 */
@Schema(description = "单据生命周期状态")
public enum DocumentStatus {
    PENDING(0, "待提交"),
    APPROVING(1, "审批中"),
    APPROVED(2, "已通过"),
    REJECTED(3, "已驳回"),
    SUPPLEMENT_REQUIRED(4, "待补充材料");

    private final int code;
    private final String label;

    DocumentStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static DocumentStatus fromCode(int code) {
        for (DocumentStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知单据状态 code: " + code);
    }
}
