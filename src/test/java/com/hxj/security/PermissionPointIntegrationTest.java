package com.hxj.security;

import com.hxj.auth.AuthController;
import com.hxj.auth.AuthService;
import com.hxj.config.MethodSecurityConfig;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, properties = {
        "app.jwt.secret=test-auth-jwt-secret-key-with-at-least-thirty-two-bytes",
        "app.jwt.expiration-ms=28800000"
})
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class,
        JwtAuthenticationFilter.class,
        JsonAuthenticationEntryPoint.class,
        SecurityResponseWriter.class,
        JsonAccessDeniedHandler.class,
        JwtService.class,
        AuthService.class,
        TokenBlacklistService.class,
        SecurityBeansConfig.class,
        TimeConfig.class,
        GlobalExceptionHandler.class,
        PermissionPointIntegrationTest.PermissionProbeController.class
})
class PermissionPointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private SysUserRepository userRepository;

    @Test
    void shouldAllowMethodWhenJwtContainsRequiredPermission() throws Exception {
        String token = tokenWithPermission("VIEW_ALL_FORMS");

        mockMvc.perform(get("/api/test/permission-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn403WhenJwtLacksRequiredPermission() throws Exception {
        String token = tokenWithPermission("VIEW_OWN_FORMS");

        // 阿里风格：HTTP 统一 200，前端靠 code 分流
        mockMvc.perform(get("/api/test/permission-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.success").value(false));
    }

    private String tokenWithPermission(String permissionCode) {
        SysDataScope scope = new SysDataScope("OWN_DOCUMENTS", "本人单据");
        SysPermission permission = new SysPermission(permissionCode, permissionCode);
        SysRole role = new SysRole();
        role.setName("测试角色");
        role.setDataScope(scope);
        role.addPermission(permission);

        SysUser user = new SysUser();
        user.setAccount("test-user");
        user.setName("测试用户");
        user.setDepartment("测试部门");
        user.setPost("测试岗位");
        user.addRole(role);
        return jwtService.issueAccessToken(user);
    }

    @RestController
    static class PermissionProbeController {

        @GetMapping("/api/test/permission-check")
        @PreAuthorize("hasAuthority('VIEW_ALL_FORMS')")
        String permissionCheck() {
            return "success";
        }
    }
}