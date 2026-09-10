package com.hxj.approval;

import com.hxj.enums.DocumentStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApprovalResultResponse(
        @Schema(description = "单据ID") Long documentId,
        @Schema(description = "审批后单据状态（枚举）") DocumentStatusEnum status,
        @Schema(description = "当前节点；为空表示流程已结束") String currentNode,
        @Schema(description = "是否已归档") boolean archived) {
}