package com.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Schema 分析服务 — 代理 AI 服务的 Schema 理解 Agent
 *
 * <p>调用链：Vue / 内部服务 → Spring Boot → FastAPI → SchemaAgent (LLM/Mock)</p>
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li>LLM 模式：AI 服务调用 DeepSeek 进行深度语义分析</li>
 *   <li>Mock 模式：AI 服务降级为规则引擎字段名匹配（无 API Key 时）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaAnalyzeService {

    private final RestTemplate restTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${platform.ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    /**
     * 调用 AI 服务进行 Schema 语义分析
     *
     * @param database 数据库名
     * @param dbType   数据库类型（MySQL/PostgreSQL）
     * @param tables   表结构列表，每项包含 tableName, comment, columns
     * @return AI 服务响应的 Map（success, result, mock）
     */
    public Map<String, Object> analyzeSchema(String database, String dbType,
                                              List<Map<String, Object>> tables) {
        String url = aiServiceUrl + "/api/ai/analyze-schema";

        // 构建请求体
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("database", database);
        body.put("dbType", dbType != null ? dbType : "MySQL");
        body.put("tables", tables != null ? tables : List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

        log.info("调用 AI 服务 analyze-schema: database={}, tables={}",
                database, tables != null ? tables.size() : 0);

        try {
            String rawJson = restTemplate.postForObject(url, httpEntity, String.class);
            log.debug("AI 服务原始响应长度: {} 字符",
                    rawJson != null ? rawJson.length() : 0);

            if (rawJson == null || rawJson.isEmpty()) {
                throw new BusinessException("AI 服务返回空响应");
            }

            Map<String, Object> response = objectMapper.readValue(
                    rawJson, new TypeReference<Map<String, Object>>() {});

            Boolean success = (Boolean) response.getOrDefault("success", false);
            if (!success) {
                String error = (String) response.getOrDefault("error", "未知错误");
                log.error("AI Schema 分析返回错误: {}", error);
                throw new BusinessException(error);
            }

            Boolean mock = (Boolean) response.getOrDefault("mock", false);
            log.info("AI Schema 分析完成: database={}, mock={}", database, mock);

            return response;

        } catch (RestClientException e) {
            log.error("AI 服务调用失败: {}", e.getMessage());
            throw new BusinessException("AI 服务调用失败，请确保 AI 服务已启动: " + e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI Schema 分析响应解析失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务响应解析失败: " + e.getMessage());
        }
    }
}
