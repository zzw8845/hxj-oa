package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 单据生命周期状态。 */
public enum DocumentStatusEnum {
    /** 待提交：单据创建后尚未提交审批。 */
    PENDING(1,"待提交"),
    /** 审批中：单据已提交，正在流转审批。 */
    APPROVING(2,"审批中"),
    /** 已通过：审批流程结束且通过。 */
    APPROVED(3,"已通过"),
    /** 已驳回：审批未通过，流程终止。 */
    REJECTED(4,"已驳回"),
    /** 待补充材料：审批中要求申请人补充材料。 */
    SUPPLEMENT_REQUIRED(5,"待补充材料"),
    /** 已作废：申请人撤回或管理员作废，流程终止，终态不可逆。 */
    VOIDED(6,"已作废");


    private final Integer code;
    private final String text;

    DocumentStatusEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static DocumentStatusEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
