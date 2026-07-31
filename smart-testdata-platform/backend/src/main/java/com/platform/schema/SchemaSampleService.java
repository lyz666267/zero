package com.platform.schema;

import com.platform.dto.SampleResponse;
import com.platform.dto.SampleResponse.TableSample;
import com.platform.entity.Datasource;
import com.platform.exception.BusinessException;
import com.platform.mapper.DatasourceMapper;
import com.platform.util.AesUtil;
import com.platform.util.JdbcUrlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Schema 数据采样服务 — 对每个字段取前 5 条实际数据
 *
 * <h3>安全策略</h3>
 * <ul>
 *   <li>表名/列名先正则校验 {@code ^[a-zA-Z_][a-zA-Z0-9_]*$}，再与 information_schema 交叉验证</li>
 *   <li>仅执行 SELECT 查询，不允许任何写操作</li>
 *   <li>列名优先从本地缓存获取；缓存缺失时才直连 information_schema</li>
 * </ul>
 *
 * <h3>缓存优先策略</h3>
 * <ul>
 *   <li>先查 {@link SchemaCacheService#hasCache(Long)} 判断是否已同步</li>
 *   <li>已同步 → 用 {@link SchemaCacheService#getCachedColumnNames(Long, String)} 获取列名</li>
 *   <li>未同步 → 回退直连 information_schema（会打 WARN 日志提示先执行 sync）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaSampleService {

    /** 合法标识符正则：字母或下划线开头，仅含字母数字下划线 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** 每列采样行数 */
    private static final int SAMPLE_SIZE = 5;

    private final DatasourceMapper datasourceMapper;
    private final AesUtil aesUtil;
    private final SchemaCacheService schemaCacheService;

    /**
     * 对指定数据源的若干表进行数据采样
     *
     * @param datasourceId 数据源 ID
     * @param tableNames   表名列表
     * @return 采样结果
     */
    public SampleResponse sample(Long datasourceId, List<String> tableNames) {
        // 1. 获取数据源配置
        Datasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }

        String url = JdbcUrlBuilder.build(ds);
        String password = aesUtil.decrypt(ds.getPasswordEncrypted());

        // 2. 逐表采样
        List<SampleResponse.TableSample> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), password)) {
            for (String tableName : tableNames) {
                try {
                    TableSample ts = sampleTable(conn, datasourceId, ds.getDbName(), tableName.trim());
                    results.add(ts);
                } catch (BusinessException e) {
                    log.warn("表 {} 采样失败: {}", tableName, e.getMessage());
                    // 单表失败不影响其他表，返回空采样
                    results.add(SampleResponse.TableSample.builder()
                            .table(tableName)
                            .columns(Collections.emptyMap())
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("数据库连接失败: {}", e.getMessage());
            throw new BusinessException("数据库连接失败: " + e.getMessage());
        }

        return SampleResponse.builder()
                .success(true)
                .data(results)
                .build();
    }

    // ==================== 核心采样逻辑 ====================

    /**
     * 对单张表进行采样
     *
     * <p>缓存优先：先查本地缓存，命中则跳过 JDBC 元数据查询；未命中回退直连 information_schema。</p>
     */
    private TableSample sampleTable(Connection conn, Long datasourceId, String dbName, String tableName)
            throws SQLException {
        // (a) 正则校验表名
        validateIdentifier(tableName, "表名");

        // (b) 获取列名 — 缓存优先，information_schema 兜底
        List<String> columnNames;
        if (schemaCacheService.hasCache(datasourceId)) {
            columnNames = schemaCacheService.getCachedColumnNames(datasourceId, tableName);
            if (columnNames.isEmpty()) {
                throw new BusinessException("表 " + tableName + " 不在缓存中，请重新同步 Schema 缓存");
            }
            log.debug("表 {} 列名来自缓存: {} 列", tableName, columnNames.size());
        } else {
            log.warn("数据源 {} 无 Schema 缓存，回退到直连 information_schema 查询。"
                    + "建议先执行 POST /api/schema/cache/sync", datasourceId);
            columnNames = getValidColumnNames(conn, dbName, tableName);
            if (columnNames.isEmpty()) {
                throw new BusinessException("表 " + tableName + " 不存在或无可见列");
            }
        }

        // (c) 逐列采样
        Map<String, List<Object>> columnSamples = new LinkedHashMap<>();
        for (String colName : columnNames) {
            List<Object> samples = sampleColumn(conn, tableName, colName);
            columnSamples.put(colName, samples);
        }

        log.info("表 {} 采样完成: {} 列", tableName, columnNames.size());
        return TableSample.builder()
                .table(tableName)
                .columns(columnSamples)
                .build();
    }

    /**
     * 对单列采样前 {@value SAMPLE_SIZE} 条数据
     *
     * <p>列名已通过 information_schema 校验，可安全拼入 SQL。</p>
     */
    private List<Object> sampleColumn(Connection conn, String tableName, String colName)
            throws SQLException {
        // 列名已在 getValidColumnNames 中校验过，此处用反引号包裹防止关键字冲突
        String sql = "SELECT `" + colName + "` FROM `" + tableName
                + "` WHERE `" + colName + "` IS NOT NULL LIMIT " + SAMPLE_SIZE;

        List<Object> values = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getObject(1));
            }
        }
        return values;
    }

    // ==================== 安全校验 ====================

    /**
     * 校验标识符是否合法，防止 SQL 注入
     */
    private void validateIdentifier(String identifier, String label) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException(label + "包含非法字符: " + identifier);
        }
    }

    /**
     * 从 information_schema 获取表的真实列名列表
     *
     * <p>同时验证表是否存在：若结果为空则表不存在。</p>
     */
    private List<String> getValidColumnNames(Connection conn, String dbName, String tableName)
            throws SQLException {
        String sql = """
                SELECT COLUMN_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;

        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return names;
    }

}
