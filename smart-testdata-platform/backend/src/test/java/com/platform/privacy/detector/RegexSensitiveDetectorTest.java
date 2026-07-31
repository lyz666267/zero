package com.platform.privacy.detector;

import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 正则表达式敏感字段检测器测试
 *
 * <p>验证 RegexSensitiveDetector 基于实际数据值的格式匹配能力。</p>
 */
@DisplayName("正则检测器测试")
class RegexSensitiveDetectorTest {

    private RegexSensitiveDetector detector;

    @BeforeEach
    void setUp() {
        detector = new RegexSensitiveDetector();
    }

    // ==================== 1. 手机号检测 ====================

    @Test
    @DisplayName("手机号值应识别为 PHONE — 13812345678")
    void testPhoneValueDetected() {
        DetectionContext ctx = contextWithSamples(
                column("phone", "varchar"),
                List.of("13812345678", "13900001111"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        DetectionResult r = results.get(0);
        assertEquals("phone", r.getColumnName());
        assertEquals(SensitiveFieldType.PHONE, r.getType());
        assertEquals(DetectionSource.REGEX, r.getSource());
        assertTrue(r.getConfidence() > 0.90, "confidence 应接近 0.98: " + r.getConfidence());
        assertNotNull(r.getMatchedValue());
    }

    @Test
    @DisplayName("非手机号值不应匹配 — 12345678901")
    void testNonPhoneNotDetected() {
        // 不以 1[3-9] 开头
        DetectionContext ctx = contextWithSamples(
                column("phone", "varchar"),
                List.of("12345678901"));

        List<DetectionResult> results = detector.detect(ctx);

        assertTrue(results.isEmpty(), "非手机号格式不应被误判");
    }

    // ==================== 2. 邮箱检测 ====================

    @Test
    @DisplayName("邮箱值应识别为 EMAIL")
    void testEmailValueDetected() {
        DetectionContext ctx = contextWithSamples(
                column("email", "varchar"),
                List.of("test@example.com", "user@test.org"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        assertEquals(SensitiveFieldType.EMAIL, results.get(0).getType());
        assertEquals(DetectionSource.REGEX, results.get(0).getSource());
        assertTrue(results.get(0).getConfidence() > 0.90);
    }

    @Test
    @DisplayName("复杂邮箱格式应识别 — user.name+tag@domain.co.uk")
    void testComplexEmailDetected() {
        DetectionContext ctx = contextWithSamples(
                column("email", "varchar"),
                List.of("user.name+tag@domain.co.uk"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        assertEquals(SensitiveFieldType.EMAIL, results.get(0).getType());
    }

    @Test
    @DisplayName("非邮箱值不应匹配 — not-an-email")
    void testNonEmailNotDetected() {
        DetectionContext ctx = contextWithSamples(
                column("email", "varchar"),
                List.of("not-an-email"));

        List<DetectionResult> results = detector.detect(ctx);

        assertTrue(results.isEmpty(), "非邮箱格式不应被误判");
    }

    // ==================== 3. 身份证检测 ====================

    @Test
    @DisplayName("身份证值应识别为 ID_CARD")
    void testIdCardValueDetected() {
        DetectionContext ctx = contextWithSamples(
                column("id_card", "varchar"),
                List.of("110101199001011234"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        assertEquals(SensitiveFieldType.ID_CARD, results.get(0).getType());
        assertEquals(DetectionSource.REGEX, results.get(0).getSource());
    }

    @Test
    @DisplayName("末位为 X 的身份证应识别")
    void testIdCardWithXDetected() {
        DetectionContext ctx = contextWithSamples(
                column("id_card", "varchar"),
                List.of("11010119900101123X"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        assertEquals(SensitiveFieldType.ID_CARD, results.get(0).getType());
    }

    // ==================== 4. 银行卡检测 ====================

    @Test
    @DisplayName("银行卡值应识别为 BANK_CARD — 19位数字")
    void testBankCardValueDetected() {
        DetectionContext ctx = contextWithSamples(
                column("bank_card", "varchar"),
                List.of("6222021234567890123"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        assertEquals(SensitiveFieldType.BANK_CARD, results.get(0).getType());
        assertEquals(DetectionSource.REGEX, results.get(0).getSource());
    }

    @Test
    @DisplayName("短数字不应识别为银行卡 — 1234")
    void testShortNumberNotDetectedAsBankCard() {
        DetectionContext ctx = contextWithSamples(
                column("card", "varchar"),
                List.of("1234"));

        List<DetectionResult> results = detector.detect(ctx);

        assertTrue(results.isEmpty(), "少于16位数字不应被识别为银行卡");
    }

    // ==================== 5. 部分匹配 ====================

    @Test
    @DisplayName("部分样本匹配时置信度应降低")
    void testPartialMatchLowersConfidence() {
        // 3 个手机号 + 1 个非手机号
        DetectionContext ctx = contextWithSamples(
                column("phone", "varchar"),
                List.of("13812345678", "13900001111", "13600002222", "not-a-phone"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        double confidence = results.get(0).getConfidence();
        assertTrue(confidence < 0.98, "部分匹配时置信度应降低: " + confidence);
        assertTrue(confidence > 0.80, "75%匹配应有足够置信度: " + confidence);
    }

    // ==================== 6. 数字类型跳过 ====================

    @Test
    @DisplayName("INT 类型字段跳过电话/身份证/银行卡检测")
    void testNumericTypeSkipped() {
        // 18位整数字段不应判为身份证或银行卡
        DetectionContext ctx = contextWithSamples(
                column("user_id", "int"),
                List.of("110101199001011234"));

        List<DetectionResult> results = detector.detect(ctx);

        assertTrue(results.isEmpty(), "INT 类型不应被正则检测为敏感字段");
    }

    @Test
    @DisplayName("BIGINT 类型字段跳过敏感检测")
    void testBigintTypeSkipped() {
        DetectionContext ctx = contextWithSamples(
                column("transaction_id", "bigint"),
                List.of("6222021234567890123"));

        List<DetectionResult> results = detector.detect(ctx);

        assertTrue(results.isEmpty(), "BIGINT 类型不应被检测为银行卡");
    }

    // ==================== 7. 边界条件 ====================

    @Test
    @DisplayName("空样本数据返回空结果")
    void testNullSampleDataReturnsEmpty() {
        DetectionContext ctx = new DetectionContext(
                List.of(column("phone", "varchar")), null);

        List<DetectionResult> results = detector.detect(ctx);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("null 上下文返回空列表")
    void testNullContextReturnsEmpty() {
        List<DetectionResult> results = detector.detect(null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("matchedValue 记录第一个匹配值")
    void testMatchedValueRecorded() {
        DetectionContext ctx = contextWithSamples(
                column("email", "varchar"),
                List.of("first@test.com", "second@test.com"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(1, results.size());
        assertEquals("first@test.com", results.get(0).getMatchedValue());
    }

    // ==================== 辅助方法 ====================

    private static SchemaColumn column(String name, String dataType) {
        SchemaColumn col = new SchemaColumn();
        col.setColumnName(name);
        col.setDataType(dataType);
        return col;
    }

    private static DetectionContext contextWithSamples(SchemaColumn col, List<String> values) {
        List<Map<String, Object>> sampleData = values.stream()
                .map(v -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put(col.getColumnName(), v);
                    return row;
                })
                .toList();
        return new DetectionContext(List.of(col), sampleData);
    }
}
