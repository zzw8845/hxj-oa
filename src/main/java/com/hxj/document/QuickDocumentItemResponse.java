package com.hxj.document;

import com.hxj.enums.BusinessTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;

public record QuickDocumentItemResponse(
        @Schema(description = "快捷单据ID") Long id,
        @Schema(description = "业务类型（枚举）") BusinessTypeEnum businessType,
        @Schema(description = "名称") String name,
        @Schema(description = "排序号，升序排列") Integer sortOrder) {}