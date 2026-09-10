package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

/** 岗位管理视图集合（容器类，仅作嵌套 record 命名空间）。 */
public final class PostViews {
    private PostViews() {
    }

    /** 岗位条目（departmentId 为空表示通用岗位）。 */
    public record Post(
            @Schema(description = "岗位ID") Long id,
            @Schema(description = "归属部门ID，空表示通用岗位") Long departmentId,
            @Schema(description = "岗位名称") String name) {
    }
}
