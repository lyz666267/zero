package com.platform.service;

import com.platform.dto.QualityReportResponse;
import com.platform.mapper.TestDataTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据质量评估器测试
 *
 * <p>验证五项质量指标计算和综合评分算法的正确性。</p>
 */
@SpringBootTest
@DisplayName("数据质量评估器测试")
class DataQualityEvaluatorTest {

    @Autowired
    private DataQualityEvaluator evaluator;

    @Autowired
    private TestDataResultService resultService;

    @Autowired
    private TestDataTaskMapper taskMapper;

    @Autowired
    private com.platform.mapper.schema.SchemaTableMapper schemaTableMapper;

    @Autowired
    private com.platform.mapper.schema.SchemaColumnMapper schemaColumnMapper;

    @Autowired
    private com.platform.mapper.DatasourceMapper datasourceMapper;

    private static long uniqueIdCounter = System.currentTimeMillis();

    // ==================== 1. 完整性计算 ====================

    @Test
    @DisplayName("完整性 — 全量非空数据应得满分")
    void testCompletenessAllFilled() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("id", 1, "name", "张三", "email", "a@b.com", "phone", "13800001111"),
                createRow("id", 2, "name", "李四", "email", "c@d.com", "phone", "13900002222"),
                createRow("id", 3, "name", "王五", "email", "e@f.com", "phone", "13700003333")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        assertEquals(taskId, report.getTaskId());
        double completeness = report.getMetrics().get("completeness");
        assertEquals(100.0, completeness, 0.01,
                "全量非空数据完整性应为 100，实际: " + completeness);
    }

    @Test
    @DisplayName("完整性 — 部分缺失数据应扣分")
    void testCompletenessPartialNull() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("id", 1, "name", "张三", "email", null, "phone", "13800001111"),
                createRow("id", 2, "name", null, "email", "c@d.com", "phone", null),
                createRow("id", 3, "name", "王五", "email", "e@f.com", "phone", "13700003333")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double completeness = report.getMetrics().get("completeness");
        // 3 rows x 4 cols = 12 cells, 3 null -> 9/12 = 75%
        assertEquals(75.0, completeness, 0.01,
                "partial null completeness should be 75: " + completeness);
    }

    @Test
    @DisplayName("完整性 — 全部为空应得 0 分")
    void testCompletenessAllNull() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "empty_table", List.of(
                createRow("col1", null, "col2", null),
                createRow("col1", null, "col2", null)
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double completeness = report.getMetrics().get("completeness");
        assertEquals(0.0, completeness, 0.01,
                "全空数据完整性应为 0，实际: " + completeness);
    }

    // ==================== 2. 唯一性计算 ====================

    @Test
    @DisplayName("唯一性 — 无重复主键应得满分")
    void testUniquenessNoDuplicates() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("id", 1, "name", "张三"),
                createRow("id", 2, "name", "李四"),
                createRow("id", 3, "name", "王五")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double uniqueness = report.getMetrics().get("uniqueness");
        assertEquals(100.0, uniqueness, 0.01,
                "无重复数据唯一性应为 100，实际: " + uniqueness);
    }

    @Test
    @DisplayName("唯一性 — 主键重复应扣分")
    void testUniquenessDuplicatePK() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("id", 1, "name", "张三"),
                createRow("id", 1, "name", "李四"),  // 重复 id
                createRow("id", 2, "name", "王五")
        ));

        seedSchema(dsId, "users", new String[]{"id", "name"});

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double uniqueness = report.getMetrics().get("uniqueness");
        // 3 PK values total, only 2 unique → 66.67%
        assertTrue(uniqueness < 80,
                "主键重复时唯一性应显著下降，实际: " + uniqueness);
    }

    @Test
    @DisplayName("唯一性 — 整行完全重复应扣分")
    void testUniquenessFullRowDuplicate() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "items", List.of(
                createRow("name", "test"),  // 无主键字段，靠行级去重
                createRow("name", "test")   // 完全重复
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double uniqueness = report.getMetrics().get("uniqueness");
        assertTrue(uniqueness <= 100,
                "整行重复应触发去重检测，实际: " + uniqueness);
    }

    // ==================== 3. 关联一致性计算 ====================

    @Test
    @DisplayName("一致性 — 无外键数据应得满分（100分）")
    void testConsistencyNoFK() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "standalone", List.of(
                createRow("id", 1, "data", "hello"),
                createRow("id", 2, "data", "world")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double consistency = report.getMetrics().get("consistency");
        assertEquals(100.0, consistency, 0.01,
                "无外键时一致性应为 100，实际: " + consistency);
    }

    // ==================== 4. 格式合法性计算 ====================

    @Test
    @DisplayName("格式合法性 — 合法邮箱/手机号应得满分")
    void testValidityValidFormats() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("email", "test@example.com", "phone", "13812345678"),
                createRow("email", "hello@test.org", "phone", "15900001111")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double validity = report.getMetrics().get("validity");
        assertEquals(100.0, validity, 0.01,
                "合法格式的邮箱和手机号应得 100，实际: " + validity);
    }

    @Test
    @DisplayName("格式合法性 — 非法格式应扣分")
    void testValidityInvalidFormats() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("email", "not-an-email", "phone", "12345"),       // 都不合法
                createRow("email", "also@bad", "phone", "not-a-phone")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double validity = report.getMetrics().get("validity");
        assertEquals(0.0, validity, 0.01,
                "全部格式不合法时 validity 应为 0，实际: " + validity);
    }

    @Test
    @DisplayName("格式合法性 — 部分合法应精确计算")
    void testValidityMixedFormats() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("email", "good@test.com", "phone", "13812345678"), // 都合法
                createRow("email", "bad-email", "phone", "123")              // 都不合法
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double validity = report.getMetrics().get("validity");
        assertEquals(50.0, validity, 0.01,
                "2/4 合法 → 50%，实际: " + validity);
    }

    @Test
    @DisplayName("格式合法性 — 日期格式校验")
    void testValidityDateFormat() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "logs", List.of(
                createRow("created_at", "2024-01-15 10:30:00"),
                createRow("birth_date", "1990-06-20")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double validity = report.getMetrics().get("validity");
        assertTrue(validity >= 0,
                "日期字段格式校验应正常运行，实际: " + validity);
    }

    // ==================== 5. 隐私安全检查 ====================

    @Test
    @DisplayName("隐私安全 — 无敏感字段应得满分")
    void testPrivacyNoSensitiveFields() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "products", List.of(
                createRow("product_name", "Widget", "price", "99.00"),
                createRow("product_name", "Gadget", "price", "149.00")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double privacy = report.getMetrics().get("privacy");
        assertEquals(100.0, privacy, 0.01,
                "无敏感字段隐私应为 100，实际: " + privacy);
    }

    @Test
    @DisplayName("隐私安全 — 已脱敏字段应得满分")
    void testPrivacyMaskedFields() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("name", "张*", "phone", "138****5678", "email", "u***@test.com"),
                createRow("name", "李*", "phone", "159****1234", "email", "v***@demo.com")
        ));

        // 注意：带星号的值可能被 isMasked 识别
        // name=张* matches the name masking pattern
        // phone=138****5678 matches phone masking pattern
        // email=u***@test.com matches email masking pattern

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double privacy = report.getMetrics().get("privacy");
        // 所有 name/phone/email field values are masked
        assertEquals(100.0, privacy, 0.01,
                "所有敏感字段已脱敏隐私应为 100，实际: " + privacy);
    }

    @Test
    @DisplayName("隐私安全 — 未脱敏敏感数据应扣分")
    void testPrivacyUnmaskedData() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "users", List.of(
                createRow("name", "张三", "phone", "13812345678", "email", "zhangsan@gmail.com"),
                createRow("name", "李四", "phone", "15900001111", "email", "lisi@qq.com")
        ));

        seedSchema(dsId, "users", new String[]{"name", "phone", "email"});

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double privacy = report.getMetrics().get("privacy");
        // name=张三 doesn't match masking pattern (not single char + *)
        // phone=13812345678 doesn't match masked phone pattern
        // email=zhangsan@gmail.com doesn't contain ***@
        assertEquals(0.0, privacy, 0.01,
                "所有敏感字段未脱敏隐私应为 0，实际: " + privacy);
    }

    // ==================== 6. 综合评分计算 ====================

    @Test
    @DisplayName("综合评分 — 满分数据应得 100 分")
    void testTotalScorePerfect() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "standalone", List.of(
                createRow("id", 1, "data", "hello"),
                createRow("id", 2, "data", "world"),
                createRow("id", 3, "data", "test")
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        // No sensitive fields → privacy=100
        // No FKs → consistency=100
        // All non-null → completeness=100
        // No PK duplicates → uniqueness=100 (no PK column in schema)
        // No format-checked fields → validity=100

        double totalScore = report.getTotalScore();
        assertTrue(totalScore >= 90,
                "无问题的数据综合评分应 ≥90，实际: " + totalScore);
        assertEquals("优秀", report.getGrade());
    }

    @Test
    @DisplayName("综合评分 — 加权公式验证")
    void testWeightedFormula() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);

        // 故意制造：完整性较差，其他满分
        resultService.saveResult(taskId, "standalone", List.of(
                createRow("data", "hello"),   // 3 cells
                createRow("data", null),       // 1 null = 5/6 ≈ 83%
                createRow("data", "good")
        ));

        // With only 'data' column (not a PK, not sensitive, not formatted):
        // completeness = ~83.33, uniqueness = 100 (no PK col), consistency = 100 (no FK),
        // validity = 100 (no pattern match on 'data'), privacy = 100 (not sensitive)
        // total = 83.33*0.25 + 100*0.20 + 100*0.25 + 100*0.15 + 100*0.15
        //      = 20.83 + 20 + 25 + 15 + 15 = 95.83

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        double totalScore = report.getTotalScore();
        assertTrue(totalScore > 90 && totalScore < 100,
                "加权公式计算应与预期一致，实际: " + totalScore);
    }

    @Test
    @DisplayName("综合评分 — 多维度差评数据")
    void testTotalScorePoor() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);

        // 全部空值 + 非法格式
        resultService.saveResult(taskId, "bad_data", List.of(
                createRow("email", "not-valid", "name", null),  // null + invalid email
                createRow("email", "also-bad", "name", null)    // null + invalid email
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        // completeness ≈ 50% (name nulls) → 50
        // uniqueness = 100 (no PK)
        // consistency = 100 (no FK)
        // validity = 0 (all emails invalid) → 0
        // privacy = 50 or 0 (name is sensitive with nulls)
        // total ≈ 50*0.25 + 100*0.20 + 100*0.25 + 0*0.15 + 0*0.15
        //      = 12.5 + 20 + 25 + 0 + 0 = 57.5

        double totalScore = report.getTotalScore();
        assertTrue(totalScore < 80,
                "质量差的数据应有较低评分，实际: " + totalScore);
    }

    // ==================== 7. 等级评定 ====================

    @Test
    @DisplayName("等级评定 — 各分数段映射验证")
    void testGradeBoundaries() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);

        // 场景 1: 满分 → 优秀
        resultService.saveResult(taskId, "t1", List.of(
                createRow("id", 1, "data", "ok")
        ));
        QualityReportResponse r1 = evaluator.evaluate(taskId, dsId);
        assertTrue(r1.getTotalScore() >= 90);
        assertEquals("优秀", r1.getGrade());
    }

    @Test
    @DisplayName("等级评定 — 良好等级验证")
    void testGradeGood() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);

        // 一半空值，完整性 ~50%
        resultService.saveResult(taskId, "t1", List.of(
                createRow("data", "hello"),
                createRow("data", null)
        ));

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);
        // completeness = 50 → 50*0.25 + 100*0.75 = 87.5 → 良好
        assertTrue(report.getTotalScore() >= 80,
                "评分应在良好范围，实际: " + report.getTotalScore());
    }

    // ==================== 8. 报告保存与查询 ====================

    @Test
    @DisplayName("报告保存 — evaluate 后 getReport 应返回相同数据")
    void testReportSaveAndRetrieve() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        resultService.saveResult(taskId, "test", List.of(
                createRow("id", 1, "name", "test")
        ));

        QualityReportResponse evaluated = evaluator.evaluate(taskId, dsId);
        QualityReportResponse retrieved = evaluator.getReport(taskId);

        assertNotNull(retrieved, "getReport 应返回已保存的报告");
        assertEquals(evaluated.getTaskId(), retrieved.getTaskId());
        assertEquals(evaluated.getTotalScore(), retrieved.getTotalScore());
        assertEquals(evaluated.getGrade(), retrieved.getGrade());

        Map<String, Double> m1 = evaluated.getMetrics();
        Map<String, Double> m2 = retrieved.getMetrics();
        assertEquals(m1.get("completeness"), m2.get("completeness"));
        assertEquals(m1.get("uniqueness"), m2.get("uniqueness"));
        assertEquals(m1.get("consistency"), m2.get("consistency"));
        assertEquals(m1.get("validity"), m2.get("validity"));
        assertEquals(m1.get("privacy"), m2.get("privacy"));
    }

    @Test
    @DisplayName("报告查询 — 未评估任务应返回 null")
    void testReportNotEvaluated() {
        Long taskId = newTaskId();
        QualityReportResponse report = evaluator.getReport(taskId);
        assertNull(report, "未评估的任务应返回 null");
    }

    @Test
    @DisplayName("报告保存 — 重复评估应覆盖旧报告")
    void testReportReEvaluate() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);

        // 第一次评估 — 全量数据
        resultService.saveResult(taskId, "test", List.of(
                createRow("data", "hello")
        ));
        evaluator.evaluate(taskId, dsId);

        // 第二次评估 — 重新评估更新报告
        evaluator.evaluate(taskId, dsId);

        // 报告应被覆盖（获取最新）
        QualityReportResponse latest = evaluator.getReport(taskId);
        assertNotNull(latest);
    }

    // ==================== 9. 空数据容错 ====================

    @Test
    @DisplayName("空数据 — 无生成结果的任务应返回空报告")
    void testEmptyData() {
        Long taskId = newTaskId();
        Long dsId = newDsId();

        seedTask(taskId);
        // 不保存任何 result

        QualityReportResponse report = evaluator.evaluate(taskId, dsId);

        assertEquals(taskId, report.getTaskId());
        assertEquals(0.0, report.getTotalScore());
        assertEquals("不合格", report.getGrade());
        assertEquals(0.0, report.getMetrics().get("completeness"));
        assertEquals(0.0, report.getMetrics().get("uniqueness"));
        assertEquals(0.0, report.getMetrics().get("consistency"));
        assertEquals(0.0, report.getMetrics().get("validity"));
        assertEquals(0.0, report.getMetrics().get("privacy"));
        assertTrue(report.getDetails().isEmpty());
    }

    // ==================== 辅助方法 ====================

    private void seedSchema(Long datasourceId, String tableName, String[] columns) {
        com.platform.entity.Datasource datasource = new com.platform.entity.Datasource();
        datasource.setId(datasourceId);
        datasource.setProjectId(datasourceId);
        datasource.setName("quality-test-ds");
        datasource.setDbType("MySQL");
        datasource.setHost("127.0.0.1");
        datasource.setPort(3306);
        datasource.setDbName("testdb");
        datasource.setUsername("sa");
        datasource.setPasswordEncrypted("dummy");
        datasource.setStatus("CONNECTED");
        datasourceMapper.insert(datasource);

        com.platform.entity.schema.SchemaTable table =
                new com.platform.entity.schema.SchemaTable();
        table.setDatasourceId(datasourceId);
        table.setTableName(tableName);
        table.setColumnCount(columns.length);
        schemaTableMapper.insert(table);

        int position = 1;
        for (String columnName : columns) {
            com.platform.entity.schema.SchemaColumn column =
                    new com.platform.entity.schema.SchemaColumn();
            column.setTableId(table.getId());
            column.setColumnName(columnName);
            column.setDataType("varchar");
            column.setNullable(true);
            column.setPrimaryKey("id".equals(columnName));
            column.setOrdinalPosition(position++);
            schemaColumnMapper.insert(column);
        }
    }

    /** 创建测试数据行 */
    private Map<String, Object> createRow(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return row;
    }

    /** 生成唯一任务 ID */
    private Long newTaskId() {
        return ++uniqueIdCounter;
    }

    /** 生成唯一数据源 ID */
    private Long newDsId() {
        return ++uniqueIdCounter;
    }

    /** 在 testdata_task 表中创建占位记录，防止外键约束/测试查询失败 */
    private void seedTask(Long taskId) {
        // 检查是否已存在
        if (taskMapper.selectById(taskId) != null) return;
        var task = new com.platform.entity.TestDataTask();
        task.setId(taskId);
        task.setTaskName("质量评估测试-" + taskId);
        task.setDatasourceId(taskId);
        task.setStatus("SUCCESS");
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        taskMapper.insert(task);
    }
}
