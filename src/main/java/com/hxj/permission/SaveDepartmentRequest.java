package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/编辑部门请求（编辑时 parentId=null 表示移动为根部门）。 */
public record SaveDepartmentRequest(
        @Schema(description = "部门名称（全公司唯一）") @NotBlank @Size(max = 100) String name,
        @Schema(description = "上级部门ID；新增时为空表示根部门，编辑时为空表示移动为根部门")
        Long parentId,
        @Schema(description = "同级排序号，默认 0") Integer sortOrder) {

    /** 归一化：排序号缺省为 0。 */
    public SaveDepartmentRequest {
        sortOrder = sortOrder == null ? 0 : sortOrder;
    }
}
