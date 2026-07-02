package com.interview.modules.auth.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.auth.model.User;
import com.interview.modules.auth.repository.UserRepository;
import com.interview.modules.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$encoded_password");
        user.setDisplayName("测试用户");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setEnabled(true);
    }

    // ==================== 注册 ====================

    @Test
    void shouldRegisterSuccessfully() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtTokenProvider.generateToken(eq(1L), eq("testuser"), eq("USER"))).thenReturn("test-jwt");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), eq("testuser"))).thenReturn("test-refresh");

        Map<String, Object> result = authService.register("testuser", "123456", "测试用户", "test@example.com");

        assertNotNull(result);
        assertEquals("test-jwt", result.get("token"));
        assertEquals("test-refresh", result.get("refreshToken"));
        assertEquals("testuser", result.get("username"));
        assertEquals("USER", result.get("role"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.register("testuser", "123456", "测试用户", "test@example.com"));

        assertEquals(ErrorCode.USERNAME_EXISTS, e.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldUseUsernameAsDefaultDisplayName() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtTokenProvider.generateToken(anyLong(), anyString(), anyString())).thenReturn("token");
        when(jwtTokenProvider.generateRefreshToken(anyLong(), anyString())).thenReturn("refresh");

        authService.register("testuser", "123456", null, null);

        verify(userRepository).save(argThat(u -> "testuser".equals(u.getDisplayName())));
    }

    // ==================== 登录 ====================

    @Test
    void shouldLoginSuccessfully() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "$2a$encoded_password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, "testuser", "USER")).thenReturn("test-jwt");
        when(jwtTokenProvider.generateRefreshToken(1L, "testuser")).thenReturn("test-refresh");

        Map<String, Object> result = authService.login("testuser", "123456");

        assertEquals("test-jwt", result.get("token"));
        assertEquals("testuser", result.get("username"));
    }

    @Test
    void shouldRejectLoginWithWrongUsername() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.login("unknown", "123456"));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$encoded_password")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.login("testuser", "wrong"));
    }

    @Test
    void shouldRejectDisabledAccount() {
        user.setEnabled(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "$2a$encoded_password")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login("testuser", "123456"));

        assertEquals(ErrorCode.ACCOUNT_DISABLED, e.getErrorCode());
    }

    // ==================== 获取当前用户 ====================

    @Test
    void shouldGetCurrentUser() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        Map<String, Object> result = authService.getCurrentUser("testuser");

        assertEquals(1L, result.get("userId"));
        assertEquals("testuser", result.get("username"));
        assertEquals("test@example.com", result.get("email"));
    }

    @Test
    void shouldThrowWhenUserNotFoundForGetCurrentUser() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.getCurrentUser("unknown"));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    // ==================== 修改密码 ====================

    @Test
    void shouldChangePasswordSuccessfully() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "$2a$encoded_password")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("$2a$new_encoded");

        assertDoesNotThrow(() -> authService.changePassword("testuser", "oldPass", "newPass"));

        verify(userRepository).save(argThat(u -> "$2a$new_encoded".equals(u.getPassword())));
    }

    @Test
    void shouldRejectChangePasswordWithWrongOldPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongOld", "$2a$encoded_password")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.changePassword("testuser", "wrongOld", "newPass"));

        assertEquals(ErrorCode.PASSWORD_WRONG, e.getErrorCode());
    }

    @Test
    void shouldRejectPasswordSameAsOld() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("samePass", "$2a$encoded_password")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.changePassword("testuser", "samePass", "samePass"));

        assertEquals(ErrorCode.PASSWORD_SAME_AS_OLD, e.getErrorCode());
    }

    // ==================== 更新用户信息 ====================

    @Test
    void shouldUpdateProfile() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        Map<String, Object> result = authService.updateProfile("testuser", "新昵称", "new@example.com");

        assertEquals("新昵称", result.get("displayName"));
        assertEquals("new@example.com", result.get("email"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldNotUpdateNullFields() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        Map<String, Object> result = authService.updateProfile("testuser", null, null);

        assertEquals("测试用户", result.get("displayName"));
        assertEquals("test@example.com", result.get("email"));
    }

    // ==================== 刷新Token ====================

    @Test
    void shouldRefreshToken() {
        when(jwtTokenProvider.refreshAccessToken("old-refresh")).thenReturn("new-token");
        when(jwtTokenProvider.getUserIdFromToken("new-token")).thenReturn(1L);
        when(jwtTokenProvider.getUsernameFromToken("new-token")).thenReturn("testuser");
        when(jwtTokenProvider.generateRefreshToken(1L, "testuser")).thenReturn("new-refresh");

        Map<String, Object> result = authService.refreshToken("old-refresh");

        assertEquals("new-token", result.get("token"));
        assertEquals("new-refresh", result.get("refreshToken"));
    }

    @Test
    void shouldRejectInvalidRefreshToken() {
        when(jwtTokenProvider.refreshAccessToken("invalid")).thenThrow(new RuntimeException("invalid"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.refreshToken("invalid"));

        assertEquals(ErrorCode.TOKEN_REFRESH_FAILED, e.getErrorCode());
    }
}