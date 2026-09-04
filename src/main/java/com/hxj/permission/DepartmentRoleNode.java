package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DepartmentRoleNode(
        @Schema(description = "部门名称") String department,
        @Schema(description = "该部门下的角色树节点列表") List<RoleTreeNode> roles) {

    /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
    public DepartmentRoleNode {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}