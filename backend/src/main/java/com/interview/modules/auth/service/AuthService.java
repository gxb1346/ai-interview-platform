package com.interview.modules.auth.service;

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
            throw new RuntimeException("用户名已存在");
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

        return Map.of(
                "token", token,
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
            throw new RuntimeException("账户已被禁用");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());

        return Map.of(
                "token", token,
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
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        return Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "email", user.getEmail(),
                "role", user.getRole()
        );
    }
}
