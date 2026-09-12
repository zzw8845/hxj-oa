package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateEmployeeRequest(
        @Schema(description = "姓名") @NotBlank String name,
        @Schema(description = "工号") @NotBlank String jobNo,
        @Schema(description = "登录账号") @NotBlank String account,
        @Schema(description = "登录密码（明文传输，建议配合 HTTPS）") @NotBlank String password,
        @Schema(description = "部门ID（sys_department 字典）") @NotNull Long departmentId,
        @Schema(description = "岗位ID（sys_post 字典）") @NotNull Long postId,
        @Schema(description = "直属主管登录账号（汇报线，可空）") String managerAccount,
        @Schema(description = "分配的角色名称列表，与 EmployeeResponse.roles 同源可原样回写")
        @NotEmpty List<String> roles) {

    /**
     * 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。
     *
     * <p>请求 DTO 来自外部输入，拷贝可确保后续业务流转期间入参不被改动。
     */
    public CreateEmployeeRequest {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

}
