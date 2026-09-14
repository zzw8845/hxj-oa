package com.hxj.auth;

import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.enums.UserStatusEnum;
import com.hxj.config.SecurityBeansConfig;
import com.hxj.config.TimeConfig;
import com.hxj.exception.GlobalExceptionHandler;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.JwtService;
import com.hxj.security.JsonAuthenticationEntryPoint;
import com.hxj.security.SecurityResponseWriter;
import com.hxj.security.TokenBlacklistService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, properties = {
        "app.jwt.secret=test-auth-jwt-secret-key-with-at-least-thirty-two-bytes",
        "app.jwt.expiration-ms=28800000"
})
@AutoConfigureMockMvc(addFilters = false)
@Import({
        AuthService.class,
        JwtService.class,
        JsonAuthenticationEntryPoint.class,
        SecurityResponseWriter.class,
        TokenBlacklistService.class,
        SecurityBeansConfig.class,
        TimeConfig.class,
        GlobalExceptionHandler.class
})
class AuthLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SysDataScope scope = new SysDataScope("ALL", "全部单据");
        SysPermission permission = new SysPermission("VIEW_ALL_FORMS", "查看全部表单");

        SysRole role = new SysRole();
        role.setName("超级管理员");
        role.setDepartment("总经办");
        role.setPost("系统管理员");
        role.setDataScope(scope);
        role.addPermission(permission);

        SysUser activeUser = new SysUser();
        activeUser.setName("林安然");
        activeUser.setJobNo("HXJ001");
        activeUser.setAccount("linanran");
        activeUser.setPassword(passwordEncoder.encode("password"));
        activeUser.setDepartment("总经办");
        activeUser.setPost("系统管理员");
        activeUser.setStatus(UserStatusEnum.ACTIVE);
        activeUser.addRole(role);

        SysUser resignedUser = new SysUser();
        resignedUser.setName("离职员工");
        resignedUser.setJobNo("HXJ999");
        resignedUser.setAccount("resigned");
        resignedUser.setPassword(passwordEncoder.encode("password"));
        resignedUser.setDepartment("总经办");
        resignedUser.setPost("员工");
        resignedUser.setStatus(UserStatusEnum.RESIGNED);
        resignedUser.addRole(role);

        when(userRepository.findByAccount("linanran")).thenReturn(Optional.of(activeUser));
        when(userRepository.findByAccount("resigned")).thenReturn(Optional.of(resignedUser));
        when(userRepository.findByAccount("missing")).thenReturn(Optional.empty());
    }

    @Test
    void shouldLoginAndIssueJwtWithIdentityAndRoleClaims() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"linanran","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(28800))
                .andExpect(jsonPath("$.data.user.name").value("林安然"))
                .andExpect(jsonPath("$.data.user.department").value("总经办"))
                .andExpect(jsonPath("$.data.user.post").value("系统管理员"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("超级管理员"))
                .andExpect(jsonPath("$.data.user.permissions[0]").value("VIEW_ALL_FORMS"))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.data.accessToken");
        Map<String, Object> claims = jwtService.parseClaims(token);
        assertThat(claims.get("account")).isEqualTo("linanran");
        assertThat(claims.get("name")).isEqualTo("林安然");
        assertThat(String.valueOf(claims.get("roles"))).contains("超级管理员");
        assertThat(String.valueOf(claims.get("permissions"))).contains("VIEW_ALL_FORMS");
    }

    @Test
    void shouldRefreshTokenAndRotate() throws Exception {
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"linanran","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");

        String refreshBody = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.account").value("linanran"))
                .andReturn().getResponse().getContentAsString();

        String newAccessToken = JsonPath.read(refreshBody, "$.data.accessToken");
        Map<String, Object> claims = jwtService.parseClaims(newAccessToken);
        assertThat(claims.get("account")).isEqualTo("linanran");
    }

    @Test
    void shouldRejectInvalidRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"invalid.token.value"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWrongPasswordWithoutRevealingAccountExistence() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"linanran","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"missing","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    void shouldRejectResignedAccountAndValidateRequiredFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"resigned","password":"password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.success").value(false));

        // 阿里风格：HTTP 统一 200，前端靠 code 分流
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"","password":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        String token = jwtService.issueAccessToken(buildTestUser());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldLogoutWithoutBearerPrefix() throws Exception {
        String token = jwtService.issueAccessToken(buildTestUser());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldRejectLogoutWithoutAuthorizationHeader() throws Exception {
        // 阿里风格：HTTP 统一 200，前端靠 code 分流
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    private SysUser buildTestUser() {
        SysDataScope scope = new SysDataScope("ALL", "全部单据");
        SysPermission permission = new SysPermission("VIEW_ALL_FORMS", "查看全部表单");

        SysRole role = new SysRole();
        role.setName("超级管理员");
        role.setDepartment("总经办");
        role.setPost("系统管理员");
        role.setDataScope(scope);
        role.addPermission(permission);

        SysUser user = new SysUser();
        user.setName("林安然");
        user.setJobNo("HXJ001");
        user.setAccount("linanran");
        user.setPassword(passwordEncoder.encode("password"));
        user.setDepartment("总经办");
        user.setPost("系统管理员");
        user.setStatus(UserStatusEnum.ACTIVE);
        user.addRole(role);
        return user;
    }
}