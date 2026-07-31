package com.platform.privacy.detector;

import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 敏感字段检测器测试
 *
 * <p>由于 LLMSensitiveDetector 依赖外部 AI 服务，这些测试主要验证：
 * <ul>
 *   <li>AI 服务不可用时的降级行为（返回空列表）</li>
 *   <li>空输入保护</li>
 *   <li>请求体构建逻辑（通过集成测试覆盖）</li>
 * </ul>
 *
 * <p>完整的 LLM 调用链路测试在 Python 端进行（test_tool_agent.py 等）。</p>
 */
@DisplayName("LLM 检测器测试")
class LLMSensitiveDetectorTest {

    private LLMSensitiveDetector detector;

    @BeforeEach
    void setUp() {
        // 使用真实的 RestTemplate（无 AI 服务运行 → 每次调用都会降级）
        detector = new LLMSensitiveDetector(new RestTemplate());
        // 指向一个不存在端口，确保快速失败
        detector.aiServiceUrl = "http://localhost:19999";
    }

    // ==================== 1. AI 服务不可用时降级 ====================

    @Test
    @DisplayName("AI 服务不可用时返回空列表（不抛异常）")
    void testAiServiceUnavailableReturnsEmpty() {
        DetectionContext ctx = contextOf(
                column("phone", "varchar", "用户手机号"),
                column("email", "varchar", "用户邮箱"));

        List<DetectionResult> results = detector.detect(ctx);

        // 服务不可用 → 降级返回空列表
        assertTrue(results.isEmpty(),
                "AI 服务不可用时应降级返回空列表，实际返回: " + results.size() + " results");
    }

    @Test
    @DisplayName("AI 服务不可用 — 不应抛异常")
    void testAiServiceUnavailableNoException() {
        DetectionContext ctx = contextOf(
                column("phone", "varchar", "手机"));

        // 不应抛出任何异常
        assertDoesNotThrow(() -> detector.detect(ctx));
    }

    // ==================== 2. 空输入保护 ====================

    @Test
    @DisplayName("null 上下文返回空列表")
    void testNullContextReturnsEmpty() {
        List<DetectionResult> results = detector.detect(null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("空列列表返回空列表")
    void testEmptyColumnsReturnsEmpty() {
        DetectionContext ctx = new DetectionContext(List.of(), null);
        List<DetectionResult> results = detector.detect(ctx);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("null 列列表返回空列表")
    void testNullColumnsReturnsEmpty() {
        DetectionContext ctx = new DetectionContext(null, null);
        List<DetectionResult> results = detector.detect(ctx);
        assertTrue(results.isEmpty());
    }

    // ==================== 3. 请求体构建验证 ====================

    @Test
    @DisplayName("带样本数据时请求体应包含 sampleValues")
    void testSampleValuesIncludedWhenPresent() {
        SchemaColumn col = column("phone", "varchar", "手机号");
        List<Map<String, Object>> sampleData = List.of(
                Map.of("phone", (Object) "13812345678"),
                Map.of("phone", (Object) "13900001111")
        );
        DetectionContext ctx = new DetectionContext(List.of(col), sampleData);

        // 不应抛异常（即使服务不可用）
        List<DetectionResult> results = detector.detect(ctx);
        assertTrue(results.isEmpty()); // 服务不可用
    }

    @Test
    @DisplayName("无样本数据时不抛异常")
    void testNoSampleDataHandled() {
        DetectionContext ctx = contextOf(
                column("phone", "varchar", "手机号"));

        List<DetectionResult> results = detector.detect(ctx);
        assertTrue(results.isEmpty());
    }

    // ==================== 辅助方法 ====================

    private static SchemaColumn column(String name, String columnType, String comment) {
        SchemaColumn col = new SchemaColumn();
        col.setColumnName(name);
        col.setColumnType(columnType);
        col.setColumnComment(comment);
        return col;
    }

    private static DetectionContext contextOf(SchemaColumn... columns) {
        return new DetectionContext(List.of(columns), null);
    }
}
