package com.hxj.approval;

import io.swagger.v3.oas.annotations.media.Schema;

/** 管理端任务转办请求：被转办人账号必填，转办说明可选（缺省自动生成留痕文案）。 */
@Schema(description = "管理端任务转办请求")
public record TransferTaskRequest(
        @Schema(description = "被转办人登录账号", example = "wuhezhen") String account,
        @Schema(description = "转办说明（可选）") String comment) {
}
