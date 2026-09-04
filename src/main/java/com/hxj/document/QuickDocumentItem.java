package com.hxj.document;

import com.hxj.entity.BusinessType;
import io.swagger.v3.oas.annotations.media.Schema;

public record QuickDocumentItem(
        @Schema(description = "快捷单据ID") Long id,
        @Schema(description = "业务类型（枚举）") BusinessType businessType,
        @Schema(description = "名称") String name,
        @Schema(description = "排序号，升序排列") Integer sortOrder) {}