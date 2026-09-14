package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveRoleRequest(
        @Schema(description = "角色名称") @NotBlank String name,
        @Schema(description = "归属部门ID（sys_department 字典），与 RoleResponse.departmentId 同源可原样回写")
        @NotNull Long departmentId,
        @Schema(description = "适用岗位") @NotBlank String post,
        @Schema(description = "数据范围类型（ALL/OWN/DEPT/DEPT_AND_CHILD/CUSTOM），与 RoleResponse.dataScope 同源可原样回写")
        @NotBlank String dataScope,
        @Schema(description = "自定义数据范围的部门ID列表（仅 dataScope=CUSTOM 时生效），与 RoleResponse.scopeDepartmentIds 同源可原样回写")
        List<Long> scopeDepartmentIds,
        @Schema(description = "权限点编码列表（如 VIEW_OWN_FORMS），与 RoleResponse.permissions 同源可原样回写")
        @NotEmpty List<String> permissions) {

    /**
     * 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。
     *
     * <p>请求 DTO 来自外部输入，拷贝可确保后续业务流转期间入参不被改动。
     */
    public SaveRoleRequest {
        scopeDepartmentIds = scopeDepartmentIds == null ? List.of() : List.copyOf(scopeDepartmentIds);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
