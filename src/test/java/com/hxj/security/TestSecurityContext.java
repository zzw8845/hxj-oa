package com.hxj.security;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 测试工具：在单元测试中设置当前登录用户到 Spring Security Context。
 * 使用完后请务必调用 {@link #clear()} 清理，避免测试相互影响。
 */
public final class TestSecurityContext {

    private TestSecurityContext() {
    }

    /**
     * 将指定用户模拟到当前线程的 SecurityContext 中。
     *
     * @param user 模拟的已认证用户
     */
    public static void mock(AuthenticatedUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        // 使用 TestingAuthenticationToken 作为 Authentication，principal 即为 AuthenticatedUser
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(user, null, user.permissions().toArray(new String[0]));
        authentication.setAuthenticated(true);
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    /**
     * 清除当前线程的 SecurityContext，避免测试相互影响。
     */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
