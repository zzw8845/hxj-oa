package com.hxj.workflow;

import io.swagger.v3.oas.annotations.media.Schema;

/** 全局节点处理效率统计。 */
public record WorkflowNodeStat(
        @Schema(description = "节点名称") String nodeName,
        @Schema(description = "已完成单据数") long completedCount,
        @Schema(description = "进行中单据数") long activeCount,
        @Schema(description = "平均完成时长（小时）") double avgCompletionHours) {
}
