package com.hxj.auth;

import com.hxj.common.ApiResponse;
import com.hxj.security.AuthenticatedUserResponse;
import com.hxj.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口：登录与当前用户信息。 */
@Tag(name = "认证", description = "登录、获取当前登录用户信息")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "登录", description = "使用账号+密码登录，成功后返回 JWT 令牌与用户信息")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "当前用户", description = "获取当前登录用户信息（需携带 Bearer Token）")
    @GetMapping("/me")
    public ApiResponse<AuthenticatedUserResponse> currentUser() {
        return ApiResponse.success(CurrentUser.get());
    }

    @Operation(summary = "刷新 Token", description = "使用 Refresh Token 换取新的 Access Token（同时轮换 Refresh Token）")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "退出登录", description = "将当前 Access Token 加入黑名单，使其立即失效")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        authService.logout(token);
        return ApiResponse.success();
    }
}
