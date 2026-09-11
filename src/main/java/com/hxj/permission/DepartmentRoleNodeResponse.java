package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 部门角色架构树节点：真树形结构（部门闭包表层级），角色按归属部门挂载，
 * 供前端组织权限控制台直接渲染与操作定位。
 */
public record DepartmentRoleNodeResponse(
        @Schema(description = "部门ID（编辑/新增子部门时定位用）") Long id,
        @Schema(description = "部门名称") String name,
        @Schema(description = "上级部门ID（根部门为 null）") Long parentId,
        @Schema(description = "同级排序号") Integer sortOrder,
        @Schema(description = "该部门下的角色列表") List<RoleTreeNodeResponse> roles,
        @Schema(description = "子部门节点") List<DepartmentRoleNodeResponse> children) {

    /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
    public DepartmentRoleNodeResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
        children = children == null ? List.of() : List.copyOf(children);
    }

}
