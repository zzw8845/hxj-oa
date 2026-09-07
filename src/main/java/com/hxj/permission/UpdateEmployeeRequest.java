package com.hxj.permission;

import com.hxj.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateEmployeeRequest(
        @Schema(description = "姓名") @NotBlank String name,
        @Schema(description = "工号") @NotBlank String jobNo,
        @Schema(description = "所属部门") @NotBlank String department,
        @Schema(description = "岗位") @NotBlank String post,
        @Schema(description = "用户状态（枚举）") @NotNull UserStatus status,
        @Schema(description = "新密码；为空表示不修改密码") String newPassword,
        @Schema(description = "授予的角色ID列表") @NotEmpty List<Long> roleIds) {

    /**
     * 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。
     *
     * <p>请求 DTO 来自外部输入，拷贝可确保后续业务流转期间入参不被改动。
     */
    public UpdateEmployeeRequest {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }
}