package com.reader.auth;

import com.reader.common.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务：注册、登录、刷新令牌、登出。
 * 控制器不写业务逻辑，全部由本服务承载。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 注册：校验用户名/邮箱唯一性，BCrypt 哈希密码后入库，签发令牌。
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(400, "用户名已存在");
        }
        if (request.getEmail() != null && userMapper.findByEmail(request.getEmail()) != null) {
            throw new BusinessException(400, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userMapper.insert(user);

        return buildAuthResponse(user);
    }

    /**
     * 登录：校验用户名与密码，签发令牌。
     */
    public AuthResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return buildAuthResponse(user);
    }

    /**
     * 刷新令牌：校验刷新令牌有效性，签发新的访问令牌与刷新令牌（轮换）。
     */
    public AuthResponse refresh(RefreshRequest request) {
        Long userId;
        try {
            userId = jwtTokenProvider.validateAndGetUserId(request.getRefreshToken());
        } catch (Exception e) {
            throw new BusinessException(401, "刷新令牌无效或已过期");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        return buildAuthResponse(user);
    }

    /**
     * 登出：无状态 JWT 下，客户端丢弃令牌即可；服务端无需额外操作。
     * 本方法仅作为接口语义占位，便于后续扩展服务端令牌黑名单。
     */
    public void logout() {
        // 无状态 JWT：登出由客户端清除令牌完成
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername());
    }
}
