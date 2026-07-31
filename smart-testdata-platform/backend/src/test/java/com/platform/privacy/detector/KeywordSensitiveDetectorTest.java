package com.platform.privacy.detector;

import com.platform.dto.SensitiveField;
import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldDetector;
import com.platform.privacy.SensitiveFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键词敏感字段检测器测试
 *
 * <p>验证 KeywordSensitiveDetector 正确委托给现有 SensitiveFieldDetector，
 * 并将其输出适配为 DetectionResult 格式。</p>
 */
@DisplayName("关键词检测器测试")
class KeywordSensitiveDetectorTest {

    private KeywordSensitiveDetector detector;

    @BeforeEach
    void setUp() {
        detector = new KeywordSensitiveDetector(new SensitiveFieldDetector());
    }

    // ==================== 1. 基础识别 ====================

    @Test
    @DisplayName("phone 字段应识别为 PHONE")
    void testDetectPhone() {
        DetectionContext ctx = contextOf(column("phone"), column("email"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(2, results.size());
        assertEquals(SensitiveFieldType.PHONE, findByColumn(results, "phone").getType());
        assertEquals(DetectionSource.KEYWORD, findByColumn(results, "phone").getSource());
        assertEquals(0.95, findByColumn(results, "phone").getConfidence(), 0.01);
    }

    @Test
    @DisplayName("email 字段应识别为 EMAIL")
    void testDetectEmail() {
        DetectionContext ctx = contextOf(column("email"), column("user_email"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(2, results.size());
        assertEquals(SensitiveFieldType.EMAIL, findByColumn(results, "email").getType());
        assertEquals(0.95, findByColumn(results, "email").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "user_email").getConfidence(), 0.01);
    }

    @Test
    @DisplayName("id_card 字段应识别为 ID_CARD")
    void testDetectIdCard() {
        DetectionContext ctx = contextOf(column("id_card"), column("idcard"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(2, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.ID_CARD, r.getType()));
    }

    @Test
    @DisplayName("name 字段应识别为 NAME")
    void testDetectName() {
        DetectionContext ctx = contextOf(column("name"), column("username"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(2, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.NAME, r.getType()));
    }

    @Test
    @DisplayName("address 字段应识别为 ADDRESS")
    void testDetectAddress() {
        DetectionContext ctx = contextOf(column("address"), column("addr"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(2, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.ADDRESS, r.getType()));
    }

    @Test
    @DisplayName("bank 字段应识别为 BANK_CARD")
    void testDetectBankCard() {
        DetectionContext ctx = contextOf(column("bank"), column("credit_card"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(2, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.BANK_CARD, r.getType()));
    }

    // ==================== 2. 大小写不敏感 ====================

    @Test
    @DisplayName("大小写变体应正确识别")
    void testCaseInsensitive() {
        DetectionContext ctx = contextOf(
                column("Phone"), column("EMAIL"), column("Mobile"));

        List<DetectionResult> results = detector.detect(ctx);

        assertEquals(3, results.size());
        assertEquals(SensitiveFieldType.PHONE, findByColumn(results, "Phone").getType());
        assertEquals(SensitiveFieldType.EMAIL, findByColumn(results, "EMAIL").getType());
        assertEquals(SensitiveFieldType.PHONE, findByColumn(results, "Mobile").getType());
    }

    // ==================== 3. 无效输入 ====================

    @Test
    @DisplayName("不匹配的字段不应返回")
    void testUnknownFieldsIgnored() {
        DetectionContext ctx = contextOf(
                column("age"), column("status"), column("created_at"));

        List<DetectionResult> results = detector.detect(ctx);

        assertTrue(results.isEmpty(), "未匹配的字段不应出现在结果中");
    }

    @Test
    @DisplayName("null 上下文应返回空列表")
    void testNullContextReturnsEmpty() {
        List<DetectionResult> results = detector.detect(null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("空列表应返回空结果")
    void testEmptyColumnsReturnsEmpty() {
        DetectionContext ctx = contextOf();
        List<DetectionResult> results = detector.detect(ctx);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("所有结果的 source 都应为 KEYWORD")
    void testAllResultsSourceIsKeyword() {
        DetectionContext ctx = contextOf(column("phone"), column("email"), column("name"));

        List<DetectionResult> results = detector.detect(ctx);

        results.forEach(r ->
                assertEquals(DetectionSource.KEYWORD, r.getSource(),
                        r.getColumnName() + " 的 source 应为 KEYWORD"));
    }

    // ==================== 辅助方法 ====================

    private static SchemaColumn column(String name) {
        SchemaColumn col = new SchemaColumn();
        col.setColumnName(name);
        return col;
    }

    private static DetectionContext contextOf(SchemaColumn... columns) {
        return new DetectionContext(List.of(columns), null);
    }

    private static DetectionResult findByColumn(List<DetectionResult> results, String columnName) {
        return results.stream()
                .filter(r -> columnName.equals(r.getColumnName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("结果中未找到字段: " + columnName));
    }
}
