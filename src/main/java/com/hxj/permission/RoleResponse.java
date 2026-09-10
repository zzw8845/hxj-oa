package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RoleResponse(
        @Schema(description = "角色ID") Long id,
        @Schema(description = "角色名称") String name,
        @Schema(description = "适用部门（该角色在此部门下生效）") String department,
        @Schema(description = "适用岗位") String post,
        @Schema(description = "数据范围类型（ALL=全部 / OWN=仅本人 / DEPT=本部门 / DEPT_AND_CHILD=本部门及以下 / CUSTOM=自定义部门集合）")
        String dataScope,
        @Schema(description = "自定义数据范围的部门ID列表（仅 CUSTOM 时有值）") List<Long> scopeDepartmentIds,
        @Schema(description = "权限编码列表") List<String> permissions,
        @Schema(description = "成员姓名列表") List<String> members) {

    /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
    public RoleResponse {
        scopeDepartmentIds = scopeDepartmentIds == null ? List.of() : List.copyOf(scopeDepartmentIds);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        members = members == null ? List.of() : List.copyOf(members);
    }
}
