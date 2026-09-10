package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 审批记录支持的操作类型。 */
public enum ApprovalActionEnum {
    /** 通过：审批人同意，流程继续。 */
    APPROVE(1,"通过"),
    /** 驳回：审批人拒绝，流程终止。 */
    REJECT(2,"驳回"),
    /** 补充材料：当前处理人提交补充材料。 */
    SUPPLEMENT(3,"补充材料"),
    /** 加签：在当前节点追加审批人。 */
    SIGN(4,"加签"),
    /** 作废：申请人撤回或管理员作废，流程终止留痕。 */
    VOID(5,"作废");

    private final Integer code;
    private final String text;

    ApprovalActionEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static ApprovalActionEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }

}
