package com.hxj.approval;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 流程历史条目：业务留痕与 Flowable 活动合并后的统一视图。 */
public record ApprovalHistoryItemResponse(
        @Schema(description = "来源（业务系统 / 流程引擎）") String source,
        @Schema(description = "节点名称") String nodeName,
        @Schema(description = "操作人") String operator,
        @Schema(description = "动作（如 提交 / 通过 / 驳回）") String action,
        @Schema(description = "审批意见") String comment,
        @Schema(description = "佐证材料附件ID") Long evidenceAttachmentId,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间；为空表示进行中") LocalDateTime endedAt) {
}
