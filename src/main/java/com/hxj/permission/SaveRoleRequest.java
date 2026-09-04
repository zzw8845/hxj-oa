package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveRoleRequest(
        @Schema(description = "角色名称") @NotBlank String name,
        @Schema(description = "适用部门") @NotBlank String department,
        @Schema(description = "适用岗位") @NotBlank String post,
        @Schema(description = "数据权限范围ID") @NotNull Long dataScopeId,
        @Schema(description = "授予的权限ID列表") @NotEmpty List<Long> permissionIds) {

    /**
     * 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。
     *
     * <p>请求 DTO 来自外部输入，拷贝可确保后续业务流转期间入参不被改动。
     */
    public SaveRoleRequest {
        permissionIds = permissionIds == null ? List.of() : List.copyOf(permissionIds);
    }
}