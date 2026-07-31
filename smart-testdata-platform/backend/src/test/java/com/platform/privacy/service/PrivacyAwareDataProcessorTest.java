package com.platform.privacy.service;

import com.platform.dto.PrivacyProcessRequest.SensitiveFieldInfo;
import com.platform.privacy.detector.CompositeSensitiveDetector;
import com.platform.privacy.executor.DefaultMaskExecutor;
import com.platform.privacy.mask.MaskRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 隐私感知数据处理器测试
 *
 * <p>验证脱敏融合逻辑：正确识别敏感字段、执行脱敏、保留非敏感字段。</p>
 */
@DisplayName("隐私感知数据处理器测试")
class PrivacyAwareDataProcessorTest {

    private PrivacyAwareDataProcessor processor;
    private MaskRuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaskRuleRegistry();
        registry.init(); // 手动触发 PostConstruct 逻辑
        CompositeSensitiveDetector mockDetector = mock(CompositeSensitiveDetector.class);
        processor = new PrivacyAwareDataProcessor(registry, new DefaultMaskExecutor(), mockDetector);
    }

    // ==================== 1. 手机号脱敏 ====================

    @Test
    @DisplayName("手机号字段应被脱敏")
    void testPhoneMasking() {
        List<Map<String, Object>> data = List.of(
                createRow("name", "张三", "phone", "13812345678")
        );
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("phone", "PHONE")
        );

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).get("name"), "非敏感字段应保持原值");
        assertEquals("138****5678", result.get(0).get("phone"), "手机号应被脱敏");
    }

    // ==================== 2. 邮箱脱敏 ====================

    @Test
    @DisplayName("邮箱字段应被脱敏")
    void testEmailMasking() {
        List<Map<String, Object>> data = List.of(
                createRow("name", "李四", "email", "zhangsan@gmail.com")
        );
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("email", "EMAIL")
        );

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals(1, result.size());
        assertEquals("李四", result.get(0).get("name"), "非敏感字段应保持原值");
        assertEquals("zha***@gmail.com", result.get(0).get("email"), "邮箱应被脱敏");
    }

    // ==================== 3. 普通字段不变化 ====================

    @Test
    @DisplayName("非敏感字段应保持原值不变")
    void testNormalFieldUnchanged() {
        List<Map<String, Object>> data = List.of(
                createRow("id", 1001, "name", "王五", "age", 28, "status", "active")
        );
        List<SensitiveFieldInfo> fields = List.of(); // 无敏感字段

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals(1, result.size());
        assertEquals(1001, result.get(0).get("id"));
        assertEquals("王五", result.get(0).get("name"));
        assertEquals(28, result.get(0).get("age"));
        assertEquals("active", result.get(0).get("status"));
    }

    @Test
    @DisplayName("仅标记为敏感的字段才脱敏，其余不变")
    void testOnlyMarkedFieldsMasked() {
        List<Map<String, Object>> data = List.of(
                createRow("name", "赵六", "phone", "13900001111", "email", "zhao@test.com", "age", 30)
        );
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("phone", "PHONE")
                // 只标记 phone，email 不标记
        );

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals("139****1111", result.get(0).get("phone"), "phone 应被脱敏");
        assertEquals("zhao@test.com", result.get(0).get("email"), "未标记的 email 应保持原值");
        assertEquals("赵六", result.get(0).get("name"));
        assertEquals(30, result.get(0).get("age"));
    }

    // ==================== 4. 多行数据处理 ====================

    @Test
    @DisplayName("多行数据应逐行脱敏")
    void testMultiRowProcessing() {
        List<Map<String, Object>> data = List.of(
                createRow("name", "张三", "phone", "13800000001"),
                createRow("name", "李四", "phone", "13800000002"),
                createRow("name", "王五", "phone", "13800000003")
        );
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("phone", "PHONE")
        );

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals(3, result.size());
        assertEquals("138****0001", result.get(0).get("phone"));
        assertEquals("138****0002", result.get(1).get("phone"));
        assertEquals("138****0003", result.get(2).get("phone"));

        // 姓名全部保留
        assertEquals("张三", result.get(0).get("name"));
        assertEquals("李四", result.get(1).get("name"));
        assertEquals("王五", result.get(2).get("name"));
    }

    // ==================== 5. null 值安全 ====================

    @Test
    @DisplayName("null 字段值应安全保持 null")
    void testNullValueSafety() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "测试");
        row.put("phone", null);
        row.put("email", "test@test.com");

        List<Map<String, Object>> data = List.of(row);
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("phone", "PHONE"),
                new SensitiveFieldInfo("email", "EMAIL")
        );

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals(1, result.size());
        assertNull(result.get(0).get("phone"), "null 值应保持 null");
        assertEquals("tes***@test.com", result.get(0).get("email"), "非 null 敏感字段应正常脱敏");
        assertEquals("测试", result.get(0).get("name"), "非敏感字段应保持原值");
    }

    // ==================== 6. 边界条件 ====================

    @Test
    @DisplayName("空数据列表应返回空列表")
    void testEmptyDataReturnsEmpty() {
        List<Map<String, Object>> result = processor.process(
                List.of(),
                List.of(new SensitiveFieldInfo("phone", "PHONE"))
        );
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 数据应返回空列表")
    void testNullDataReturnsEmpty() {
        List<Map<String, Object>> result = processor.process(
                null,
                List.of(new SensitiveFieldInfo("phone", "PHONE"))
        );
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("空敏感字段列表应返回原始数据")
    void testEmptySensitiveFieldsReturnsOriginal() {
        List<Map<String, Object>> data = List.of(
                createRow("phone", "13812345678")
        );

        List<Map<String, Object>> result = processor.process(data, null);

        assertEquals(1, result.size());
        assertEquals("13812345678", result.get(0).get("phone"),
                "无敏感字段时值应完全不变");
    }

    @Test
    @DisplayName("UNKNOWN 类型的字段不脱敏")
    void testUnknownTypeNotMasked() {
        List<Map<String, Object>> data = List.of(
                createRow("phone", "13812345678")
        );
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("phone", "UNKNOWN")
        );

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals("13812345678", result.get(0).get("phone"),
                "UNKNOWN 类型不应对值做任何修改");
    }

    @Test
    @DisplayName("无法解析的类型名应跳过并记录警告")
    void testInvalidTypeNameSkipped() {
        List<Map<String, Object>> data = List.of(
                createRow("phone", "13812345678")
        );
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("phone", "NOT_A_REAL_TYPE")
        );

        List<Map<String, Object>> result = processor.process(data, fields);

        assertEquals("13812345678", result.get(0).get("phone"),
                "无效类型名应被跳过");
    }

    @Test
    @DisplayName("入参数据不被修改（防御性复制）")
    void testInputNotMutated() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "张三");
        row.put("phone", "13812345678");

        List<Map<String, Object>> data = new ArrayList<>();
        data.add(row);

        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("phone", "PHONE")
        );

        processor.process(data, fields);

        // 原始数据应保持不变
        assertEquals("13812345678", data.get(0).get("phone"),
                "原始输入数据不应被修改");
    }

    @Test
    @DisplayName("集成测试 — 完整流程：姓名+手机+邮箱+身份证+地址+银行卡")
    void testFullPipeline() {
        List<Map<String, Object>> data = List.of(
                createRow(
                        "name", "张伟强",
                        "phone", "13812345678",
                        "email", "zhangsan@gmail.com",
                        "id_card", "110101199001011234",
                        "address", "北京市朝阳区建国路100号",
                        "bank_card", "6222021234567890123",
                        "age", 28
                )
        );
        List<SensitiveFieldInfo> fields = List.of(
                new SensitiveFieldInfo("name", "NAME"),
                new SensitiveFieldInfo("phone", "PHONE"),
                new SensitiveFieldInfo("email", "EMAIL"),
                new SensitiveFieldInfo("id_card", "ID_CARD"),
                new SensitiveFieldInfo("address", "ADDRESS"),
                new SensitiveFieldInfo("bank_card", "BANK_CARD")
        );

        List<Map<String, Object>> result = processor.process(data, fields);
        Map<String, Object> row = result.get(0);

        assertEquals("张**", row.get("name"));
        assertEquals("138****5678", row.get("phone"));
        assertEquals("zha***@gmail.com", row.get("email"));
        assertEquals("110101********1234", row.get("id_card"));
        assertTrue(row.get("address").toString().startsWith("北京市朝阳区"));
        assertEquals("****0123", row.get("bank_card"));
        assertEquals(28, row.get("age"), "非敏感字段 age 应保持原值");
    }

    // ==================== 辅助方法 ====================

    @SafeVarargs
    private static Map<String, Object> createRow(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}
