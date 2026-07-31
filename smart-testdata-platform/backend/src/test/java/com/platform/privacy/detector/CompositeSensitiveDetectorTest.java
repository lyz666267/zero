package com.platform.privacy.detector;

import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldDetector;
import com.platform.privacy.SensitiveFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组合敏感字段检测器测试
 *
 * <p>验证 CompositeSensitiveDetector 的三层融合策略：
 * Regex &gt; Keyword &gt; LLM 优先级，以及容错行为。</p>
 */
@DisplayName("组合检测器测试")
class CompositeSensitiveDetectorTest {

    private CompositeSensitiveDetector composite;
    private RegexSensitiveDetector regexDetector;
    private KeywordSensitiveDetector keywordDetector;
    private LLMSensitiveDetector llmDetector;

    @BeforeEach
    void setUp() {
        regexDetector = new RegexSensitiveDetector();
        keywordDetector = new KeywordSensitiveDetector(new SensitiveFieldDetector());
        llmDetector = new LLMSensitiveDetector(new RestTemplate());
        llmDetector.aiServiceUrl = "http://localhost:19999"; // 确保 LLM 降级

        composite = new CompositeSensitiveDetector(regexDetector, keywordDetector, llmDetector);
    }

    // ==================== 1. Regex 优先于 Keyword ====================

    @Test
    @DisplayName("Regex 和 Keyword 都匹配同一字段 → Regex 优先")
    void testRegexOverridesKeyword() {
        // phone 字段：keyword 匹配到 PHONE (confidence=0.95), regex 也匹配 (confidence=0.98)
        SchemaColumn col = column("phone", "varchar");
        List<Map<String, Object>> samples = List.of(
                Map.of("phone", (Object) "13812345678")
        );
        DetectionContext ctx = new DetectionContext(List.of(col), samples);

        List<DetectionResult> results = composite.detect(ctx);

        assertEquals(1, results.size());
        DetectionResult r = results.get(0);
        assertEquals(SensitiveFieldType.PHONE, r.getType());
        assertEquals(DetectionSource.REGEX, r.getSource(),
                "Regex 应覆盖 Keyword，但实际来源是: " + r.getSource());
        assertTrue(r.getConfidence() > 0.90, "应使用 Regex 的置信度");
    }

    // ==================== 2. Keyword 补充 Regex 空白 ====================

    @Test
    @DisplayName("Regex 未覆盖的字段由 Keyword 补充")
    void testKeywordFillsRegexGap() {
        // name 字段：无样本数据 → regex 无法检测 → keyword 应识别 NAME
        SchemaColumn col = column("name", "varchar");
        DetectionContext ctx = new DetectionContext(List.of(col), null);

        List<DetectionResult> results = composite.detect(ctx);

        assertEquals(1, results.size());
        DetectionResult r = results.get(0);
        assertEquals(SensitiveFieldType.NAME, r.getType());
        assertEquals(DetectionSource.KEYWORD, r.getSource(),
                "Regex 无数据时 Keyword 应补充，但实际来源是: " + r.getSource());
    }

    // ==================== 3. 三层融合：不同字段由不同层检测 ====================

    @Test
    @DisplayName("不同字段被不同检测器识别 → 全部在融合结果中")
    void testMultiLayerFusion() {
        // phone + email: 有样本值 → regex 检测
        // address: 无样本值但有字段名 → keyword 检测
        List<SchemaColumn> columns = List.of(
                column("phone", "varchar"),
                column("email", "varchar"),
                column("address", "varchar")
        );
        List<Map<String, Object>> samples = List.of(
                createRow("phone", "13812345678", "email", "test@example.com", "address", "北京市")
        );
        DetectionContext ctx = new DetectionContext(columns, samples);

        List<DetectionResult> results = composite.detect(ctx);

        assertTrue(results.size() >= 2, "至少应有 phone + email 被检测: " + results.size());
        // phone 应由 regex 检测
        DetectionResult phoneResult = findByColumn(results, "phone");
        assertNotNull(phoneResult);
        assertEquals(DetectionSource.REGEX, phoneResult.getSource());
        // email 应由 regex 检测
        DetectionResult emailResult = findByColumn(results, "email");
        assertNotNull(emailResult);
        assertEquals(DetectionSource.REGEX, emailResult.getSource());
        // address 应由 keyword 检测
        DetectionResult addrResult = findByColumn(results, "address");
        assertNotNull(addrResult);
    }

