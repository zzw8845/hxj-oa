package com.hxj.security;

import java.util.List;

/** 从已验证 JWT 中恢复出的当前用户身份快照。 */
public record AuthenticatedUserResponse(
        Long userId,
        String account,
        String name,
        String department,
        String post,
        List<String> roles,
        List<String> permissions,
        List<String> dataScopes) {

    /**
     * 紧凑构造器：集合组件防御性拷贝为不可变列表。
     *
     * <p>本对象作为 SecurityContext 的 Principal 在请求线程内共享，
     * 若持有可变列表，任何拿到引用的代码都能增删角色/权限，等同于越权提权。
     */
    public AuthenticatedUserResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        dataScopes = dataScopes == null ? List.of() : List.copyOf(dataScopes);
    }
}