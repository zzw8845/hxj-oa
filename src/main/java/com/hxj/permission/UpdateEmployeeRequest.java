package com.hxj.permission;

import com.hxj.enums.UserStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateEmployeeRequest(
        @Schema(description = "姓名") @NotBlank String name,
        @Schema(description = "工号") @NotBlank String jobNo,
        @Schema(description = "部门ID（sys_department 字典）") @NotNull Long departmentId,
        @Schema(description = "岗位ID（sys_post 字典）") @NotNull Long postId,
        @Schema(description = "直属主管登录账号（汇报线，留空表示未设置）") String managerAccount,
        @Schema(description = "用户状态（枚举）") @NotNull UserStatusEnum status,
        @Schema(description = "新密码；为空表示不修改密码") String newPassword,
        @Schema(description = "分配的角色名称列表，与 EmployeeResponse.roles 同源可原样回写")
        @NotEmpty List<String> roles,
        @Schema(description = "兼职部门ID列表（可空；主部门用 departmentId）") List<Long> extraDepartmentIds) {

    /**
     * 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。
     *
     * <p>请求 DTO 来自外部输入，拷贝可确保后续业务流转期间入参不被改动。
     */
    public UpdateEmployeeRequest {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
    /** 兼容构造：未传兼职部门时视为无兼职。 */
    public UpdateEmployeeRequest(
            String name, String jobNo, Long departmentId, Long postId,
            String managerAccount, UserStatusEnum status, String newPassword, List<String> roles) {
        this(name, jobNo, departmentId, postId, managerAccount, status, newPassword, roles, null);
    }
}
