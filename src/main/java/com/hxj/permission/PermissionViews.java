package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

/** 权限点只读视图集合（容器类，仅作嵌套 record 命名空间）。 */
public final class PermissionViews {
    private PermissionViews() {
    }

    /** 权限点条目（角色编辑下拉数据源，只读、不开放写）。 */
    public record PermissionPoint(
            @Schema(description = "权限点编码") String code,
            @Schema(description = "权限点名称") String name,
            @Schema(description = "权限点说明") String description) {
    }
}
