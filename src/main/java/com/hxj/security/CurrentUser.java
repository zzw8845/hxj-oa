package com.hxj.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户获取工具类。
 * 从 Spring Security 的 SecurityContext 中直接获取 AuthenticatedUserResponse，
 * 避免在 Controller 方法参数中显式声明 @AuthenticationPrincipal。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 获取当前登录用户。
     *
     * @return 当前登录用户，如果未登录或未认证则返回 null
     */
    public static AuthenticatedUserResponse get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserResponse user) {
            return user;
        }
        return null;
    }

    /**
     * 获取当前登录用户，如果未登录则抛出异常。
     *
     * @return 当前登录用户
     * @throws IllegalStateException 如果未登录或未认证
     */
    public static AuthenticatedUserResponse require() {
        AuthenticatedUserResponse user = get();
        if (user == null) {
            throw new IllegalStateException("当前用户未登录或未认证");
        }
        return user;
    }

    /**
     * 获取当前用户的 ID。
     *
     * @return 用户 ID，如果未登录则返回 null
     */
    public static Long getUserId() {
        AuthenticatedUserResponse user = get();
        return user != null ? user.userId() : null;
    }

    /**
     * 获取当前用户的账号。
     *
     * @return 用户账号，如果未登录则返回 null
     */
    public static String getAccount() {
        AuthenticatedUserResponse user = get();
        return user != null ? user.account() : null;
    }
}
