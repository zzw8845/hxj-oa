package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 抄送来源枚举。 */
@Schema(description = "抄送来源：DOCUMENT_SUBMIT单据提交/SUPPLEMENT补充材料/APPROVAL审批/REJECT驳回/SEAL用印/FLOW流程/SELF_SELECTED自选")
public enum CcSource {
    DOCUMENT_SUBMIT(0, "单据提交"),
    SUPPLEMENT(1, "补充材料"),
    APPROVAL(2, "审批"),
    REJECT(3, "驳回"),
    SEAL(4, "用印"),
    FLOW(5, "流程"),
    SELF_SELECTED(6, "自选");

    private final int code;
    private final String label;

    CcSource(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CcSource fromCode(int code) {
        for (CcSource value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知抄送来源 code: " + code);
    }
}
