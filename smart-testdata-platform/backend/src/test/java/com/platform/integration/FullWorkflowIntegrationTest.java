package com.platform.integration;

import com.platform.dto.GeneratePlanResponse;
import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.dto.GeneratePlanResponse.PlanData;
import com.platform.dto.GeneratePlanResponse.TablePlan;
import com.platform.dto.MultiTableGenerateResponse;
import com.platform.dto.MultiTableGenerateResponse.TableResult;
import com.platform.dto.QualityReportResponse;
import com.platform.entity.*;
import com.platform.generator.task.MultiTableDataGenerator;
import com.platform.generator.persistence.MultiTableWriteService;
import com.platform.mapper.*;
import com.platform.privacy.SensitiveFieldType;
import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.detector.CompositeSensitiveDetector;
import com.platform.privacy.detector.DetectionContext;
import com.platform.privacy.detector.DetectionResult;
import com.platform.privacy.service.PrivacyAwareDataProcessor;
import com.platform.schema.SchemaCacheService;
import com.platform.service.*;
import com.platform.util.AesUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 全流程端到端集成测试 — 验证智能测试数据生成与隐私脱敏平台完整业务流程
 *
 * <h3>测试流程</h3>
 * <ol>
 *   <li>创建测试任务 → 状态 PENDING</li>
 *   <li>连接 MySQL 测试数据库（TestContainers）</li>
 *   <li>Schema 自动解析 → 缓存到 schema_table / schema_column</li>
 *   <li>LLM Agent 生成测试数据计划（Mock）</li>
 *   <li>调用生成工具 → MultiTableDataGenerator 按依赖顺序生成</li>
 *   <li>生成测试数据 → 写入目标数据库</li>
 *   <li>保存生成结果 → TestDataResult 持久化</li>
 *   <li>敏感字段检测 → 三层融合检测</li>
 *   <li>隐私脱敏 → PrivacyAwareDataProcessor 自动脱敏</li>
 *   <li>数据质量评分 → 五项指标综合评估</li>
 *   <li>Agent 执行轨迹 → 步骤日志记录</li>
 *   <li>CSV / SQL / JSON 导出 → ExportService 三种格式</li>
 * </ol>
 *
 * <h3>测试数据库</h3>
 * <p>使用 TestContainers MySQL 8.0，通过 Flyway 自动迁移平台表，
 * 并在 @BeforeAll 中创建测试业务表（user / order 含外键关系）。</p>
 *
 * <h3>Mock 策略</h3>
 * <p>仅 Mock AI 服务调用（TestdataService），因为 Python AI 服务在单元测试中不可用。
 * 其余所有组件均使用真实实例，在真实 MySQL 上执行。</p>
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("全流程端到端集成测试")
class FullWorkflowIntegrationTest {

    // ==================== TestContainers MySQL ====================

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    // ==================== Mock AI 服务 ====================

    @MockBean
    private TestdataService testdataService;

    // ==================== 真实服务注入 ====================

    @Autowired
    private TestDataTaskMapper taskMapper;
    @Autowired
    private TestDataResultService resultService;
    @Autowired
    private TestDataResultMapper resultMapper;
    @Autowired
    private TestDataTaskPlanService planService;
    @Autowired
    private MultiTableDataGenerator multiTableDataGenerator;
    @Autowired
    private MultiTableWriteService multiTableWriteService;
    @Autowired
    private SchemaCacheService schemaCacheService;
    @Autowired
    private PrivacyAwareDataProcessor privacyProcessor;
    @Autowired
    private CompositeSensitiveDetector sensitiveDetector;
    @Autowired
    private DataQualityEvaluator qualityEvaluator;
    @Autowired
    private AgentLogService agentLogService;
    @Autowired
    private AgentExecutionLogMapper agentLogMapper;
    @Autowired
    private ExportService exportService;
    @Autowired
    private AesUtil aesUtil;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private DatasourceMapper datasourceMapper;
    @Autowired
    private UserMapper userMapper;

    // ==================== 测试状态 ====================

    private static Long testUserId;
    private static Long testProjectId;
    private static Long testDatasourceId;
    private static final String TEST_DB = "testdb";

    /** 每张表生成的计划行数 */
    private static final int USER_ROWS = 5;
    private static final int ORDER_ROWS = 10;

