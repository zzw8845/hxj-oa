package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 审批操作枚举。 */
@Schema(description = "审批操作：APPROVE通过/REJECT驳回/APPROVE_WITH_SUPPLEMENT通过但需补材料/ADD_SIGN加签/SUPPLEMENT补充材料/SUPPLEMENT_RETURN补充材料回传/SEAL用印/SEAL_RETURN用印回传")
public enum ApprovalAction {
    APPROVE(0, "通过"),
    REJECT(1, "驳回"),
    APPROVE_WITH_SUPPLEMENT(2, "通过但需补材料"),
    ADD_SIGN(3, "加签"),
    SUPPLEMENT(4, "补充材料"),
    SUPPLEMENT_RETURN(5, "补充材料回传"),
    SEAL(6, "用印"),
    SEAL_RETURN(7, "用印回传");

    private final int code;
    private final String label;

    ApprovalAction(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ApprovalAction fromCode(int code) {
        for (ApprovalAction value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知审批操作 code: " + code);
    }
}
