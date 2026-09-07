package com.hxj.entity;

/** 单据生命周期状态。 */
public enum DocumentStatus {
    /** 待提交：单据创建后尚未提交审批。 */
    PENDING,
    /** 审批中：单据已提交，正在流转审批。 */
    APPROVING,
    /** 已通过：审批流程结束且通过。 */
    APPROVED,
    /** 已驳回：审批未通过，流程终止。 */
    REJECTED,
    /** 待补充材料：审批中要求申请人补充材料。 */
    SUPPLEMENT_REQUIRED
}
