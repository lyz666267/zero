package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.connector.MetadataReader;
import com.platform.dto.DatasourceRequest;
import com.platform.dto.SchemaResponse;
import com.platform.entity.Datasource;
import com.platform.exception.BusinessException;
import com.platform.mapper.DatasourceMapper;
import com.platform.util.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据源管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceMapper datasourceMapper;
    private final AesUtil aesUtil;
    private final MetadataReader metadataReader;

    /**
     * 创建数据源配置（密码 AES 加密存储）
     */
    public Datasource create(Long userId, DatasourceRequest request) {
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
        return ds;
    }

    /**
     * 查询项目下的所有数据源
     */
    public List<Datasource> listByProject(Long projectId, Long userId) {
        return datasourceMapper.selectList(
                new LambdaQueryWrapper<Datasource>()
                        .eq(Datasource::getProjectId, projectId)
                        .orderByDesc(Datasource::getCreatedAt));
    }

    /**
     * 查询数据源详情
     */
    public Datasource getById(Long id) {
        Datasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        return ds;
    }

    /**
     * 更新数据源配置
     */
    public Datasource update(Long id, DatasourceRequest request) {
        Datasource ds = getById(id);
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
        return ds;
    }

    /**
     * 删除数据源
     */
    public void delete(Long id) {
        getById(id); // 确保存在
        datasourceMapper.deleteById(id);
    }

    /**
     * 测试数据库连接（基于已保存的数据源 ID）
     */
    public boolean testConnection(Long id) {
        Datasource ds = getById(id);
        String url = buildJdbcUrl(ds);
        String password = aesUtil.decrypt(ds.getPasswordEncrypted());

        boolean ok = metadataReader.testConnection(url, ds.getUsername(), password);

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
        Datasource ds = getById(id);
        String url = buildJdbcUrl(ds);
        String password = aesUtil.decrypt(ds.getPasswordEncrypted());

        try {
            SchemaResponse schema = metadataReader.readSchema(
                    url, ds.getUsername(), password, ds.getDbName(), ds.getDbType());
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
     * 根据请求参数构建 JDBC URL（不依赖实体）
     */
    private String buildJdbcUrlFromRequest(DatasourceRequest req) {
        String dbType = req.getDbType() != null ? req.getDbType().toLowerCase() : "mysql";
        return switch (dbType) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                    req.getHost(), req.getPort(), req.getDatabaseName());
            default -> throw new BusinessException("暂不支持的数据库类型: " + req.getDbType());
        };
    }

    /**
     * 根据数据源配置构建 JDBC URL
     */
    private String buildJdbcUrl(Datasource ds) {
        String dbType = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "mysql";
        return switch (dbType) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                    ds.getHost(), ds.getPort(), ds.getDbName());
            default -> throw new BusinessException("暂不支持的数据库类型: " + ds.getDbType());
        };
    }
}
