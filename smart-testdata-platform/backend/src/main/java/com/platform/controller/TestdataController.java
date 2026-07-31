package com.platform.controller;

import com.platform.dto.*;
import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.dto.GeneratePlanResponse.Range;
import com.platform.dto.GeneratePlanResponse.TablePlan;
import com.platform.generator.GeneratorEngine;
import com.platform.generator.table.TableDataGenerator;
import com.platform.generator.task.MultiTableDataGenerator;
import com.platform.generator.persistence.MultiTableWriteService;
import com.platform.service.TestdataService;
import com.platform.sql.InsertSqlBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 测试数据生成接口 — 代理 AI 服务 + 生成器引擎
 * <p>
 * 调用链：Vue → Spring Boot (/api/testdata/...) → FastAPI → LangChain Agent
 */
@RestController
@RequestMapping("/api/testdata")
@RequiredArgsConstructor
public class TestdataController {

    private final TestdataService testdataService;
    private final GeneratorEngine generatorEngine;
    private final TableDataGenerator tableDataGenerator;
    private final MultiTableDataGenerator multiTableDataGenerator;
    private final InsertSqlBuilder insertSqlBuilder;
    private final MultiTableWriteService multiTableWriteService;

    /**
     * 生成测试数据计划
     * <p>
     * POST /api/testdata/generate-plan
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "schema": { ... Schema JSON ... },
     *   "requirement": "生成1000条用户数据"
     * }
     * </pre>
     * <p>
     * 返回: GenerationPlan JSON
     */
    @PostMapping("/generate-plan")
    public ApiResponse<GeneratePlanResponse> generatePlan(@RequestBody GeneratePlanRequest request) {
        GeneratePlanResponse result = testdataService.generatePlan(request);
        return ApiResponse.success(result);
    }

    // ==================== 生成器测试 ====================

    /**
     * 测试单个生成器
     * <p>
     * POST /api/testdata/generator/test
     * <p>
     * 请求体:
     * <pre>
     * { "generator": "faker.email" }
     * </pre>
     * <p>
     * 返回:
     * <pre>
     * { "success": true, "value": "test@qq.com" }
     * </pre>
     */
    @PostMapping("/generator/test")
    public ApiResponse<GeneratorTestResponse> testGenerator(@RequestBody GeneratorTestRequest request) {
        try {
            FieldPlan fieldPlan = GeneratePlanResponse.FieldPlan.builder()
                    .name("_test")
                    .generator(request.getGenerator())
                    .range(Range.builder().min(1).max(100).build())
                    .build();

            Object value = generatorEngine.execute(fieldPlan);

            GeneratorTestResponse result = GeneratorTestResponse.builder()
                    .success(true)
                    .value(value)
                    .build();
            return ApiResponse.success(result);
        } catch (com.platform.exception.BusinessException e) {
            GeneratorTestResponse result = GeneratorTestResponse.builder()
                    .success(false)
                    .value(e.getMessage())
                    .build();
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    // ==================== 表级生成 ====================

    /**
     * 单表测试数据生成
     * <p>
     * POST /api/testdata/generator/table
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "table": "user",
     *   "count": 3,
     *   "fields": [
     *     { "name": "username", "generator": "faker.name" },
     *     { "name": "email",    "generator": "faker.email" }
     *   ]
     * }
     * </pre>
     * <p>
     * 返回:
     * <pre>
     * {
     *   "success": true,
     *   "table": "user",
     *   "count": 3,
     *   "data": [
     *     { "username": "张三", "email": "xxx@qq.com" },
     *     ...
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/generator/table")
    public ApiResponse<TableGenerateResponse> generateTable(@RequestBody TableGenerateRequest request) {
        try {
            TablePlan tablePlan = GeneratePlanResponse.TablePlan.builder()
                    .table(request.getTable())
                    .count(request.getCount())
                    .fields(request.getFields())
                    .build();

            List<Map<String, Object>> rows = tableDataGenerator.generate(tablePlan);

            TableGenerateResponse result = TableGenerateResponse.builder()
                    .success(true)
                    .table(request.getTable())
                    .count(rows.size())
                    .data(rows)
                    .build();
            return ApiResponse.success(result);
        } catch (com.platform.exception.BusinessException e) {
            TableGenerateResponse result = TableGenerateResponse.builder()
                    .success(false)
                    .table(request.getTable())
                    .count(0)
                    .data(List.of())
                    .build();
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    // ==================== 多表生成 ====================

    /**
     * 多表测试数据生成（按外键依赖关系自动排序）
     * <p>
     * POST /api/testdata/generator/multi-table
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "tables": [
     *     {
     *       "table": "department",
     *       "count": 3,
     *       "fields": [
     *         { "name": "id", "generator": "random.integer",
     *           "params": { "primaryKey": true } },
     *         { "name": "name", "generator": "faker.name" }
     *       ]
     *     },
     *     {
     *       "table": "employee",
     *       "count": 5,
     *       "fields": [
     *         { "name": "name", "generator": "faker.name" },
     *         { "name": "department_id",
     *           "foreignKey": { "table": "department", "column": "id" } }
     *       ]
     *     }
     *   ]
     * }
     * </pre>
     * <p>
     * 返回:
     * <pre>
     * {
     *   "success": true,
     *   "tables": [
     *     { "table": "department", "count": 3, "data": [...] },
     *     { "table": "employee",   "count": 5, "data": [...] }
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/generator/multi-table")
    public ApiResponse<MultiTableGenerateResponse> generateMultiTable(
            @RequestBody MultiTableGenerateRequest request) {
        try {
            MultiTableGenerateResponse result = multiTableDataGenerator.generate(request.getTables());
            return ApiResponse.success(result);
        } catch (com.platform.exception.BusinessException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    // ==================== SQL 生成 ====================

    /**
     * 构建 INSERT SQL 语句
     * <p>
     * POST /api/testdata/sql/build
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "table": "user",
     *   "data": [
     *     { "name": "test", "age": 20 }
     *   ]
     * }
     * </pre>
     * <p>
     * 返回:
     * <pre>
     * {
     *   "success": true,
     *   "sql": "INSERT INTO user (name, age) VALUES ('test', 20);"
     * }
     * </pre>
     */
    @PostMapping("/sql/build")
    public ApiResponse<SqlGenerateResponse> buildSql(@RequestBody SqlGenerateRequest request) {
        try {
            String sql = insertSqlBuilder.build(request.getTable(), request.getData());
            SqlGenerateResponse result = SqlGenerateResponse.builder()
                    .success(true)
                    .sql(sql)
                    .build();
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            SqlGenerateResponse result = SqlGenerateResponse.builder()
                    .success(false)
                    .sql(null)
                    .build();
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ==================== 数据库写入 ====================

    /**
     * 批量写入测试数据到目标数据库（事务保护）
     * <p>
     * POST /api/testdata/write
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "datasourceId": 2,
     *   "tables": [
     *     {
     *       "table": "department",
     *       "data": [
     *         { "id": 1, "name": "研发部" },
     *         { "id": 2, "name": "市场部" }
     *       ]
     *     }
     *   ]
     * }
     * </pre>
     * <p>
     * 返回:
     * <pre>
     * {
     *   "success": true,
     *   "tables": [
     *     { "table": "department", "success": true, "insertCount": 2 }
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/write")
    public ApiResponse<DatabaseWriteResponse> writeToDatabase(
            @RequestBody DatabaseWriteRequest request) {
        try {
            DatabaseWriteResponse result = multiTableWriteService.writeAll(
                    request.getDatasourceId(), request.getTables());
            return ApiResponse.success(result);
        } catch (com.platform.exception.BusinessException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }
}
