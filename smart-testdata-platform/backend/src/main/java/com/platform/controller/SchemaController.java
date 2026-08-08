package com.platform.controller;

import com.platform.dto.*;
import com.platform.dto.RelationAnalysisResponse.DependencyGraph;
import com.platform.dto.RelationAnalysisResponse.RelationItem;
import com.platform.schema.SchemaCacheService;
import com.platform.schema.SchemaSampleService;
import com.platform.schema.relation.DependencyGraphService;
import com.platform.schema.relation.RelationAnalyzerService;
import com.platform.schema.relation.TableOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Schema 分析接口 — 缓存同步 / 缓存查询 / 数据采样 / 关系分析
 */
@Slf4j
@RestController
@RequestMapping("/api/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaSampleService schemaSampleService;
    private final SchemaCacheService schemaCacheService;
    private final RelationAnalyzerService relationAnalyzerService;
    private final DependencyGraphService dependencyGraphService;
    private final TableOrderService tableOrderService;

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

    // ==================== 关系分析 ====================

    /**
     * 分析数据源的外键关系、依赖图、生成顺序
     * GET /api/schema/relation/{datasourceId}
     *
     * <p>返回包含三部分：</p>
     * <ul>
     *   <li>relations — 外键关系列表</li>
     *   <li>graph — 依赖图（节点 + 有向边）</li>
     *   <li>generationOrder — 拓扑排序后的表生成顺序</li>
     * </ul>
     */
    @GetMapping("/relation/{datasourceId}")
    public ApiResponse<RelationAnalysisResponse> getRelations(@PathVariable Long datasourceId) {
        List<RelationItem> relations = relationAnalyzerService.analyze(datasourceId);
        DependencyGraph graph = dependencyGraphService.build(relations);
        List<String> order = tableOrderService.topologicalSort(relations);

        RelationAnalysisResponse result = RelationAnalysisResponse.builder()
                .relations(relations)
                .graph(graph)
                .generationOrder(order)
                .build();

        return ApiResponse.success(result);
    }
}
