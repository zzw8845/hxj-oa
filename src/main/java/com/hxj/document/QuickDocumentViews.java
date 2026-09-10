package com.hxj.document;

import com.hxj.enums.BusinessTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;

/** 快捷单据目录管理视图集合（容器类，仅作嵌套 record 命名空间）。 */
public final class QuickDocumentViews {
    private QuickDocumentViews() {
    }

    /** 快捷单据条目。 */
    public record Entry(
            @Schema(description = "快捷单据ID") Long id,
            @Schema(description = "业务类型（枚举）") BusinessTypeEnum businessType,
            @Schema(description = "单据名称") String name,
            @Schema(description = "排序号") Integer sortOrder) {
    }
}
