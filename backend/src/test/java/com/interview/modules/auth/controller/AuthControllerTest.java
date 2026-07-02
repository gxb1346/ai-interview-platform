package com.interview.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== 注册 ====================

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        when(authService.register(anyString(), anyString(), any(), any()))
                .thenReturn(Map.of("token", "test-jwt-token", "userId", 1L));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "testuser",
                                "password": "123456",
                                "displayName": "测试用户",
                                "email": "test@example.com"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void shouldRejectRegisterWithShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "testuser",
                                "password": "123"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("密码长度不能少于6位"));
    }

    @Test
    void shouldRejectRegisterWithEmptyFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "",
                                "password": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("用户名和密码不能为空"));
    }

    // ==================== 登录 ====================

    @Test
    void shouldLoginSuccessfully() throws Exception {
        when(authService.login("testuser", "123456"))
                .thenReturn(Map.of("token", "test-jwt-token", "refreshToken", "test-refresh"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "testuser",
                                "password": "123456"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.refreshToken").value("test-refresh"));
    }

    @Test
    void shouldRejectLoginWithEmptyFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "",
                                "password": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("用户名和密码不能为空"));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        when(authService.login("testuser", "wrong"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "username": "testuser",
                                "password": "wrong"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("用户名或密码错误"));
    }

    // ==================== 获取当前用户 ====================

    @Test
    void shouldReturn401WhenNotAuthenticatedForMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 修改密码 ====================

    @Test
    void shouldReturn401WhenNotAuthenticatedForPassword() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "oldPassword": "oldPass",
                                "newPassword": "newPass123"
                            }
                            """))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 刷新Token ====================

    @Test
    void shouldRefreshToken() throws Exception {
        when(authService.refreshToken("test-refresh"))
                .thenReturn(Map.of("token", "new-jwt-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "refreshToken": "test-refresh"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt-token"));
    }

    @Test
    void shouldRejectEmptyRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("refreshToken 不能为空"));
    }
}