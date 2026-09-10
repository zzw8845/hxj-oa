package com.hxj.document;

import com.hxj.enums.BusinessTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增/编辑快捷单据请求。 */
public record SaveQuickDocumentRequest(
        @Schema(description = "业务类型（枚举）") @NotNull BusinessTypeEnum businessType,
        @Schema(description = "单据名称（同业务类型下唯一）") @NotBlank @Size(max = 200) String name,
        @Schema(description = "排序号，默认 0") Integer sortOrder) {

    /** 归一化：排序号缺省为 0。 */
    public SaveQuickDocumentRequest {
        sortOrder = sortOrder == null ? 0 : sortOrder;
    }
}
