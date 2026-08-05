package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.connector.DatasourceConnectionPool;
import com.platform.connector.MetadataReader;
import com.platform.dto.DatasourceRequest;
import com.platform.dto.DatasourceResponse;
import com.platform.dto.SchemaResponse;
import com.platform.entity.Datasource;
import com.platform.entity.Project;
import com.platform.exception.BusinessException;
import com.platform.mapper.DatasourceMapper;
import com.platform.mapper.ProjectMapper;
import com.platform.util.AesUtil;
import com.platform.util.JdbcUrlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据源管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceMapper datasourceMapper;
    private final ProjectMapper projectMapper;
    private final AesUtil aesUtil;
    private final MetadataReader metadataReader;
    private final DatasourceConnectionPool connectionPool;

    /**
     * 创建数据源配置（密码 AES 加密存储）
     */
    public DatasourceResponse create(Long userId, DatasourceRequest request) {
        // 验证项目归属
        validateProjectOwnership(request.getProjectId(), userId);

        Datasource ds = new Datasource();
        ds.setProjectId(request.getProjectId());
        ds.setName(request.getName());
        ds.setDbType(request.getDbType() != null ? request.getDbType() : "MySQL");
        ds.setHost(request.getHost());
        ds.setPort(request.getPort());
        ds.setUsername(request.getUsername());
        ds.setPasswordEncrypted(aesUtil.encrypt(request.getPassword()));
        ds.setDbName(request.getDatabaseName());
        ds.setStatus("UNCONNECTED");
        datasourceMapper.insert(ds);
        log.info("数据源已创建: id={}, name={}", ds.getId(), ds.getName());
        return DatasourceResponse.fromEntity(ds);
    }

    /**
     * 查询项目下的所有数据源
     */
    public List<DatasourceResponse> listByProject(Long projectId, Long userId) {
        // 验证项目归属
        validateProjectOwnership(projectId, userId);

        List<Datasource> list = datasourceMapper.selectList(
                new LambdaQueryWrapper<Datasource>()
                        .eq(Datasource::getProjectId, projectId)
                        .orderByDesc(Datasource::getCreatedAt));
        return list.stream().map(DatasourceResponse::fromEntity).collect(Collectors.toList());
    }

    /**
     * 查询数据源详情（含用户隔离校验）
     */
    public DatasourceResponse getById(Long id, Long userId) {
        Datasource ds = getEntityById(id);
        // 验证数据源所属项目是否属于当前用户
        validateProjectOwnership(ds.getProjectId(), userId);
        return DatasourceResponse.fromEntity(ds);
    }

    /**
     * 更新数据源配置（含用户隔离校验）
     */
    public DatasourceResponse update(Long id, Long userId, DatasourceRequest request) {
        Datasource ds = getEntityById(id);
        // 验证数据源所属项目是否属于当前用户
        validateProjectOwnership(ds.getProjectId(), userId);

        ds.setName(request.getName());
        ds.setDbType(request.getDbType() != null ? request.getDbType() : ds.getDbType());
        ds.setHost(request.getHost());
        ds.setPort(request.getPort());
        ds.setUsername(request.getUsername());
        // 只有提供了新密码才更新
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            ds.setPasswordEncrypted(aesUtil.encrypt(request.getPassword()));
        }
        ds.setDbName(request.getDatabaseName());
        datasourceMapper.updateById(ds);
        connectionPool.evict(id);
        return DatasourceResponse.fromEntity(ds);
    }

    /**
     * 删除数据源（含用户隔离校验）
     */
    public void delete(Long id, Long userId) {
        Datasource ds = getEntityById(id);
        // 验证数据源所属项目是否属于当前用户
        validateProjectOwnership(ds.getProjectId(), userId);
        datasourceMapper.deleteById(id);
        connectionPool.evict(id);
    }

    // ==================== 内部方法（返回实体，供内部服务使用） ====================

    /**
     * 获取数据源实体（内部使用，不暴露给 Controller）
     */
    public Datasource getEntityById(Long id) {
        Datasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        return ds;
    }

    /**
     * 测试数据库连接（基于已保存的数据源 ID）
     */
    public boolean testConnection(Long id) {
        Datasource ds = getEntityById(id);
        boolean ok = metadataReader.testConnection(ds);

        // 更新连接状态
        ds.setStatus(ok ? "CONNECTED" : "ERROR");
        datasourceMapper.updateById(ds);

        return ok;
    }

    /**
     * 测试数据库连接（基于请求参数，不需要先保存）
     */
    public boolean testConnection(DatasourceRequest request) {
        String url = buildJdbcUrlFromRequest(request);
        return metadataReader.testConnection(url, request.getUsername(), request.getPassword());
    }

    /**
     * 获取数据库 Schema 信息
     */
    public SchemaResponse getSchema(Long id) {
        Datasource ds = getEntityById(id);
        try {
            SchemaResponse schema = metadataReader.readSchema(ds);
            log.info("Schema 读取成功: database={}, tables={}",
                    schema.getDatabase(), schema.getTables().size());
            return schema;
        } catch (Exception e) {
            log.error("Schema 读取失败: {}", e.getMessage());
            throw new BusinessException("数据库 Schema 读取失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 验证项目是否属于指定用户
     */
    private void validateProjectOwnership(Long projectId, Long userId) {
        if (projectId == null) {
            throw new BusinessException(400, "项目 ID 不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getUserId().equals(userId)) {
            throw new BusinessException(404, "项目不存在");
        }
    }

    /**
     * 根据请求参数构建 JDBC URL（不依赖实体）
     */
    private String buildJdbcUrlFromRequest(DatasourceRequest req) {
        return JdbcUrlBuilder.build(
                req.getHost(), req.getPort(), req.getDatabaseName(), req.getDbType());
    }

    /**
     * 根据数据源配置构建 JDBC URL
     */
    private String buildJdbcUrl(Datasource ds) {
        return JdbcUrlBuilder.build(ds);
    }
}
