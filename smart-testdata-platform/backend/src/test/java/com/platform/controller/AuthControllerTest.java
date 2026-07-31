package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.LoginRequest;
import com.platform.dto.LoginResponse;
import com.platform.dto.RegisterRequest;
import com.platform.config.JwtAuthenticationFilter;
import com.platform.exception.BusinessException;
import com.platform.exception.GlobalExceptionHandler;
import com.platform.service.AuthService;
import com.platform.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 单元测试 — Spring MockMvc
 *
 * 测试覆盖: 登录成功 / 登录失败 / 注册重复用户
 */
@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                DataSourceAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                SqlInitializationAutoConfiguration.class,
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("AuthController — 认证接口测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private LoginRequest validLoginRequest;
    private RegisterRequest validRegisterRequest;

    @BeforeEach
    void setUp() {
        validLoginRequest = new LoginRequest();
        validLoginRequest.setUsername("testuser");
        validLoginRequest.setPassword("password123");

        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setUsername("newuser");
        validRegisterRequest.setPassword("password123");
        validRegisterRequest.setNickname("新用户");
        validRegisterRequest.setEmail("newuser@test.com");
    }

    // ============================================================
    // 登录测试
    // ============================================================

    @Nested
    @DisplayName("POST /api/auth/login — 用户登录")
    class LoginTests {

        @Test
        @DisplayName("登录成功: 返回 token + 用户信息")
        void loginSuccess() throws Exception {
            LoginResponse mockResponse = new LoginResponse(
                    "eyJhbGciOiJIUzI1NiJ9.mock_token", "testuser", "测试用户");

            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("success"))
                    .andExpect(jsonPath("$.data.token").value("eyJhbGciOiJIUzI1NiJ9.mock_token"))
                    .andExpect(jsonPath("$.data.username").value("testuser"))
                    .andExpect(jsonPath("$.data.nickname").value("测试用户"));
        }

        @Test
        @DisplayName("登录失败: 用户名或密码错误")
        void loginFailure_wrongCredentials() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException("用户名或密码错误"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }

        @Test
        @DisplayName("登录失败: 账号被禁用")
        void loginFailure_accountDisabled() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException("账号已被禁用"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("账号已被禁用"));
        }

        @Test
        @DisplayName("登录失败: 参数校验 — 用户名为空")
        void loginFailure_emptyUsername() throws Exception {
            LoginRequest emptyUsername = new LoginRequest();
            emptyUsername.setUsername("");
            emptyUsername.setPassword("password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyUsername)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("用户名不能为空"));
        }

        @Test
        @DisplayName("登录失败: 参数校验 — 密码为空")
        void loginFailure_emptyPassword() throws Exception {
            LoginRequest emptyPassword = new LoginRequest();
            emptyPassword.setUsername("testuser");
            emptyPassword.setPassword("");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyPassword)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("密码不能为空"));
        }
    }

    // ============================================================
    // 注册测试
    // ============================================================

    @Nested
    @DisplayName("POST /api/auth/register — 用户注册")
    class RegisterTests {

        @Test
        @DisplayName("注册重复用户: 用户名已存在")
        void registerDuplicateUser() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new BusinessException("用户名已存在"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegisterRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("用户名已存在"));
        }

        @Test
        @DisplayName("注册失败: 参数校验 — 用户名过短")
        void registerFailure_shortUsername() throws Exception {
            RegisterRequest shortUsername = new RegisterRequest();
            shortUsername.setUsername("ab");  // min=3
            shortUsername.setPassword("password123");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(shortUsername)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("用户名长度为 3-64 个字符"));
        }

        @Test
        @DisplayName("注册失败: 参数校验 — 密码过短")
        void registerFailure_shortPassword() throws Exception {
            RegisterRequest shortPassword = new RegisterRequest();
            shortPassword.setUsername("validuser");
            shortPassword.setPassword("12345");  // min=6

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(shortPassword)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("密码长度为 6-128 个字符"));
        }
    }
}
