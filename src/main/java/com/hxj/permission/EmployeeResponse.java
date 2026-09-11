package com.hxj.permission;

import com.hxj.enums.UserStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record EmployeeResponse(
        @Schema(description = "用户ID") Long id,
        @Schema(description = "姓名") String name,
        @Schema(description = "工号") String jobNo,
        @Schema(description = "登录账号") String account,
        @Schema(description = "部门ID") Long departmentId,
        @Schema(description = "所属部门名称") String department,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "岗位名称") String post,
        @Schema(description = "直属主管ID，未设置为空") Long managerId,
        @Schema(description = "直属主管登录账号") String managerAccount,
        @Schema(description = "直属主管姓名") String managerName,
        @Schema(description = "用户状态（枚举）") UserStatusEnum status,
        @Schema(description = "角色名称列表") List<String> roles,
        @Schema(description = "兼职部门ID列表") List<Long> extraDepartmentIds,
        @Schema(description = "兼职部门名称列表") List<String> extraDepartments) {

    /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
    public EmployeeResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
        extraDepartmentIds = extraDepartmentIds == null ? List.of() : List.copyOf(extraDepartmentIds);
        extraDepartments = extraDepartments == null ? List.of() : List.copyOf(extraDepartments);
    }
}