    // ==================== 4. 容错 ====================

    @Test
    @DisplayName("LLM 失败 → Keyword + Regex 仍正常工作")
    void testLlmFailureDoesNotAffectOthers() {
        // LLM 不可用（端口 19999），但 keyword + regex 应正常
        SchemaColumn col = column("phone", "varchar");
        List<Map<String, Object>> samples = List.of(
                Map.of("phone", (Object) "13812345678")
        );
        DetectionContext ctx = new DetectionContext(List.of(col), samples);

        List<DetectionResult> results = composite.detect(ctx);

        assertEquals(1, results.size());
        assertNotNull(findByColumn(results, "phone"));
    }

    @Test
    @DisplayName("所有检测器都无结果 → 返回空列表")
    void testAllDetectorsEmptyReturnsEmpty() {
        List<SchemaColumn> columns = List.of(
                column("age", "int"),
                column("status", "varchar")
        );
        DetectionContext ctx = new DetectionContext(columns, null);

        List<DetectionResult> results = composite.detect(ctx);

        assertTrue(results.isEmpty(), "无敏感字段时应返回空列表");
    }

    // ==================== 5. 边界条件 ====================

    @Test
    @DisplayName("null 上下文返回空列表")
    void testNullContextReturnsEmpty() {
        List<DetectionResult> results = composite.detect(null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("空列列表返回空列表")
    void testEmptyColumnsReturnsEmpty() {
        DetectionContext ctx = new DetectionContext(List.of(), null);
        List<DetectionResult> results = composite.detect(ctx);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("null 列列表返回空列表")
    void testNullColumnsReturnsEmpty() {
        DetectionContext ctx = new DetectionContext(null, null);
        List<DetectionResult> results = composite.detect(ctx);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Regex + Keyword 同字段 — Regex 置信度更高应保留")
    void testHigherConfidenceFromRegex() {
        SchemaColumn col = column("mobile", "varchar");
        List<Map<String, Object>> samples = List.of(
                Map.of("mobile", (Object) "13812345678")
        );
        DetectionContext ctx = new DetectionContext(List.of(col), samples);

        List<DetectionResult> results = composite.detect(ctx);

        assertEquals(1, results.size());
        DetectionResult r = results.get(0);
        // Regex 置信度 > Keyword 置信度
        assertTrue(r.getConfidence() > 0.90,
                "Regex 置信度应高于 Keyword: " + r.getConfidence());
        assertEquals(DetectionSource.REGEX, r.getSource());
    }

    @Test
    @DisplayName("Regex 从值检测到敏感但 Keyword 无匹配 → 仍返回 Regex 结果")
    void testRegexDetectsWhenKeywordMisses() {
        // 列名为 "contact_info"，keyword 无法识别，但值包含手机号 → regex 应检测到
        SchemaColumn col = column("contact_info", "varchar");
        List<Map<String, Object>> samples = List.of(
                Map.of("contact_info", (Object) "13812345678")
        );
        DetectionContext ctx = new DetectionContext(List.of(col), samples);

        List<DetectionResult> results = composite.detect(ctx);

        assertEquals(1, results.size());
        assertEquals(SensitiveFieldType.PHONE, results.get(0).getType());
        assertEquals(DetectionSource.REGEX, results.get(0).getSource());
    }

    // ==================== 辅助方法 ====================

    private static SchemaColumn column(String name, String dataType) {
        SchemaColumn col = new SchemaColumn();
        col.setColumnName(name);
        col.setDataType(dataType);
        return col;
    }

    private static Map<String, Object> createRow(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static DetectionResult findByColumn(List<DetectionResult> results, String columnName) {
        return results.stream()
                .filter(r -> columnName.equals(r.getColumnName()))
                .findFirst()
                .orElse(null);
    }
}
