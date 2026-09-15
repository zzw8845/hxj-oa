package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 部门管理视图集合（容器类，仅作嵌套 record 命名空间）。 */
public final class DepartmentViews {
    private DepartmentViews() {
    }

    /** 部门树节点（任意层级，children 递归）。 */
    public record Department(
            @Schema(description = "部门ID") Long id,
            @Schema(description = "部门名称") String name,
            @Schema(description = "上级部门ID，根部门为空") Long parentId,
            @Schema(description = "同级排序号") Integer sortOrder,
            @Schema(description = "部门负责人用户ID，空表示未设置") Long leaderUserId,
            @Schema(description = "部门负责人姓名，未设置或用户不存在时为空") String leaderName,
            @Schema(description = "子部门列表") List<Department> children) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public Department {
            children = children == null ? List.of() : List.copyOf(children);
        }

        /** 兼容构造器：不含负责人信息的调用方（老视图组装点）。 */
        public Department(Long id, String name, Long parentId, Integer sortOrder, List<Department> children) {
            this(id, name, parentId, sortOrder, null, null, children);
        }
    }
}
