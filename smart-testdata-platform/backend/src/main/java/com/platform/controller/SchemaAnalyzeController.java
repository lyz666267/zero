package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.service.SchemaAnalyzeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Schema 语义分析接口 — 代理 AI 服务的 Schema 理解 Agent
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/schema/analyze — Schema 语义分析（字段标签 + 敏感检测 + 外键推断 + 生成器推荐）</li>
 * </ul>
 *
 * <h3>调用链</h3>
 * <pre>
 * Vue / 内部服务 → Spring Boot SchemaAnalyzeController
 *     → SchemaAnalyzeService → FastAPI POST /api/ai/analyze-schema
 *     → SchemaAgent (LLM DeepSeek / Mock 规则引擎)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/schema")
@RequiredArgsConstructor
public class SchemaAnalyzeController {

    private final SchemaAnalyzeService schemaAnalyzeService;

    /**
     * Schema 语义分析
     *
     * <p>POST /api/schema/analyze</p>
     *
     * <h3>请求示例</h3>
     * <pre>
     * {
     *   "database": "my_shop",
     *   "dbType": "MySQL",
     *   "tables": [
     *     {
     *       "tableName": "users",
     *       "comment": "用户表",
     *       "columns": [
     *         {"name": "id", "type": "INT", "nullable": false, "primaryKey": true},
     *         {"name": "username", "type": "VARCHAR(50)", "nullable": false},
     *         {"name": "phone", "type": "VARCHAR(20)", "nullable": true},
     *         {"name": "email", "type": "VARCHAR(100)", "nullable": true}
     *       ]
     *     }
     *   ]
     * }
     * </pre>
     *
     * <h3>响应示例</h3>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "success": true,
     *     "result": {
     *       "database": "my_shop",
     *       "dbType": "MySQL",
     *       "tables": [{
     *         "tableName": "users",
     *         "columns": [{
     *           "name": "phone",
     *           "semanticLabel": "PHONE",
     *           "sensitiveDetection": { "sensitive": true, "sensitiveType": "PHONE", "confidence": 0.95 },
     *           "generatorSuggestion": { "generator": "faker.phone_number", "reason": "..." }
     *         }]
     *       }],
     *       "summary": { "totalTables": 1, "totalColumns": 4, "sensitiveColumns": 2 }
     *     },
     *     "mock": false
     *   }
     * }
     * </pre>
     *
     * @param body 请求体，包含 database, dbType, tables
     * @return AI 分析结果
     */
    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyzeSchema(@RequestBody Map<String, Object> body) {
        String database = (String) body.getOrDefault("database", "unknown");
        String dbType = (String) body.getOrDefault("dbType", "MySQL");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) body.get("tables");

        log.info("Schema 分析请求: database={}, tableCount={}",
                database, tables != null ? tables.size() : 0);

        Map<String, Object> result = schemaAnalyzeService.analyzeSchema(database, dbType, tables);

        return ApiResponse.success(result);
    }
}
