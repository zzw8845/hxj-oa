package com.hxj.entity;

/** 审批记录支持的操作类型。 */
public enum ApprovalAction {
    /** 通过：审批人同意，流程继续。 */
    APPROVE,
    /** 驳回：审批人拒绝，流程终止。 */
    REJECT,
    /** 补充材料：当前处理人提交补充材料。 */
    SUPPLEMENT,
    /** 加签：在当前节点追加审批人。 */
    SIGN
}
