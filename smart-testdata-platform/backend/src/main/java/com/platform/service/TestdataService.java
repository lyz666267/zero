package com.platform.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.GeneratePlanRequest;
import com.platform.dto.GeneratePlanResponse;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试数据生成服务 — 代理 AI 服务
 * <p>
 * 调用链：Vue → Spring Boot → FastAPI → LangChain Agent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestdataService {

    private final RestTemplate restTemplate;

    /** Jackson ObjectMapper — 配置忽略未知字段，兼容 AI 服务返回的额外字段 */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${platform.ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    /**
     * 调用 AI 服务生成测试数据计划
     *
     * @param request 包含 Schema JSON + 用户需求
     * @return 生成计划
     */
    public GeneratePlanResponse generatePlan(GeneratePlanRequest request) {
        String url = aiServiceUrl + "/api/ai/generate-plan";

        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("schema_data", request.getSchema() != null ? request.getSchema() : Collections.emptyMap());
        body.put("requirement", request.getRequirement());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

        log.info("调用 AI 服务 generate-plan: requirement={}", request.getRequirement());

        try {
            // 先以 String 获取响应，再用容错的 ObjectMapper 反序列化
            String rawJson = restTemplate.postForObject(url, httpEntity, String.class);
            log.debug("AI 服务原始响应: {}", rawJson);

            if (rawJson == null || rawJson.isEmpty()) {
                throw new BusinessException("AI 服务返回空响应");
            }

            GeneratePlanResponse response = objectMapper.readValue(rawJson, GeneratePlanResponse.class);

            if (!response.isSuccess()) {
                log.error("AI 服务返回错误: {}", response.getError());
                throw new BusinessException(response.getError() != null
                        ? response.getError() : "生成计划失败");
            }

            log.info("AI 服务返回成功: taskName={}, mock={}",
                    response.getPlan() != null ? response.getPlan().getTaskName() : "null",
                    response.isMock());

            return response;

        } catch (RestClientException e) {
            log.error("AI 服务调用失败: {}", e.getMessage());
            throw new BusinessException("AI 服务调用失败，请确保 AI 服务已启动: " + e.getMessage());
        } catch (Exception e) {
            log.error("AI 响应解析失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务响应解析失败: " + e.getMessage());
        }
    }
}
