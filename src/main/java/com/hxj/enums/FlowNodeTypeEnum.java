package com.hxj.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** 流程节点的行为类型。 */
public enum FlowNodeTypeEnum {
    /** 开始：流程起始节点。 */
    START(1,"开始"),
    /** 审批：审批节点。 */
    APPROVAL(2,"审批"),
    /** 条件：条件分支节点。 */
    CONDITION(3,"条件"),
    /** 处理人：指定处理人的节点。 */
    HANDLER(4,"处理人"),
    /** 抄送：抄送节点。 */
    CC(5,"抄送"),
    /** 结束：流程结束节点。 */
    END(6,"结束");




    private final Integer code;
    private final String text;

    FlowNodeTypeEnum(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    @JsonValue
    public String getName() {
        return name();
    }

    @JsonCreator
    public static FlowNodeTypeEnum fromText(String text) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(text) || e.text.equals(text) || e.code.toString().equals(text))
                .findFirst()
                .orElse(null);
    }
}
