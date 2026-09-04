package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RoleTreeNode(
        @Schema(description = "角色ID") Long id,
        @Schema(description = "角色名称") String name,
        @Schema(description = "适用岗位") String post,
        @Schema(description = "数据权限范围编码") String dataScope,
        @Schema(description = "权限编码列表") List<String> permissions,
        @Schema(description = "成员姓名列表") List<String> members) {

    /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
    public RoleTreeNode {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        members = members == null ? List.of() : List.copyOf(members);
    }
}