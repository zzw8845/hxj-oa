package com.hxj.entity;

/** 流程节点的行为类型。 */
public enum FlowNodeType {
    /** 开始：流程起始节点。 */
    START,
    /** 审批：审批节点。 */
    APPROVAL,
    /** 条件：条件分支节点。 */
    CONDITION,
    /** 处理人：指定处理人的节点。 */
    HANDLER,
    /** 抄送：抄送节点。 */
    CC,
    /** 结束：流程结束节点。 */
    END
}
