package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数据范围只读视图集合（容器类，仅作嵌套 record 命名空间）。 */
public final class DataScopeViews {
    private DataScopeViews() {
    }

    /** 数据范围条目（角色编辑下拉数据源，只读、不开放写）。 */
    public record Scope(
            @Schema(description = "数据范围编码") String code,
            @Schema(description = "数据范围名称") String name,
            @Schema(description = "数据范围说明") String description) {
    }
}
