package com.interview.modules.auth.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.auth.model.User;
import com.interview.modules.auth.repository.UserRepository;
import com.interview.modules.auth.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 认证服务
 * 处理用户注册与登录
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 用户注册
     */
    public Map<String, Object> register(String username, String password, String displayName, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(displayName != null ? displayName : username);
        user.setEmail(email);
        user.setRole("USER");
        user.setEnabled(true);

        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole()
        );
    }

    /**
     * 用户登录
     */
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole()
        );
    }

    /**
     * 获取当前用户信息
     */
    public Map<String, Object> getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail(),
                "role", user.getRole()
        );
    }

    /**
     * 修改密码
     */
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }

        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 更新用户信息
     */
    public Map<String, Object> updateProfile(String username, String displayName, String email) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        if (email != null && !email.isBlank()) {
            user.setEmail(email);
        }
        userRepository.save(user);

        return Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail(),
                "role", user.getRole()
        );
    }

    /**
     * 刷新访问令牌
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            String newToken = jwtTokenProvider.refreshAccessToken(refreshToken);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(
                    jwtTokenProvider.getUserIdFromToken(newToken),
                    jwtTokenProvider.getUsernameFromToken(newToken)
            );
            return Map.of(
                    "token", newToken,
                    "refreshToken", newRefreshToken
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_REFRESH_FAILED, "刷新令牌无效或已过期");
        }
    }
}