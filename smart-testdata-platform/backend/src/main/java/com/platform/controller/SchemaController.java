package com.platform.controller;

import com.platform.dto.*;
import com.platform.schema.SchemaCacheService;
import com.platform.schema.SchemaSampleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Schema 分析接口 — 缓存同步 / 缓存查询 / 数据采样
 */
@RestController
@RequestMapping("/api/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaSampleService schemaSampleService;
    private final SchemaCacheService schemaCacheService;

    // ==================== 数据采样 ====================

    /**
     * 对数据源的表进行数据采样（优先使用缓存）
     * POST /api/schema/sample
     */
    @PostMapping("/sample")
    public ApiResponse<SampleResponse> sample(@Valid @RequestBody SampleRequest request) {
        SampleResponse result = schemaSampleService.sample(
                request.getDatasourceId(),
                request.getTables());
        return ApiResponse.success(result);
    }

    // ==================== Schema 缓存 ====================

    /**
     * 同步 Schema 到缓存表
     * POST /api/schema/cache/sync
     */
    @PostMapping("/cache/sync")
    public ApiResponse<SchemaCacheSyncResponse> syncCache(
            @Valid @RequestBody SchemaCacheSyncRequest request) {
        SchemaCacheSyncResponse result = schemaCacheService.sync(request.getDatasourceId());
        return ApiResponse.success(result);
    }

    /**
     * 查询缓存的 Schema
     * GET /api/schema/cache/{datasourceId}
     */
    @GetMapping("/cache/{datasourceId}")
    public ApiResponse<CachedSchemaResponse> getCache(@PathVariable Long datasourceId) {
        CachedSchemaResponse result = schemaCacheService.getSchema(datasourceId);
        return ApiResponse.success(result);
    }
}
