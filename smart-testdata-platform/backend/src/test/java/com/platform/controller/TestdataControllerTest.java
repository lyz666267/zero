package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.GeneratePlanRequest;
import com.platform.dto.GeneratePlanResponse;
import com.platform.config.JwtAuthenticationFilter;
import com.platform.exception.BusinessException;
import com.platform.exception.GlobalExceptionHandler;
import com.platform.generator.GeneratorEngine;
import com.platform.generator.persistence.MultiTableWriteService;
import com.platform.generator.table.TableDataGenerator;
import com.platform.generator.task.MultiTableDataGenerator;
import com.platform.service.TestdataService;
import com.platform.sql.InsertSqlBuilder;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TestdataController 单元测试 — Spring MockMvc
 *
 * 测试覆盖: generatePlan 正常调用 / 参数校验 / AI 服务异常
 */
@WebMvcTest(
        value = TestdataController.class,
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
@DisplayName("TestdataController — 测试数据生成接口测试")
class TestdataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TestdataService testdataService;

    @MockBean
    private GeneratorEngine generatorEngine;

    @MockBean
    private TableDataGenerator tableDataGenerator;

    @MockBean
    private MultiTableDataGenerator multiTableDataGenerator;

    @MockBean
    private InsertSqlBuilder insertSqlBuilder;

    @MockBean
    private MultiTableWriteService multiTableWriteService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private Map<String, Object> validSchema;

    @BeforeEach
    void setUp() {
        validSchema = Map.of(
                "database", "test_db",
                "dbType", "MySQL",
                "tables", List.of(
                        Map.of(
                                "tableName", "users",
                                "comment", "用户表",
                                "columns", List.of(
                                        Map.of("name", "id", "type", "INT",
                                                "nullable", false, "primaryKey", true),
                                        Map.of("name", "username", "type", "VARCHAR(50)",
                                                "nullable", false),
                                        Map.of("name", "email", "type", "VARCHAR(100)",
                                                "nullable", false)
                                )
                        )
                )
        );
    }

    // ============================================================
    // generatePlan 测试
    // ============================================================

    @Nested
    @DisplayName("POST /api/testdata/generate-plan — 生成测试数据计划")
    class GeneratePlanTests {

        @Test
        @DisplayName("正常调用: 返回生成计划")
        void generatePlanSuccess() throws Exception {
            GeneratePlanResponse mockPlan = GeneratePlanResponse.builder()
                    .success(true)
                    .mock(false)
                    .plan(GeneratePlanResponse.PlanData.builder()
                            .taskName("users 表测试数据生成")
                            .tables(List.of(
                                    GeneratePlanResponse.TablePlan.builder()
                                            .table("users")
                                            .count(1000)
                                            .fields(List.of(
                                                    GeneratePlanResponse.FieldPlan.builder()
                                                            .name("username")
                                                            .generator("faker.name")
                                                            .build(),
                                                    GeneratePlanResponse.FieldPlan.builder()
                                                            .name("email")
                                                            .generator("faker.email")
                                                            .build()
                                            ))
                                            .build()
                            ))
                            .build())
                    .build();

            when(testdataService.generatePlan(any(GeneratePlanRequest.class)))
                    .thenReturn(mockPlan);

            GeneratePlanRequest request = new GeneratePlanRequest();
            request.setSchema(validSchema);
            request.setRequirement("生成1000条用户数据");

            mockMvc.perform(post("/api/testdata/generate-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.mock").value(false))
                    .andExpect(jsonPath("$.data.plan.taskName").value("users 表测试数据生成"))
                    .andExpect(jsonPath("$.data.plan.tables[0].table").value("users"))
                    .andExpect(jsonPath("$.data.plan.tables[0].count").value(1000))
                    .andExpect(jsonPath("$.data.plan.tables[0].fields.length()").value(2))
                    .andExpect(jsonPath("$.data.plan.tables[0].fields[0].name").value("username"))
                    .andExpect(jsonPath("$.data.plan.tables[0].fields[0].generator").value("faker.name"));
        }

        @Test
        @DisplayName("正常调用: Mock 模式返回")
        void generatePlanMockMode() throws Exception {
            GeneratePlanResponse mockPlan = GeneratePlanResponse.builder()
                    .success(true)
                    .mock(true)  // Mock 模式
                    .plan(GeneratePlanResponse.PlanData.builder()
                            .taskName("Mock 生成计划")
                            .tables(List.of(
                                    GeneratePlanResponse.TablePlan.builder()
                                            .table("users")
                                            .count(100)
                                            .fields(List.of(
                                                    GeneratePlanResponse.FieldPlan.builder()
                                                            .name("username")
                                                            .generator("faker.name")
                                                            .build()
                                            ))
                                            .build()
                            ))
                            .build())
                    .build();

            when(testdataService.generatePlan(any(GeneratePlanRequest.class)))
                    .thenReturn(mockPlan);

            GeneratePlanRequest request = new GeneratePlanRequest();
            request.setSchema(validSchema);
            request.setRequirement("生成100条数据");

            mockMvc.perform(post("/api/testdata/generate-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.mock").value(true))
                    .andExpect(jsonPath("$.data.plan.taskName").value("Mock 生成计划"));
        }

        @Test
        @DisplayName("AI 服务调用失败: 返回业务异常")
        void generatePlanAiServiceFailure() throws Exception {
            when(testdataService.generatePlan(any(GeneratePlanRequest.class)))
                    .thenThrow(new BusinessException("AI 服务调用失败，请确保 AI 服务已启动"));

            GeneratePlanRequest request = new GeneratePlanRequest();
            request.setSchema(validSchema);
            request.setRequirement("生成1000条用户数据");

            mockMvc.perform(post("/api/testdata/generate-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("AI 服务调用失败，请确保 AI 服务已启动"));
        }

        @Test
        @DisplayName("参数校验: 空 schema — 仍可正常调用")
        void generatePlanEmptySchema() throws Exception {
            GeneratePlanResponse mockPlan = GeneratePlanResponse.builder()
                    .success(true)
                    .mock(true)
                    .plan(GeneratePlanResponse.PlanData.builder()
                            .taskName("空 Schema 降级")
                            .tables(List.of())
                            .build())
                    .build();

            when(testdataService.generatePlan(any(GeneratePlanRequest.class)))
                    .thenReturn(mockPlan);

            GeneratePlanRequest request = new GeneratePlanRequest();
            request.setSchema(Map.of());
            request.setRequirement("生成数据");

            mockMvc.perform(post("/api/testdata/generate-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.plan.tables").isArray());
        }

        @Test
        @DisplayName("参数校验: null requirement — 允许透传")
        void generatePlanNullRequirement() throws Exception {
            GeneratePlanResponse mockPlan = GeneratePlanResponse.builder()
                    .success(false)
                    .error("需求不能为空")
                    .mock(false)
                    .build();

            when(testdataService.generatePlan(any(GeneratePlanRequest.class)))
                    .thenReturn(mockPlan);

            GeneratePlanRequest request = new GeneratePlanRequest();
            request.setSchema(validSchema);
            request.setRequirement(null);

            mockMvc.perform(post("/api/testdata/generate-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(false))
                    .andExpect(jsonPath("$.data.error").value("需求不能为空"));
        }
    }
}
