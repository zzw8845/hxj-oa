package com.hxj.workflow;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record WorkflowHistoryItem(
        @Schema(description = "活动实例ID") String activityId,
        @Schema(description = "节点名称") String nodeName,
        @Schema(description = "活动类型（如 userTask、serviceTask）") String activityType,
        @Schema(description = "处理人") String assignee,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间；为空表示进行中") LocalDateTime endedAt) {
}