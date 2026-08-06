package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.dto.DatasourceRequest;
import com.platform.dto.DatasourceResponse;
import com.platform.dto.SchemaResponse;
import com.platform.service.DatasourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理接口
 *
 * <h3>安全策略</h3>
 * <ul>
 *   <li>所有写操作和详情查询必须验证资源所属用户（通过 project → userId 链路）</li>
 *   <li>返回 {@link DatasourceResponse} 而非实体，禁止泄露 {@code passwordEncrypted}</li>
 * </ul>
 */
@Slf4j
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
    public ApiResponse<DatasourceResponse> create(@Valid @RequestBody DatasourceRequest request,
                                                   Authentication auth) {
        Long userId = getCurrentUserId(auth);
        DatasourceResponse response = datasourceService.create(userId, request);
        log.info("Datasource created: userId={}, datasourceId={}, projectId={}",
                userId, response.getId(), response.getProjectId());
        return ApiResponse.success(response);
    }

    /**
     * 查询项目下的数据源列表
     * GET /api/datasource?projectId=1
     */
    @GetMapping
    public ApiResponse<List<DatasourceResponse>> list(@RequestParam Long projectId,
                                                       Authentication auth) {
        return ApiResponse.success(datasourceService.listByProject(projectId, getCurrentUserId(auth)));
    }

    /**
     * 查询数据源详情（需用户隔离）
     * GET /api/datasource/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<DatasourceResponse> getById(@PathVariable Long id,
                                                    Authentication auth) {
        return ApiResponse.success(datasourceService.getById(id, getCurrentUserId(auth)));
    }

    /**
     * 更新数据源配置（需用户隔离）
     * PUT /api/datasource/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<DatasourceResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody DatasourceRequest request,
                                                   Authentication auth) {
        Long userId = getCurrentUserId(auth);
        DatasourceResponse response = datasourceService.update(id, userId, request);
        log.info("Datasource updated: userId={}, datasourceId={}", userId, id);
        return ApiResponse.success(response);
    }

    /**
     * 删除数据源（需用户隔离）
     * DELETE /api/datasource/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    Authentication auth) {
        Long userId = getCurrentUserId(auth);
        datasourceService.delete(id, userId);
        log.info("Datasource deleted: userId={}, datasourceId={}", userId, id);
        return ApiResponse.success();
    }

    /**
     * 测试数据库连接
     * POST /api/datasource/test
     */
    @PostMapping("/test")
    public ApiResponse<Boolean> testConnection(@RequestBody DatasourceRequest request) {
        boolean ok = datasourceService.testConnection(request);
        return ApiResponse.success(ok);
    }

    /**
     * 获取数据库 Schema 信息
     * GET /api/datasource/{id}/schema
     */
    @GetMapping("/{id}/schema")
    public ApiResponse<SchemaResponse> getSchema(@PathVariable Long id) {
        SchemaResponse response = datasourceService.getSchema(id);
        log.info("Datasource schema loaded: datasourceId={}, tables={}",
                id, response.getTables() == null ? 0 : response.getTables().size());
        return ApiResponse.success(response);
    }
}
