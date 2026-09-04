package com.hxj.auth;

import com.hxj.common.ErrorCode;
import com.hxj.entity.SysUser;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.JwtService;
import com.hxj.security.TokenBlacklistService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账号密码登录用例。 */
@Service
public class AuthService {

    /** 对不存在账号执行等价 BCrypt 工作量，减少账号枚举的时序差异。 */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    public AuthService(
            SysUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenBlacklistService blacklistService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByAccount(request.account()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }
        if (!user.isLoginEnabled()) {
            throw new AuthException(ErrorCode.ACCOUNT_DISABLED, "账号已停用");
        }

        String token = jwtService.issueAccessToken(user);
        String refreshToken = jwtService.issueRefreshToken(user);
        LoginResponse.UserIdentity identity = new LoginResponse.UserIdentity(
                user.getId(),
                user.getJobNo(),
                user.getAccount(),
                user.getName(),
                user.getDepartment(),
                user.getPost(),
                jwtService.roleNames(user),
                jwtService.permissionCodes(user),
                jwtService.dataScopeCodes(user));
        return new LoginResponse(token, refreshToken, "Bearer", jwtService.getExpirationSeconds(), identity);
    }

    /** 使用 Refresh Token 换取新的 Access Token（同时轮换 Refresh Token）。 */
    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        String account;
        try {
            account = jwtService.parseRefreshToken(refreshToken);
        } catch (Exception ex) {
            throw new AuthException(ErrorCode.AUTH_FAILED, "Refresh Token 无效或已过期");
        }
        SysUser user = userRepository.findByAccount(account)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_FAILED, "账号或密码错误"));
        if (!user.isLoginEnabled()) {
            throw new AuthException(ErrorCode.ACCOUNT_DISABLED, "账号已停用");
        }
        String token = jwtService.issueAccessToken(user);
        String newRefreshToken = jwtService.issueRefreshToken(user);
        LoginResponse.UserIdentity identity = new LoginResponse.UserIdentity(
                user.getId(),
                user.getJobNo(),
                user.getAccount(),
                user.getName(),
                user.getDepartment(),
                user.getPost(),
                jwtService.roleNames(user),
                jwtService.permissionCodes(user),
                jwtService.dataScopeCodes(user));
        return new LoginResponse(token, newRefreshToken, "Bearer", jwtService.getExpirationSeconds(), identity);
    }

    /** 退出登录：将当前 Access Token 加入黑名单。 */
    public void logout(String accessToken) {
        blacklistService.blacklist(accessToken);
    }

    private AuthException invalidCredentials() {
        return new AuthException(ErrorCode.AUTH_FAILED, "账号或密码错误");
    }
}