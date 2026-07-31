package com.platform.privacy.detector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 语义敏感字段检测器（第 3 层）
 *
 * <h3>职责</h3>
 * <p>调用 Python AI 服务的 LLM 进行语义级别的敏感字段识别。
 * 发送字段名、注释和样本值，由 LLM 判断是否包含敏感信息并返回类型、置信度和原因。</p>
 *
 * <h3>降级策略</h3>
 * <ul>
 *   <li>AI 服务不可用 → 返回空列表（不阻断其他检测器）</li>
 *   <li>LLM 返回无效 JSON → 返回空列表</li>
 *   <li>网络超时/错误 → 返回空列表并记录警告</li>
 * </ul>
 *
 * <h3>调用链路</h3>
 * <pre>
 * Java LLMSensitiveDetector
 *   → RestTemplate POST /api/ai/detect-sensitive
 *     → Python FastAPI routes.py
 *       → llm_router.chat()
 *         → DeepSeek / Qwen / Mock
 * </pre>
 */
@Slf4j
@Component
public class LLMSensitiveDetector implements SensitiveDetector {

    private final RestTemplate restTemplate;

    @Value("${platform.ai-service.url:http://localhost:8000}")
    String aiServiceUrl;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public LLMSensitiveDetector(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<DetectionResult> detect(DetectionContext context) {
        if (context == null || context.getColumns() == null || context.getColumns().isEmpty()) {
            log.debug("LLMSensitiveDetector: 无列信息，跳过检测");
            return Collections.emptyList();
        }

        try {
            // 构建请求体
            Map<String, Object> body = buildRequestBody(context);

            String url = aiServiceUrl + "/api/ai/detect-sensitive";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.info("LLMSensitiveDetector: 调用 AI 服务 detect-sensitive, {} 列", context.columnCount());

            String rawJson = restTemplate.postForObject(url, entity, String.class);

            if (rawJson == null || rawJson.isEmpty()) {
                log.warn("LLMSensitiveDetector: AI 服务返回空响应");
                return Collections.emptyList();
            }

            // 解析响应
            Map<String, Object> response = objectMapper.readValue(rawJson,
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) response.getOrDefault("fields", List.of());

            List<DetectionResult> results = fields.stream()
                    .map(this::parseField)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            boolean mock = Boolean.TRUE.equals(response.get("mock"));
            log.info("LLMSensitiveDetector: {} → {} 个敏感字段 (mock={})",
                    context.columnCount(), results.size(), mock);

            return results;

        } catch (RestClientException e) {
            log.warn("LLMSensitiveDetector: AI 服务调用失败，降级返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("LLMSensitiveDetector: 解析失败，降级返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建发送给 AI 服务的请求体
     */
    private Map<String, Object> buildRequestBody(DetectionContext context) {
        List<Map<String, Object>> columns = context.getColumns().stream()
                .map(col -> {
                    Map<String, Object> colMap = new LinkedHashMap<>();
                    colMap.put("columnName", col.getColumnName());
                    colMap.put("columnType", col.getColumnType());
                    colMap.put("columnComment", col.getColumnComment() != null ? col.getColumnComment() : "");
                    return colMap;
                })
                .collect(Collectors.toList());

        // 提取样本值
        Map<String, List<String>> sampleValues = new LinkedHashMap<>();
        if (context.hasSampleData()) {
            for (SchemaColumn col : context.getColumns()) {
                List<String> values = context.getValuesForColumn(col.getColumnName());
                if (!values.isEmpty()) {
                    // 最多取 5 个样本值
                    sampleValues.put(col.getColumnName(),
                            values.size() > 5 ? values.subList(0, 5) : values);
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("columns", columns);
        body.put("sampleValues", sampleValues);
        return body;
    }

    /**
     * 解析 LLM 返回的单个字段结果
     */
    private DetectionResult parseField(Map<String, Object> field) {
        try {
            String columnName = (String) field.get("columnName");
            String typeStr = (String) field.get("type");
            double confidence = field.get("confidence") instanceof Number
                    ? ((Number) field.get("confidence")).doubleValue() : 0.0;
            String reason = (String) field.getOrDefault("reason", "");

            if (columnName == null || typeStr == null) {
                return null;
            }

            SensitiveFieldType type;
            try {
                type = SensitiveFieldType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.debug("LLMSensitiveDetector: LLM 返回了无法识别的类型: {}", typeStr);
                return null;
            }

            if (type == SensitiveFieldType.UNKNOWN) {
                return null; // UNKNOWN 不返回
            }

            return DetectionResult.fromLLM(columnName, type, confidence, reason);

        } catch (Exception e) {
            log.warn("LLMSensitiveDetector: 解析字段失败: {}", e.getMessage());
            return null;
        }
    }
}
