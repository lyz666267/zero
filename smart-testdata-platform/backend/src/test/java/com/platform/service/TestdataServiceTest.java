package com.platform.service;

import com.platform.dto.GeneratePlanRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestdataService 单测 — 验证转发 AI 服务时使用 schema_data 字段
 */
@ExtendWith(MockitoExtension.class)
class TestdataServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TestdataService testdataService;

    @Test
    @SuppressWarnings("unchecked")
    void generatePlanShouldSendSchemaDataFieldToAiService() {
        ReflectionTestUtils.setField(testdataService, "aiServiceUrl", "http://localhost:8000");

        Map<String, Object> schema = Map.of(
                "database", "demo_db",
                "tables", Collections.emptyList()
        );
        GeneratePlanRequest request = new GeneratePlanRequest(schema, "生成100条用户数据");

        when(restTemplate.postForObject(
                eq("http://localhost:8000/api/ai/generate-plan"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn("{\"success\":true,\"plan\":null,\"mock\":true}");

        testdataService.generatePlan(request);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(
                eq("http://localhost:8000/api/ai/generate-plan"),
                captor.capture(),
                eq(String.class)
        );

        Map<String, Object> body = captor.getValue().getBody();
        assertTrue(body.containsKey("schema_data"));
        assertEquals(schema, body.get("schema_data"));
        assertEquals("生成100条用户数据", body.get("requirement"));
        assertFalse(body.containsKey("schema"));
    }
}
