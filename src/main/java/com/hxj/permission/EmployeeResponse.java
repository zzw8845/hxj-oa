package com.hxj.permission;

import com.hxj.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record EmployeeResponse(
        @Schema(description = "用户ID") Long id,
        @Schema(description = "姓名") String name,
        @Schema(description = "工号") String jobNo,
        @Schema(description = "登录账号") String account,
        @Schema(description = "所属部门") String department,
        @Schema(description = "岗位") String post,
        @Schema(description = "用户状态（枚举：ACTIVE=在职 / RESIGNED=离职）") UserStatus status,
        @Schema(description = "角色名称列表") List<String> roles) {

    /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
    public EmployeeResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}