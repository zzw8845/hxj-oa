package com.hxj.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 管理员作废单据请求。 */
public record VoidDocumentRequest(
        @Schema(description = "作废原因（留痕必填）") @NotBlank String comment) {
}
