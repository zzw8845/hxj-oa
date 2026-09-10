package com.hxj.approval;

import com.hxj.enums.SupplementModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 审批操作请求 DTO。
 *
 * <p>用于审批通过/驳回/加签等操作，封装审批意见、证据附件、驳回目标节点、补材料要求等。
 */
public record ApprovalRequest(
        @Schema(description = "审批意见/备注")
        String comment,
        @Schema(description = "证据附件 ID（如驳回时上传的证明材料）")
        Long evidenceFileId,
        @Schema(description = "驳回目标节点（退回到指定节点，为空则退回到上一节点）")
        String rejectTarget,
        @Schema(description = "驳回时要求补充的材料说明")
        String rejectMaterials,
        @Schema(description = "补材料模式（枚举）")
        SupplementModeEnum supplementMode,
        @Schema(description = "补材料目标节点")
        String supplementTarget,
        @Schema(description = "补材料要求说明")
        String supplementMaterials) {
}
