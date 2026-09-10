package com.hxj.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 登录响应 DTO。
 *
 * <p>登录成功时返回 Access Token、Refresh Token 及用户身份信息。
 * 客户端需保存 Token，后续请求在 Header 中携带 {@code Authorization: Bearer {accessToken}}。
 */
public record LoginResponse(
        @Schema(description = "JWT Access Token（有效期 4 小时）")
        String accessToken,
        @Schema(description = "JWT Refresh Token（有效期 7 天，用于刷新 Access Token）")
        String refreshToken,
        @Schema(description = "Token 类型（固定为 \"Bearer\"）")
        String tokenType,
        @Schema(description = "Access Token 有效期（秒）")
        long expiresInSeconds,
        @Schema(description = "用户身份信息")
        UserIdentity user) {

    /** 用户身份信息 */
    public record UserIdentity(
            @Schema(description = "用户 ID")
            Long id,
            @Schema(description = "工号")
            String jobNo,
            @Schema(description = "登录账号")
            String account,
            @Schema(description = "姓名")
            String name,
            @Schema(description = "部门")
            String department,
            @Schema(description = "岗位")
            String post,
            @Schema(description = "角色编码列表")
            List<String> roles,
            @Schema(description = "权限编码列表")
            List<String> permissions,
            @Schema(description = "数据范围类型列表（ALL/OWN/DEPT/DEPT_AND_CHILD/CUSTOM）")
            List<String> dataScopes) {

        /** 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。 */
        public UserIdentity {
            roles = roles == null ? List.of() : List.copyOf(roles);
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
            dataScopes = dataScopes == null ? List.of() : List.copyOf(dataScopes);
        }
    }
}
