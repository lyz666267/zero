package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.dto.DatasourceRequest;
import com.platform.dto.SchemaResponse;
import com.platform.entity.Datasource;
import com.platform.service.DatasourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理接口
 */
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;

    private Long getCurrentUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    /**
     * 创建数据源配置
     * POST /api/datasource
     */
    @PostMapping
    public ApiResponse<Datasource> create(@Valid @RequestBody DatasourceRequest request,
                                          Authentication auth) {
        return ApiResponse.success(datasourceService.create(getCurrentUserId(auth), request));
    }

    /**
     * 查询项目下的数据源列表
     * GET /api/datasource?projectId=1
     */
    @GetMapping
    public ApiResponse<List<Datasource>> list(@RequestParam Long projectId,
                                              Authentication auth) {
        return ApiResponse.success(datasourceService.listByProject(projectId, getCurrentUserId(auth)));
    }

    /**
     * 查询数据源详情
     * GET /api/datasource/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<Datasource> getById(@PathVariable Long id) {
        return ApiResponse.success(datasourceService.getById(id));
    }

    /**
     * 更新数据源配置
     * PUT /api/datasource/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<Datasource> update(@PathVariable Long id,
                                          @Valid @RequestBody DatasourceRequest request) {
        return ApiResponse.success(datasourceService.update(id, request));
    }

    /**
     * 删除数据源
     * DELETE /api/datasource/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return ApiResponse.success();
    }

    /**
     * 测试数据库连接
     * POST /api/datasource/test
     */
    @PostMapping("/test")
    public ApiResponse<Boolean> testConnection(@RequestBody DatasourceRequest request) {
        // 临时创建、测试、然后可选择保存
        boolean ok = datasourceService.testConnection(request);
        return ApiResponse.success(ok);
    }

    /**
     * 获取数据库 Schema 信息
     * GET /api/datasource/{id}/schema
     */
    @GetMapping("/{id}/schema")
    public ApiResponse<SchemaResponse> getSchema(@PathVariable Long id) {
        return ApiResponse.success(datasourceService.getSchema(id));
    }
}