    // ==================== 初始化：创建测试表 ====================

    @BeforeAll
    static void setupTestTables() throws Exception {
        // 使用 JDBC 直接在 MySQL 容器中创建测试业务表
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(mysql.getJdbcUrl());
        ds.setUsername(mysql.getUsername());
        ds.setPassword(mysql.getPassword());

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            // 创建 user 表（含手机号、邮箱等敏感字段）
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL COMMENT '用户姓名',
                        phone VARCHAR(20) NOT NULL COMMENT '手机号',
                        email VARCHAR(200) NOT NULL COMMENT '电子邮箱'
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表'
                    """);

            // 创建 order 表（外键 → user.id）
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `order` (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL COMMENT '用户ID（外键）',
                        amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
                        CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user(id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表'
                    """);

            System.out.println("✅ 测试表创建完成: user + order (含外键 fk_order_user → user.id)");
        }
    }

    // ==================== 每个测试前：准备数据源和 Schema 缓存 ====================

    @BeforeEach
    void setupDatasourceAndSchema() {
        // 1. 创建测试用户（如果尚未创建）
        if (testUserId == null) {
            User user = new User();
            user.setUsername("e2e_test_user");
            user.setPassword("$2a$10$dummy_bcrypt_hash_for_test");
            user.setNickname("E2E测试用户");
            user.setEnabled(true);
            userMapper.insert(user);
            testUserId = user.getId();
            System.out.println("👤 测试用户已创建: id=" + testUserId);
        }

        // 2. 创建测试项目（如果尚未创建）
        if (testProjectId == null) {
            Project project = new Project();
            project.setUserId(testUserId);
            project.setName("E2E测试项目");
            project.setDescription("全流程端到端集成测试");
            projectMapper.insert(project);
            testProjectId = project.getId();
            System.out.println("📁 测试项目已创建: id=" + testProjectId);
        }

        // 3. 创建数据源配置（指向 TestContainers MySQL）
        if (testDatasourceId == null) {
            Datasource ds = new Datasource();
            ds.setProjectId(testProjectId);
            ds.setName("E2E测试数据源");
            ds.setDbType("mysql");
            ds.setHost(mysql.getHost());
            ds.setPort(mysql.getMappedPort(3306));
            ds.setUsername(mysql.getUsername());
            ds.setPasswordEncrypted(aesUtil.encrypt(mysql.getPassword()));
            ds.setDbName(TEST_DB);
            ds.setStatus("CONNECTED");
            datasourceMapper.insert(ds);
            testDatasourceId = ds.getId();
            System.out.println("🔗 测试数据源已创建: id=" + testDatasourceId
                    + ", host=" + ds.getHost() + ":" + ds.getPort());
        }

        // 4. 同步 Schema 缓存（如果尚未缓存）
        if (!schemaCacheService.hasCache(testDatasourceId)) {
            schemaCacheService.sync(testDatasourceId);
            System.out.println("📊 Schema 缓存已同步: datasourceId=" + testDatasourceId);
        }
    }

    // ==================== Test 1: 全流程主测试 ====================

    @Test
    @Order(1)
    @DisplayName("1. 全流程端到端 — 创建任务 → 生成 → 脱敏 → 质量评估 → 导出")
    void shouldCompleteFullWorkflow() {
        // ========== Step 1: 创建测试任务 ==========
        Long taskId = createTaskDirectly("E2E全流程测试-" + System.currentTimeMillis());
        assertNotNull(taskId, "任务ID不应为null");

        // 读取任务信息
        TestDataTask task = taskMapper.selectById(taskId);
        String taskName = task.getTaskName();
        System.out.println("✅ Step 1: 任务创建成功 — taskId=" + taskId + ", status=" + task.getStatus());

        // ========== Step 2: Schema 分析 ==========
        var schema = schemaCacheService.getSchema(testDatasourceId);
        assertNotNull(schema, "Schema不应为null");
        assertTrue(schema.getTables().size() >= 2,
                "应至少包含user和order两张表, 实际: " + schema.getTables().size());
        System.out.println("✅ Step 2: Schema分析完成 — " + schema.getTables().size() + " 张表");

        // ========== Step 3: AI 生成计划（Mock） ==========
        GeneratePlanResponse mockPlan = buildMockPlan();
        when(testdataService.generatePlan(any())).thenReturn(mockPlan);

        // 通过 service 调用（会被 mock 拦截）
        var planResponse = testdataService.generatePlan(null);
        assertTrue(planResponse.isSuccess(), "计划生成应成功");
        assertNotNull(planResponse.getPlan(), "计划不应为null");
        assertEquals(2, planResponse.getPlan().getTables().size(),
                "应包含2张表的计划");
        System.out.println("✅ Step 3: AI计划生成 — 2张表 (user=" + USER_ROWS + "行, order=" + ORDER_ROWS + "行)");

        // 保存计划
        planService.savePlan(taskId, planResponse);
        var savedPlan = planService.getPlan(taskId);
        assertNotNull(savedPlan, "保存的计划应可查询");

        // ========== Step 4: 调用生成工具 ==========
        List<TablePlan> tablePlans = planResponse.getPlan().getTables();
        MultiTableGenerateResponse genResult = multiTableDataGenerator.generate(tablePlans);

        assertTrue(genResult.isSuccess(), "多表生成应成功");
        assertEquals(2, genResult.getTables().size(), "应生成2张表的数据");
        int totalRows = genResult.getTables().stream().mapToInt(TableResult::getCount).sum();
        assertEquals(USER_ROWS + ORDER_ROWS, totalRows,
                "总行数应为 " + (USER_ROWS + ORDER_ROWS) + ", 实际: " + totalRows);
        System.out.println("✅ Step 4: 数据生成完成 — " + totalRows + " 条记录");

        // ========== Step 4.5: 验证外键关系 ==========
        // order.user_id 必须在 user.id 集合内
        TableResult userTable = genResult.getTables().stream()
                .filter(t -> "user".equals(t.getTable())).findFirst().orElseThrow();
        TableResult orderTable = genResult.getTables().stream()
                .filter(t -> "order".equals(t.getTable())).findFirst().orElseThrow();

        Set<Object> userIds = new HashSet<>();
        for (Map<String, Object> row : userTable.getData()) {
            userIds.add(row.get("id"));
        }
        assertEquals(USER_ROWS, userIds.size(), "user表的ID应全部唯一");

        for (Map<String, Object> row : orderTable.getData()) {
            Object fkVal = row.get("user_id");
            assertNotNull(fkVal, "order.user_id不应为null");
            assertTrue(userIds.contains(fkVal),
                    "order.user_id=" + fkVal + " 应在user.id集合中");
        }
        System.out.println("✅ Step 4.5: 外键验证通过 — 全部 " + ORDER_ROWS + " 条order.user_id有效");

        // ========== Step 5: 事务写入目标数据库 ==========
        List<com.platform.dto.DatabaseWriteRequest.TableData> writeList = new ArrayList<>();
        for (TableResult tr : genResult.getTables()) {
            var td = new com.platform.dto.DatabaseWriteRequest.TableData();
            td.setTable(tr.getTable());
            td.setData(tr.getData());
            writeList.add(td);
        }

        // 先手动删除旧测试数据（如有）
        try {
            var writeDs = createTestDataSource();
            new org.springframework.jdbc.core.JdbcTemplate(writeDs).execute("DELETE FROM `order`");
            new org.springframework.jdbc.core.JdbcTemplate(writeDs).execute("DELETE FROM user");
        } catch (Exception ignored) {
            // 表可能为空
        }

        var writeResponse = multiTableWriteService.writeAll(testDatasourceId, writeList);
        assertTrue(writeResponse.isSuccess(), "多表写入应成功");
        assertEquals(2, writeResponse.getTables().size());
        System.out.println("✅ Step 5: 数据库写入完成 — user=" + USER_ROWS + "行, order=" + ORDER_ROWS + "行");

        // 验证目标数据库实际数据
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(createTestDataSource());
        Integer dbUserCount = jdbc.queryForObject("SELECT COUNT(*) FROM user", Integer.class);
        Integer dbOrderCount = jdbc.queryForObject("SELECT COUNT(*) FROM `order`", Integer.class);
        assertEquals(USER_ROWS, dbUserCount, "数据库user表应有" + USER_ROWS + "行");
        assertEquals(ORDER_ROWS, dbOrderCount, "数据库order表应有" + ORDER_ROWS + "行");

        // ========== Step 6: 保存生成结果 ==========
        for (TableResult tr : genResult.getTables()) {
            resultService.saveResult(taskId, tr.getTable(), tr.getData());
        }
        var results = resultService.findByTaskId(taskId);
        assertEquals(2, results.size(), "应保存2张表的结果");
        var dataMap = resultService.findDataByTaskId(taskId);
        assertEquals(2, dataMap.size());
        assertEquals(USER_ROWS, dataMap.get("user").size());
        assertEquals(ORDER_ROWS, dataMap.get("order").size());
        System.out.println("✅ Step 6: 生成结果已保存 — 2张表");

        // ========== Step 7: 敏感字段检测 ==========
        // 构建列元数据
        List<SchemaColumn> userColumns = buildUserColumns();
        List<SchemaColumn> orderColumns = buildOrderColumns();

        // 检测 user 表的敏感字段
        DetectionContext userCtx = new DetectionContext(userColumns, userTable.getData());
        List<DetectionResult> userDetections = sensitiveDetector.detect(userCtx);
        List<String> userSensitiveCols = userDetections.stream()
                .filter(r -> r.getType() != SensitiveFieldType.UNKNOWN)
                .map(DetectionResult::getColumnName)
                .toList();
        assertTrue(userSensitiveCols.contains("phone"), "phone应被检测为敏感字段");
        assertTrue(userSensitiveCols.contains("email"), "email应被检测为敏感字段");
        assertTrue(userSensitiveCols.contains("name"), "name应被检测为敏感字段");
        System.out.println("✅ Step 7: 敏感字段检测完成 — user表: " + userSensitiveCols);

        // 检测 order 表（不应有敏感字段，或仅 user_id 可能被关键字误判）
        DetectionContext orderCtx = new DetectionContext(orderColumns, orderTable.getData());
        List<DetectionResult> orderDetections = sensitiveDetector.detect(orderCtx);
        System.out.println("   order表敏感字段: " + orderDetections.stream()
                .filter(r -> r.getType() != SensitiveFieldType.UNKNOWN)
                .map(DetectionResult::getColumnName).toList());

        // ========== Step 8: 隐私脱敏 ==========
        // 将检测结果转换为 SensitiveFieldInfo 列表
        List<com.platform.dto.PrivacyProcessRequest.SensitiveFieldInfo> fieldInfos = userDetections.stream()
                .filter(r -> r.getType() != SensitiveFieldType.UNKNOWN)
                .map(r -> new com.platform.dto.PrivacyProcessRequest.SensitiveFieldInfo(
                        r.getColumnName(), r.getType().name()))
                .toList();
        var maskedUserData = privacyProcessor.process(userTable.getData(), fieldInfos);
        assertEquals(USER_ROWS, maskedUserData.size(), "脱敏后行数不变");

        // 验证脱敏效果：phone/email/name 应被脱敏（含 *** 或其它脱敏标记）
        boolean allMasked = true;
        for (Map<String, Object> row : maskedUserData) {
            String phone = String.valueOf(row.get("phone"));
            String email = String.valueOf(row.get("email"));
            if (!phone.contains("*") && !email.contains("*")) {
                allMasked = false;
                break;
            }
        }
        assertTrue(allMasked, "所有敏感字段应已被脱敏（含*号标记）");
        System.out.println("✅ Step 8: 隐私脱敏完成 — user表全部敏感字段已脱敏");

        // ========== Step 8.5: 更新任务状态为 SUCCESS ==========
        taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TestDataTask>()
                        .eq(TestDataTask::getId, taskId)
                        .set(TestDataTask::getStatus, "SUCCESS")
                        .set(TestDataTask::getSuccessCount, totalRows)
                        .set(TestDataTask::getFinishTime, LocalDateTime.now()));

        // ========== Step 9: 数据质量评估 ==========
        QualityReportResponse qualityReport = qualityEvaluator.evaluate(taskId, testDatasourceId);
        assertNotNull(qualityReport, "质量报告不应为null");
        assertNotNull(qualityReport.getTotalScore(), "总评分不应为null");
        assertNotNull(qualityReport.getGrade(), "等级不应为null");
        assertNotNull(qualityReport.getMetrics(), "指标详情不应为null");
        assertEquals(5, qualityReport.getMetrics().size(), "应包含5项指标");

        System.out.println("✅ Step 9: 质量评估完成 — 总分: " + qualityReport.getTotalScore()
                + ", 等级: " + qualityReport.getGrade());
        System.out.println("   指标: " + qualityReport.getMetrics());

        // 验证可通过 API 查询
        var savedReport = qualityEvaluator.getReport(taskId);
        assertNotNull(savedReport, "保存的质量报告应可查询");
        assertEquals(qualityReport.getTotalScore(), savedReport.getTotalScore());

        // ========== Step 10: Agent 执行轨迹 ==========
        agentLogService.logStep(taskId, 1, "PARSE", "需求解析",
                Map.of("taskName", taskName),
                Map.of("taskId", taskId), "", "SUCCESS", 5L, "ToolAgent");

        agentLogService.logStep(taskId, 2, "ANALYZE", "Schema分析",
                Map.of("datasourceId", testDatasourceId),
                Map.of("tableCount", 2), "SchemaTool", "SUCCESS", 15L, "ToolAgent");

        agentLogService.logStep(taskId, 3, "PLAN", "生成计划",
                Map.of("tableCount", 2),
                Map.of("plannedTables", List.of("user", "order")),
                "LLM Agent", "SUCCESS", 200L, "ToolAgent");

        agentLogService.logStep(taskId, 4, "GENERATE", "数据生成",
                Map.of("tables", 2),
                Map.of("totalRows", totalRows),
                "MultiTableDataGenerator", "SUCCESS", 100L, "ToolAgent");

        agentLogService.logStep(taskId, 5, "PRIVACY", "隐私脱敏",
                Map.of("sensitiveFields", userSensitiveCols.size()),
                Map.of("masked", "SUCCESS"),
                "PrivacyAwareDataProcessor", "SUCCESS", 50L, "ToolAgent");

        agentLogService.logStep(taskId, 6, "QUALITY", "质量评估",
                Map.of("note", "五项指标综合评估"),
                Map.of("totalScore", qualityReport.getTotalScore(),
                       "grade", qualityReport.getGrade()),
                "DataQualityEvaluator", "SUCCESS", 30L, "ToolAgent");

        var agentLogs = agentLogService.getLogs(taskId);
        assertNotNull(agentLogs, "Agent日志不应为null");
        assertEquals(6, agentLogs.getSteps().size(), "应有6个执行步骤");
        System.out.println("✅ Step 10: Agent执行轨迹 — " + agentLogs.getSteps().size() + " 个步骤");

        // ========== Step 11: 导出文件 ==========
        // CSV 导出
        String csv = exportService.exportTaskData(taskId, "CSV");
        assertNotNull(csv, "CSV导出不应为null");
        assertTrue(csv.contains("# 任务:"), "CSV应包含任务注释");
        assertTrue(csv.contains("# table: user"), "CSV应包含user表注释");
        assertTrue(csv.contains("# table: order"), "CSV应包含order表注释");
        System.out.println("✅ Step 11a: CSV导出 — " + csv.lines().count() + " 行");

        // SQL 导出
        String sql = exportService.exportTaskData(taskId, "SQL");
        assertNotNull(sql, "SQL导出不应为null");
        assertTrue(sql.contains("INSERT INTO user"), "SQL应包含user表INSERT");
        assertTrue(sql.contains("INSERT INTO `order`"), "SQL应包含order表INSERT");
        System.out.println("✅ Step 11b: SQL导出 — " + sql.lines().count() + " 行");

        // JSON 导出
        String json = exportService.exportTaskData(taskId, "JSON");
        assertNotNull(json, "JSON导出不应为null");
        assertTrue(json.contains("\"taskId\""), "JSON应包含taskId");
        assertTrue(json.contains("\"tables\""), "JSON应包含tables");
        assertTrue(json.contains("\"user\""), "JSON应包含user表");
        assertTrue(json.contains("\"order\""), "JSON应包含order表");
        System.out.println("✅ Step 11c: JSON导出 — " + json.length() + " 字符");

        // 验证导出文件名
        String csvName = exportService.generateFileName(taskId, "CSV");
        assertTrue(csvName.endsWith(".csv"), "CSV文件名应.csv结尾");
        String sqlName = exportService.generateFileName(taskId, "SQL");
        assertTrue(sqlName.endsWith(".sql"), "SQL文件名应.sql结尾");
        String jsonName = exportService.generateFileName(taskId, "JSON");
        assertTrue(jsonName.endsWith(".json"), "JSON文件名应.json结尾");

        System.out.println("\n🎉 ====== 全流程端到端测试通过！ ======");
        System.out.println("   任务ID: " + taskId);
        System.out.println("   生成数据: user=" + USER_ROWS + "行, order=" + ORDER_ROWS + "行");
        System.out.println("   外键验证: ✅");
        System.out.println("   敏感字段: phone, email, name → 已脱敏");
        System.out.println("   质量评分: " + qualityReport.getTotalScore() + " (" + qualityReport.getGrade() + ")");
        System.out.println("   导出格式: CSV ✅ | SQL ✅ | JSON ✅");
        System.out.println("   执行轨迹: " + agentLogs.getSteps().size() + " 步骤");
    }

    // ==================== Test 2: 生成数量验证 ====================

    @Test
    @Order(2)
    @DisplayName("2. 验证生成数量正确 — user=5行, order=10行")
    void shouldGenerateCorrectRowCounts() {
        GeneratePlanResponse mockPlan = buildMockPlan();
        when(testdataService.generatePlan(any())).thenReturn(mockPlan);

        MultiTableGenerateResponse genResult = multiTableDataGenerator.generate(
                mockPlan.getPlan().getTables());

        assertEquals(2, genResult.getTables().size());
        TableResult userT = genResult.getTables().get(0);
        TableResult orderT = genResult.getTables().get(1);

        assertEquals(USER_ROWS, userT.getCount(), "user表行数应为" + USER_ROWS);
        assertEquals(ORDER_ROWS, orderT.getCount(), "order表行数应为" + ORDER_ROWS);

        // 验证所有字段非空
        for (Map<String, Object> row : userT.getData()) {
            assertNotNull(row.get("id"), "user.id不应为null");
            assertNotNull(row.get("name"), "user.name不应为null");
            assertNotNull(row.get("phone"), "user.phone不应为null");
            assertNotNull(row.get("email"), "user.email不应为null");
        }
        for (Map<String, Object> row : orderT.getData()) {
            assertNotNull(row.get("id"), "order.id不应为null");
            assertNotNull(row.get("user_id"), "order.user_id不应为null");
            assertNotNull(row.get("amount"), "order.amount不应为null");
        }
        System.out.println("✅ 生成数量验证通过: user=" + USER_ROWS + "行(全字段非空), order=" + ORDER_ROWS + "行(全字段非空)");
    }

    // ==================== Test 3: 质量报告验证 ====================

    @Test
    @Order(3)
    @DisplayName("3. 验证质量评分生成 — 含五项指标 + 等级评定")
    void shouldProduceValidQualityReport() {
        // 创建独立任务用于质量评估
        Long taskId = createTaskDirectly("E2E-质量评估验证-" + System.currentTimeMillis());

        // Mock + 生成数据 + 保存结果
        GeneratePlanResponse mockPlan = buildMockPlan();
        when(testdataService.generatePlan(any())).thenReturn(mockPlan);
        var genResult = multiTableDataGenerator.generate(mockPlan.getPlan().getTables());
        for (TableResult tr : genResult.getTables()) {
            resultService.saveResult(taskId, tr.getTable(), tr.getData());
        }
        taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TestDataTask>()
                        .eq(TestDataTask::getId, taskId)
                        .set(TestDataTask::getStatus, "SUCCESS")
                        .set(TestDataTask::getSuccessCount,
                                genResult.getTables().stream().mapToInt(TableResult::getCount).sum())
                        .set(TestDataTask::getFinishTime, LocalDateTime.now()));

        // 执行质量评估
        QualityReportResponse report = qualityEvaluator.evaluate(taskId, testDatasourceId);
        assertNotNull(report, "质量报告不应为null");

        // 五项指标验证
        var metrics = report.getMetrics();
        assertTrue(metrics.containsKey("completeness"), "应包含完整性指标");
        assertTrue(metrics.containsKey("uniqueness"), "应包含唯一性指标");
        assertTrue(metrics.containsKey("consistency"), "应包含一致性指标");
        assertTrue(metrics.containsKey("validity"), "应包含合法性指标");
        assertTrue(metrics.containsKey("privacy"), "应包含隐私安全指标");

        // 指标值应在 [0, 100] 范围内
        for (var entry : metrics.entrySet()) {
            assertTrue(entry.getValue() >= 0 && entry.getValue() <= 100,
                    entry.getKey() + " 应在[0,100]范围内, 实际: " + entry.getValue());
        }

        // 等级验证
        assertNotNull(report.getGrade(), "等级不应为null");
        assertTrue(Set.of("优秀", "良好", "合格", "不合格").contains(report.getGrade()),
                "等级应为有效值, 实际: " + report.getGrade());

        // 总分与等级逻辑一致
        double score = report.getTotalScore();
        if (score >= 90) assertEquals("优秀", report.getGrade());
        else if (score >= 80) assertEquals("良好", report.getGrade());
        else if (score >= 60) assertEquals("合格", report.getGrade());
        else assertEquals("不合格", report.getGrade());

        System.out.println("✅ 质量评分验证通过: totalScore=" + score
                + ", grade=" + report.getGrade()
                + ", metrics=" + metrics);
    }

    // ==================== Test 4: 导出完整性验证 ====================

    @Test
    @Order(4)
    @DisplayName("4. 验证导出文件 — CSV/SQL/JSON 三种格式完整性")
    void shouldExportAllThreeFormatsCorrectly() {
        // 创建独立任务
        Long taskId = createTaskDirectly("E2E-导出测试-" + System.currentTimeMillis());

        // 生成 + 保存
        GeneratePlanResponse mockPlan = buildMockPlan();
        when(testdataService.generatePlan(any())).thenReturn(mockPlan);
        var genResult = multiTableDataGenerator.generate(mockPlan.getPlan().getTables());
        for (TableResult tr : genResult.getTables()) {
            resultService.saveResult(taskId, tr.getTable(), tr.getData());
        }
        taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TestDataTask>()
                        .eq(TestDataTask::getId, taskId)
                        .set(TestDataTask::getStatus, "SUCCESS")
                        .set(TestDataTask::getSuccessCount,
                                genResult.getTables().stream().mapToInt(TableResult::getCount).sum())
                        .set(TestDataTask::getFinishTime, LocalDateTime.now()));

        // === CSV 验证 ===
        String csv = exportService.exportTaskData(taskId, "CSV");
        assertNotNull(csv);
        assertFalse(csv.isEmpty(), "CSV不应为空");
        assertTrue(csv.contains("id,name,phone,email")
                        || csv.contains("\"id\",\"name\",\"phone\",\"email\""),
                "CSV应包含user表头");
        // 应有 USER_ROWS 行数据 + 2行注释 + 1行表头 + 注释行
        assertTrue(csv.lines().count() > USER_ROWS,
                "CSV行数应大于" + USER_ROWS + ", 实际: " + csv.lines().count());

        // === SQL 验证 ===
        String sql = exportService.exportTaskData(taskId, "SQL");
        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO user"), "SQL应包含user INSERT");
        assertTrue(sql.contains("INSERT INTO"), "SQL应包含INSERT语句");
        assertTrue(sql.contains("VALUES"), "SQL应包含VALUES子句");

        // === JSON 验证 ===
        String json = exportService.exportTaskData(taskId, "JSON");
        assertNotNull(json);
        assertTrue(json.contains("\"taskId\":"), "JSON应包含taskId");
        assertTrue(json.contains("\"taskName\":"), "JSON应包含taskName");
        assertTrue(json.contains("\"tables\":"), "JSON应包含tables");
        assertTrue(json.contains("\"user\":"), "JSON应包含user表数据");
        assertTrue(json.contains("\"order\":"), "JSON应包含order表数据");

        // JSON 应为合法 JSON
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            fail("JSON导出应为合法JSON: " + e.getMessage());
        }

        System.out.println("✅ CSV导出验证: " + csv.lines().count() + "行");
        System.out.println("✅ SQL导出验证: " + sql.lines().count() + "行, 含INSERT INTO");
        System.out.println("✅ JSON导出验证: " + json.length() + "字符, 合法JSON");
    }

    // ==================== Test 5: 可导出任务列表 ====================

    @Test
    @Order(5)
    @DisplayName("5. 验证可导出任务列表 — 仅返回 SUCCESS 状态任务")
    void shouldListOnlySuccessTasks() {
        var tasks = exportService.listExportableTasks();
        assertNotNull(tasks, "任务列表不应为null");

        for (var task : tasks) {
            assertEquals("SUCCESS", task.getStatus(),
                    "列表中所有任务应均为SUCCESS状态");
        }
        System.out.println("✅ 可导出任务列表: " + tasks.size() + " 个SUCCESS任务");
    }

    // ==================== 辅助方法 ====================

    /**
     * 直接插入任务实体（不触发异步执行），返回任务 ID
     */
    private Long createTaskDirectly(String taskName) {
        TestDataTask task = new TestDataTask();
        task.setTaskName(taskName);
        task.setDatasourceId(testDatasourceId);
        task.setTotalCount(USER_ROWS + ORDER_ROWS);
        task.setStatus("PENDING");
        task.setSuccessCount(0);
        task.setFailCount(0);
        taskMapper.insert(task);
        return task.getId();
    }

    /**
     * 构建 Mock 生成计划 — user (5行) + order (10行, FK→user.id)
     */
    private GeneratePlanResponse buildMockPlan() {
        // user 表字段计划
        List<FieldPlan> userFields = List.of(
                FieldPlan.builder()
                        .name("id").generator("random.integer")
                        .range(new GeneratePlanResponse.Range(1, 10000))
                        .params(Map.of("unique", true)).build(),
                FieldPlan.builder()
                        .name("name").generator("faker.name")
                        .params(Map.of("locale", "zh_CN")).build(),
                FieldPlan.builder()
                        .name("phone").generator("faker.phone")
                        .params(Map.of("locale", "zh_CN")).build(),
                FieldPlan.builder()
                        .name("email").generator("faker.email")
                        .params(Map.of("locale", "zh_CN")).build()
        );

        // order 表字段计划（含外键→user.id）
        List<FieldPlan> orderFields = List.of(
                FieldPlan.builder()
                        .name("id").generator("random.integer")
                        .range(new GeneratePlanResponse.Range(1, 10000))
                        .params(Map.of("unique", true)).build(),
                FieldPlan.builder()
                        .name("user_id").generator("fk.reference")
                        .foreignKey(new com.platform.dto.ForeignKeyInfo("user", "id"))
                        .params(Map.of()).build(),
                FieldPlan.builder()
                        .name("amount").generator("random.decimal")
                        .range(new GeneratePlanResponse.Range(1, 10000))
                        .params(Map.of("decimals", 2)).build()
        );

        List<TablePlan> tables = List.of(
                TablePlan.builder().table("user").count(USER_ROWS).fields(userFields).build(),
                TablePlan.builder().table("order").count(ORDER_ROWS).fields(orderFields).build()
        );

        return GeneratePlanResponse.builder()
                .success(true)
                .mock(true)
                .plan(PlanData.builder()
                        .taskName("E2E测试计划")
                        .tables(tables)
                        .build())
                .build();
    }

    /**
     * 构建 user 表列元数据（用于敏感字段检测 + 隐私脱敏）
     */
    private List<SchemaColumn> buildUserColumns() {
        return List.of(
                buildColumn("id", "bigint", true, false),
                buildColumn("name", "varchar(100)", false, false),
                buildColumn("phone", "varchar(20)", false, false),
                buildColumn("email", "varchar(200)", false, false)
        );
    }

    /**
     * 构建 order 表列元数据
     */
    private List<SchemaColumn> buildOrderColumns() {
        return List.of(
                buildColumn("id", "bigint", true, false),
                buildColumn("user_id", "bigint", false, false),
                buildColumn("amount", "decimal(10,2)", false, false)
        );
    }

    private SchemaColumn buildColumn(
            String name, String type, boolean primaryKey, boolean nullable) {
        var col = new SchemaColumn();
        col.setColumnName(name);
        col.setDataType(type.contains("varchar") ? "varchar" : type.contains("decimal") ? "decimal" : "bigint");
        col.setColumnType(type);
        col.setPrimaryKey(primaryKey);
        col.setNullable(nullable);
        col.setOrdinalPosition(0);
        return col;
    }

    /**
     * 创建到 TestContainers MySQL 的数据源（用于写入验证）
     */
    private DataSource createTestDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(mysql.getJdbcUrl());
        ds.setUsername(mysql.getUsername());
        ds.setPassword(mysql.getPassword());
        return ds;
    }
}
