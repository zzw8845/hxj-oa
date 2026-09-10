package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增/编辑岗位请求（岗位归属于部门，对齐 role-permission 规范）。 */
public record SavePostRequest(
        @Schema(description = "归属部门ID（sys_department 字典）") @NotNull Long departmentId,
        @Schema(description = "岗位名称（唯一）") @NotBlank @Size(max = 100) String name) {
}
