package com.platform.privacy;

import com.platform.dto.SensitiveField;
import com.platform.entity.schema.SchemaColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 敏感字段识别器测试
 *
 * <p>验证基于字段名的关键词匹配识别，大小写不敏感，支持下划线命名。</p>
 */
@DisplayName("敏感字段识别器测试")
class SensitiveFieldDetectorTest {

    private SensitiveFieldDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SensitiveFieldDetector();
    }

    // ==================== 1. PHONE 识别 ====================

    @Test
    @DisplayName("phone/mobile/tel 字段应识别为 PHONE")
    void testDetectPhone() {
        List<SchemaColumn> columns = List.of(
                column("phone"),
                column("mobile"),
                column("tel"),
                column("user_phone"),
                column("phone_number"),
                column("contact_mobile")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(6, results.size(), "所有 phone 相关字段都应被识别");
        results.forEach(r -> assertEquals(SensitiveFieldType.PHONE, r.getType(),
                r.getColumnName() + " 应为 PHONE"));

        // 精确匹配
        assertEquals(0.95, findByColumn(results, "phone").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "mobile").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "tel").getConfidence(), 0.01);

        // 包含匹配
        assertEquals(0.80, findByColumn(results, "user_phone").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "phone_number").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "contact_mobile").getConfidence(), 0.01);
    }

    @Test
    @DisplayName("大小写变体 — phone 字段应大小写不敏感识别")
    void testPhoneCaseInsensitive() {
        List<SchemaColumn> columns = List.of(
                column("Phone"),
                column("PHONE"),
                column("Mobile")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(3, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.PHONE, r.getType()));
    }

    // ==================== 2. EMAIL 识别 ====================

    @Test
    @DisplayName("email/mail 字段应识别为 EMAIL")
    void testDetectEmail() {
        List<SchemaColumn> columns = List.of(
                column("email"),
                column("mail"),
                column("user_email"),
                column("contact_mail")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(4, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.EMAIL, r.getType()));

        assertEquals(0.95, findByColumn(results, "email").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "mail").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "user_email").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "contact_mail").getConfidence(), 0.01);
    }

    // ==================== 3. ID_CARD 识别 ====================

    @Test
    @DisplayName("id_card/card_no/idcard 字段应识别为 ID_CARD")
    void testDetectIdCard() {
        List<SchemaColumn> columns = List.of(
                column("id_card"),
                column("card_no"),
                column("idcard"),
                column("user_id_card")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(4, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.ID_CARD, r.getType()));

        assertEquals(0.95, findByColumn(results, "id_card").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "card_no").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "idcard").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "user_id_card").getConfidence(), 0.01);
    }

    @Test
    @DisplayName("card_no 应识别为 ID_CARD 而非 BANK_CARD")
    void testCardNoIsIdCardNotBankCard() {
        List<SchemaColumn> columns = List.of(column("card_no"));

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(1, results.size());
        assertEquals(SensitiveFieldType.ID_CARD, results.get(0).getType(),
                "card_no 应优先匹配 ID_CARD 而非 BANK_CARD");
    }

    // ==================== 4. NAME 识别 ====================

    @Test
    @DisplayName("name/username 字段应识别为 NAME")
    void testDetectName() {
        List<SchemaColumn> columns = List.of(
                column("name"),
                column("username"),
                column("user_name"),
                column("full_name")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(4, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.NAME, r.getType()));

        assertEquals(0.95, findByColumn(results, "name").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "username").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "user_name").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "full_name").getConfidence(), 0.01);
    }

    // ==================== 5. ADDRESS 识别 ====================

    @Test
    @DisplayName("address/addr/location 字段应识别为 ADDRESS")
    void testDetectAddress() {
        List<SchemaColumn> columns = List.of(
                column("address"),
                column("addr"),
                column("location"),
                column("home_address"),
                column("ip_location")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(5, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.ADDRESS, r.getType()));

        assertEquals(0.95, findByColumn(results, "address").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "addr").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "location").getConfidence(), 0.01);
    }

    // ==================== 6. BANK_CARD 识别 ====================

    @Test
    @DisplayName("bank/card 字段应识别为 BANK_CARD")
    void testDetectBankCard() {
        List<SchemaColumn> columns = List.of(
                column("bank"),
                column("card"),
                column("bank_card"),
                column("credit_card")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(4, results.size());
        results.forEach(r -> assertEquals(SensitiveFieldType.BANK_CARD, r.getType()));

        assertEquals(0.95, findByColumn(results, "bank").getConfidence(), 0.01);
        assertEquals(0.95, findByColumn(results, "card").getConfidence(), 0.01);
        assertEquals(0.80, findByColumn(results, "bank_card").getConfidence(), 0.01);
    }

    // ==================== 7. 未知字段忽略 ====================

    @Test
    @DisplayName("不匹配的字段不应返回 — age, status, created_at")
    void testUnknownFieldsIgnored() {
        List<SchemaColumn> columns = List.of(
                column("age"),
                column("status"),
                column("created_at"),
                column("amount"),
                column("description")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertTrue(results.isEmpty(),
                "未匹配的字段不应出现在结果中，实际返回: " + results);
    }

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("空列表应返回空结果")
    void testEmptyListReturnsEmpty() {
        List<SensitiveField> results = detector.detect(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("null 输入应返回空结果")
    void testNullInputReturnsEmpty() {
        List<SensitiveField> results = detector.detect((List<SchemaColumn>) null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("混合字段 — 正确识别敏感字段并忽略普通字段")
    void testMixedFields() {
        List<SchemaColumn> columns = List.of(
                column("id"),
                column("phone"),
                column("age"),
                column("email"),
                column("created_at"),
                column("address")
        );

        List<SensitiveField> results = detector.detect(columns);

        assertEquals(3, results.size(), "phone, email, address 三个应被识别");
        assertEquals(SensitiveFieldType.PHONE, findByColumn(results, "phone").getType());
        assertEquals(SensitiveFieldType.EMAIL, findByColumn(results, "email").getType());
        assertEquals(SensitiveFieldType.ADDRESS, findByColumn(results, "address").getType());
    }

    // ==================== 辅助方法 ====================

    private static SchemaColumn column(String name) {
        SchemaColumn col = new SchemaColumn();
        col.setColumnName(name);
        return col;
    }

    private static SensitiveField findByColumn(List<SensitiveField> results, String columnName) {
        return results.stream()
                .filter(r -> columnName.equals(r.getColumnName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "结果中未找到字段: " + columnName));
    }
}
