package com.hxj.security;

import com.hxj.auth.AuthController;
import com.hxj.auth.AuthService;
import com.hxj.config.SecurityBeansConfig;
import com.hxj.config.SecurityConfig;
import com.hxj.config.TimeConfig;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.exception.GlobalExceptionHandler;
import com.hxj.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, properties = {
        "app.jwt.secret=test-auth-jwt-secret-key-with-at-least-thirty-two-bytes",
        "app.jwt.expiration-ms=28800000"
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JsonAuthenticationEntryPoint.class,
        SecurityResponseWriter.class,
        JsonAccessDeniedHandler.class,
        JwtService.class,
        AuthService.class,
        TokenBlacklistService.class,
        SecurityBeansConfig.class,
        TimeConfig.class,
        GlobalExceptionHandler.class
})
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private SysUserRepository userRepository;

    @Test
    void shouldReturn401WhenProtectedEndpointHasNoToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请先登录或重新登录"));
    }

    @Test
    void shouldReturn401WhenBearerTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请先登录或重新登录"));
    }

    @Test
    void shouldAuthenticateValidBearerTokenAndExposeCurrentIdentity() throws Exception {
        SysDataScope scope = new SysDataScope("ALL_DEPARTMENTS_ALL_NODES", "全部部门与全部节点");
        SysPermission permission = new SysPermission("VIEW_ALL_FORMS", "查看全部表单");
        SysRole role = new SysRole();
        role.setName("超级管理员");
        role.setDataScope(scope);
        role.addPermission(permission);

        SysUser user = new SysUser();
        user.setAccount("linanran");
        user.setName("林安然");
        user.setDepartment("总经办");
        user.setPost("系统管理员");
        user.addRole(role);

        String token = jwtService.issueAccessToken(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.account").value("linanran"))
                .andExpect(jsonPath("$.data.name").value("林安然"))
                .andExpect(jsonPath("$.data.roles[0]").value("超级管理员"))
                .andExpect(jsonPath("$.data.permissions[0]").value("VIEW_ALL_FORMS"));
    }
}