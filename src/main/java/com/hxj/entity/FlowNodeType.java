package com.hxj.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 流程节点类型枚举。 */
@Schema(description = "流程节点类型：START开始/APPROVAL审批/CC抄送/SUPPLEMENT补材料/SEAL用印/CONDITION条件/END结束")
public enum FlowNodeType {
    START(0, "开始"),
    APPROVAL(1, "审批"),
    CC(2, "抄送"),
    SUPPLEMENT(3, "补材料"),
    SEAL(4, "用印"),
    CONDITION(5, "条件"),
    END(6, "结束");

    private final int code;
    private final String label;

    FlowNodeType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static FlowNodeType fromCode(int code) {
        for (FlowNodeType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知流程节点类型 code: " + code);
    }
}
