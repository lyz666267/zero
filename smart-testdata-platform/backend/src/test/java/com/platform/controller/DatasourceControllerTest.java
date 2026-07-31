package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.DatasourceRequest;
import com.platform.dto.DatasourceResponse;
import com.platform.exception.BusinessException;
import com.platform.exception.GlobalExceptionHandler;
import com.platform.service.DatasourceService;
import com.platform.config.JwtAuthenticationFilter;
import com.platform.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DatasourceController 单元测试 — Spring MockMvc
 *
 * 测试覆盖: CRUD / 未授权访问 / 用户隔离
 */
@WebMvcTest(
        value = DatasourceController.class,
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
@DisplayName("DatasourceController — 数据源管理接口测试")
class DatasourceControllerTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DatasourceService datasourceService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private DatasourceRequest validRequest;
    private DatasourceResponse mockResponse;

    @BeforeEach
    void setUp() {
        // 模拟已认证用户 (principal = userId)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CURRENT_USER_ID, null, Collections.emptyList())
        );

        validRequest = new DatasourceRequest();
        validRequest.setProjectId(1L);
        validRequest.setName("测试数据源");
        validRequest.setDbType("MySQL");
        validRequest.setHost("127.0.0.1");
        validRequest.setPort(3306);
        validRequest.setUsername("root");
        validRequest.setPassword("secret");
        validRequest.setDatabaseName("test_db");

        mockResponse = DatasourceResponse.builder()
                .id(10L)
                .projectId(1L)
                .name("测试数据源")
                .dbType("MySQL")
                .host("127.0.0.1")
                .port(3306)
                .username("root")
                .dbName("test_db")
                .status("CONNECTED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // 创建数据源 (POST /api/datasource)
    // ============================================================

    @Nested
    @DisplayName("POST /api/datasource — 创建数据源")
    class CreateTests {

        @Test
        @DisplayName("创建成功: 返回数据源详情")
        void createSuccess() throws Exception {
            when(datasourceService.create(eq(CURRENT_USER_ID), any(DatasourceRequest.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/api/datasource")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.name").value("测试数据源"))
                    .andExpect(jsonPath("$.data.dbType").value("MySQL"))
                    .andExpect(jsonPath("$.data.status").value("CONNECTED"));
        }

        @Test
        @DisplayName("创建失败: 项目不存在/不属于当前用户")
        void createFailure_projectNotFound() throws Exception {
            when(datasourceService.create(eq(CURRENT_USER_ID), any(DatasourceRequest.class)))
                    .thenThrow(new BusinessException(404, "项目不存在"));

            mockMvc.perform(post("/api/datasource")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("项目不存在"));
        }

        @Test
        @DisplayName("创建失败: 参数校验 — 名称为空")
        void createFailure_emptyName() throws Exception {
            validRequest.setName("");  // @NotBlank

            mockMvc.perform(post("/api/datasource")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("数据源名称不能为空"));
        }
    }

    // ============================================================
    // 查询数据源列表 (GET /api/datasource?projectId=1)
    // ============================================================

    @Nested
    @DisplayName("GET /api/datasource — 查询数据源列表")
    class ListTests {

        @Test
        @DisplayName("查询成功: 返回数据源列表")
        void listSuccess() throws Exception {
            List<DatasourceResponse> mockList = List.of(mockResponse);
            when(datasourceService.listByProject(1L, CURRENT_USER_ID))
                    .thenReturn(mockList);

            mockMvc.perform(get("/api/datasource")
                            .param("projectId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(10))
                    .andExpect(jsonPath("$.data[0].name").value("测试数据源"));
        }

        @Test
        @DisplayName("查询成功: 空列表")
        void listEmpty() throws Exception {
            when(datasourceService.listByProject(1L, CURRENT_USER_ID))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/datasource")
                            .param("projectId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ============================================================
    // 查询数据源详情 (GET /api/datasource/{id})
    // ============================================================

    @Nested
    @DisplayName("GET /api/datasource/{id} — 查询数据源详情")
    class GetByIdTests {

        @Test
        @DisplayName("查询成功: 返回详情")
        void getByIdSuccess() throws Exception {
            when(datasourceService.getById(10L, CURRENT_USER_ID))
                    .thenReturn(mockResponse);

            mockMvc.perform(get("/api/datasource/{id}", 10L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(10));
        }

        @Test
        @DisplayName("查询失败: 数据源不存在")
        void getByIdNotFound() throws Exception {
            when(datasourceService.getById(999L, CURRENT_USER_ID))
                    .thenThrow(new BusinessException(404, "数据源不存在"));

            mockMvc.perform(get("/api/datasource/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("数据源不存在"));
        }
    }

    // ============================================================
    // 更新数据源 (PUT /api/datasource/{id})
    // ============================================================

    @Nested
    @DisplayName("PUT /api/datasource/{id} — 更新数据源")
    class UpdateTests {

        @Test
        @DisplayName("更新成功: 返回更新后的数据源")
        void updateSuccess() throws Exception {
            DatasourceResponse updated = DatasourceResponse.builder()
                    .id(10L).projectId(1L).name("已更新数据源")
                    .dbType("MySQL").host("192.168.1.1").port(3307)
                    .username("admin").dbName("prod_db").status("CONNECTED")
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();

            when(datasourceService.update(eq(10L), eq(CURRENT_USER_ID), any(DatasourceRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/datasource/{id}", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("已更新数据源"))
                    .andExpect(jsonPath("$.data.host").value("192.168.1.1"))
                    .andExpect(jsonPath("$.data.port").value(3307));
        }
    }

    // ============================================================
    // 删除数据源 (DELETE /api/datasource/{id})
    // ============================================================

    @Nested
    @DisplayName("DELETE /api/datasource/{id} — 删除数据源")
    class DeleteTests {

        @Test
        @DisplayName("删除成功")
        void deleteSuccess() throws Exception {
            mockMvc.perform(delete("/api/datasource/{id}", 10L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(datasourceService).delete(10L, CURRENT_USER_ID);
        }

        @Test
        @DisplayName("删除失败: 数据源不存在")
        void deleteNotFound() throws Exception {
            doThrow(new BusinessException(404, "数据源不存在"))
                    .when(datasourceService).delete(999L, CURRENT_USER_ID);

            mockMvc.perform(delete("/api/datasource/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("数据源不存在"));
        }
    }

    // ============================================================
    // 用户隔离验证（未授权访问模拟）
    // ============================================================

    @Nested
    @DisplayName("用户隔离 — 跨用户访问验证")
    class IsolationTests {

        @Test
        @DisplayName("用户隔离: 尝试访问其他用户的资源")
        void accessOtherUserResource() throws Exception {
            when(datasourceService.getById(10L, CURRENT_USER_ID))
                    .thenThrow(new BusinessException(404, "项目不存在"));

            // 当前登录用户 1 尝试访问不属于他的资源 → 应被拒绝
            mockMvc.perform(get("/api/datasource/{id}", 10L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("项目不存在"));
        }

        @Test
        @DisplayName("用户隔离: 更新其他用户的资源被拒绝")
        void updateOtherUserResource() throws Exception {
            when(datasourceService.update(eq(10L), eq(CURRENT_USER_ID), any()))
                    .thenThrow(new BusinessException(404, "项目不存在"));

            mockMvc.perform(put("/api/datasource/{id}", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("项目不存在"));
        }
    }

    // ============================================================
    // 测试连接 (POST /api/datasource/test)
    // ============================================================

    @Nested
    @DisplayName("POST /api/datasource/test — 测试数据库连接")
    class TestConnectionTests {

        @Test
        @DisplayName("连接测试成功")
        void testConnectionSuccess() throws Exception {
            when(datasourceService.testConnection(any(DatasourceRequest.class)))
                    .thenReturn(true);

            mockMvc.perform(post("/api/datasource/test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("连接测试失败")
        void testConnectionFailure() throws Exception {
            when(datasourceService.testConnection(any(DatasourceRequest.class)))
                    .thenReturn(false);

            mockMvc.perform(post("/api/datasource/test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(false));
        }
    }

    /**
     * 自定义参数解析器：将 SecurityContextHolder 中的 Authentication 注入到 Controller 方法参数
     *
     * <p>当 {@code @AutoConfigureMockMvc(addFilters = false)} 禁用 Spring Security 过滤器链后，
     * 原生的 {@code Authentication} 参数绑定失效（因为请求中无 {@code userPrincipal}），
     * 此解析器从线程绑定的 {@code SecurityContextHolder} 中获取 Authentication 实例。
     */
    @Configuration
    static class TestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(0, new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return Authentication.class.isAssignableFrom(parameter.getParameterType());
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                                              ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest,
                                              WebDataBinderFactory binderFactory) {
                    return SecurityContextHolder.getContext().getAuthentication();
                }
            });
        }
    }
}
