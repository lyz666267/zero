package com.platform.schema;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.dto.CachedSchemaResponse;
import com.platform.dto.CachedSchemaResponse.CachedColumnInfo;
import com.platform.dto.CachedSchemaResponse.CachedTableInfo;
import com.platform.dto.SchemaCacheSyncResponse;
import com.platform.entity.Datasource;
import com.platform.entity.schema.SchemaColumn;
import com.platform.entity.schema.SchemaTable;
import com.platform.exception.BusinessException;
import com.platform.mapper.DatasourceMapper;
import com.platform.mapper.schema.SchemaColumnMapper;
import com.platform.mapper.schema.SchemaTableMapper;
import com.platform.util.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema 缓存服务 — 将 information_schema 同步到本地表，加速后续查询
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>同步：从目标数据库拉取 Schema → 写入 schema_table / schema_column</li>
 *   <li>查询：从缓存表读取 Schema 结构</li>
 *   <li>提供列名列表供 {@link SchemaSampleService} 采样使用</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaCacheService {

    private final DatasourceMapper datasourceMapper;
    private final SchemaTableMapper schemaTableMapper;
    private final SchemaColumnMapper schemaColumnMapper;
    private final AesUtil aesUtil;

    // ==================== 同步 ====================

    /**
     * 同步数据源的 Schema 到缓存表
     *
     * <p>在事务中执行：先删后增，保证原子性。失败自动回滚。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 同步结果（表数 + 列数）
     */
    @Transactional(rollbackFor = Exception.class)
    public SchemaCacheSyncResponse sync(Long datasourceId) {
        Datasource ds = getDatasource(datasourceId);
        String url = buildJdbcUrl(ds);
        String password = aesUtil.decrypt(ds.getPasswordEncrypted());

        log.info("开始同步 Schema 缓存: datasourceId={}, db={}", datasourceId, ds.getDbName());

        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), password)) {
            // 1. 删除该数据源的旧缓存
            deleteOldCache(datasourceId);

            // 2. 读取 information_schema
            List<TableMeta> tableMetas = readTableMetas(conn, ds.getDbName());

            // 3. 写入缓存
            int totalColumns = 0;
            for (TableMeta tm : tableMetas) {
                SchemaTable st = insertTableCache(datasourceId, tm);
                totalColumns += insertColumnCache(st.getId(), tm.columns);
            }

            log.info("Schema 缓存同步完成: datasourceId={}, tables={}, columns={}",
                    datasourceId, tableMetas.size(), totalColumns);

            return SchemaCacheSyncResponse.builder()
                    .success(true)
                    .tableCount(tableMetas.size())
                    .columnCount(totalColumns)
                    .build();

        } catch (SQLException e) {
            log.error("Schema 同步失败 — 数据库连接异常: datasourceId={}, error={}",
                    datasourceId, e.getMessage());
            throw new BusinessException("数据库连接失败: " + e.getMessage());
        }
    }

    // ==================== 查询缓存 ====================

    /**
     * 查询缓存的 Schema 结构
     *
     * @param datasourceId 数据源 ID
     * @return 完整的表+列结构
     */
    public CachedSchemaResponse getSchema(Long datasourceId) {
        // 验证数据源存在
        getDatasource(datasourceId);

        List<SchemaTable> tables = schemaTableMapper.selectList(
                new LambdaQueryWrapper<SchemaTable>()
                        .eq(SchemaTable::getDatasourceId, datasourceId)
                        .orderByAsc(SchemaTable::getTableName));

        if (tables.isEmpty()) {
            throw new BusinessException("Schema 缓存不存在，请先执行同步: POST /api/schema/cache/sync");
        }

        List<CachedTableInfo> tableInfos = new ArrayList<>();
        for (SchemaTable st : tables) {
            List<SchemaColumn> columns = schemaColumnMapper.selectList(
                    new LambdaQueryWrapper<SchemaColumn>()
                            .eq(SchemaColumn::getTableId, st.getId())
                            .orderByAsc(SchemaColumn::getOrdinalPosition));

            tableInfos.add(CachedTableInfo.builder()
                    .tableName(st.getTableName())
                    .tableComment(st.getTableComment())
                    .columns(columns.stream().map(this::toCachedColumnInfo).collect(Collectors.toList()))
                    .build());
        }

        log.info("从缓存读取 Schema: datasourceId={}, tables={}", datasourceId, tableInfos.size());
        return CachedSchemaResponse.builder().tables(tableInfos).build();
    }

    /**
     * 从缓存获取指定表的列名列表（供采样服务使用）
     *
     * @param datasourceId 数据源 ID
     * @param tableName    表名
     * @return 列名列表，缓存不存在返回空
     */
    public List<String> getCachedColumnNames(Long datasourceId, String tableName) {
        SchemaTable st = findTable(datasourceId, tableName);
        if (st == null) {
            return Collections.emptyList();
        }
        return schemaColumnMapper.selectList(
                        new LambdaQueryWrapper<SchemaColumn>()
                                .eq(SchemaColumn::getTableId, st.getId())
                                .orderByAsc(SchemaColumn::getOrdinalPosition))
                .stream()
                .map(SchemaColumn::getColumnName)
                .collect(Collectors.toList());
    }

    /**
     * 检查缓存是否存在
     */
    public boolean hasCache(Long datasourceId) {
        Long count = schemaTableMapper.selectCount(
                new LambdaQueryWrapper<SchemaTable>()
                        .eq(SchemaTable::getDatasourceId, datasourceId));
        return count != null && count > 0;
    }

    // ==================== 内部方法 ====================

    /**
     * 删除数据源的旧缓存（先删列再删表，保持外键顺序）
     */
    private void deleteOldCache(Long datasourceId) {
        List<SchemaTable> oldTables = schemaTableMapper.selectList(
                new LambdaQueryWrapper<SchemaTable>()
                        .eq(SchemaTable::getDatasourceId, datasourceId));

        if (oldTables.isEmpty()) {
            return;
        }

        // 先删所有列
        for (SchemaTable st : oldTables) {
            schemaColumnMapper.delete(
                    new LambdaQueryWrapper<SchemaColumn>()
                            .eq(SchemaColumn::getTableId, st.getId()));
        }

        // 再删所有表
        schemaTableMapper.delete(
                new LambdaQueryWrapper<SchemaTable>()
                        .eq(SchemaTable::getDatasourceId, datasourceId));

        log.info("已清除旧缓存: datasourceId={}, tables={}", datasourceId, oldTables.size());
    }

    /**
     * 写入表缓存
     */
    private SchemaTable insertTableCache(Long datasourceId, TableMeta tm) {
        SchemaTable st = new SchemaTable();
        st.setDatasourceId(datasourceId);
        st.setTableName(tm.tableName);
        st.setTableComment(tm.tableComment);
        st.setColumnCount(tm.columns.size());
        st.setSyncTime(LocalDateTime.now());
        schemaTableMapper.insert(st);
        return st;
    }

    /**
     * 写入列缓存
     */
    private int insertColumnCache(Long tableId, List<ColumnMeta> columns) {
        if (columns.isEmpty()) return 0;
        // 批量：逐条插入（MyBatis-Plus BaseMapper 不原生支持批量）
        for (ColumnMeta cm : columns) {
            SchemaColumn sc = new SchemaColumn();
            sc.setTableId(tableId);
            sc.setColumnName(cm.name);
            sc.setDataType(cm.dataType);
            sc.setColumnType(cm.columnType);
            sc.setMaxLength(cm.maxLength);
            sc.setNullable(cm.nullable);
            sc.setPrimaryKey(cm.primaryKey);
            sc.setDefaultValue(cm.defaultValue);
            sc.setColumnComment(cm.comment);
            sc.setOrdinalPosition(cm.ordinalPosition);
            sc.setForeignRefTable(cm.foreignRefTable);
            sc.setForeignRefColumn(cm.foreignRefColumn);
            schemaColumnMapper.insert(sc);
        }
        return columns.size();
    }

    /**
     * 根据 datasourceId + tableName 查找缓存表
     */
    private SchemaTable findTable(Long datasourceId, String tableName) {
        List<SchemaTable> list = schemaTableMapper.selectList(
                new LambdaQueryWrapper<SchemaTable>()
                        .eq(SchemaTable::getDatasourceId, datasourceId)
                        .eq(SchemaTable::getTableName, tableName));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 获取数据源配置（不存在抛 404）
     */
    private Datasource getDatasource(Long datasourceId) {
        Datasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        return ds;
    }

    // ==================== information_schema 读取 ====================

    /**
     * 读取所有表及其列信息
     */
    private List<TableMeta> readTableMetas(Connection conn, String dbName) throws SQLException {
        List<TableMeta> tables = new ArrayList<>();

        String tableSql = """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;

        try (PreparedStatement ps = conn.prepareStatement(tableSql)) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String comment = rs.getString("TABLE_COMMENT");
                    List<ColumnMeta> columns = readColumnMetas(conn, dbName, tableName);
                    tables.add(new TableMeta(tableName, comment, columns));
                }
            }
        }
        return tables;
    }

    /**
     * 读取单表的列信息
     */
    private List<ColumnMeta> readColumnMetas(Connection conn, String dbName, String tableName)
            throws SQLException {
        List<ColumnMeta> columns = new ArrayList<>();

        String colSql = """
                SELECT
                    c.COLUMN_NAME,
                    c.DATA_TYPE,
                    c.COLUMN_TYPE,
                    c.CHARACTER_MAXIMUM_LENGTH,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.COLUMN_COMMENT,
                    c.COLUMN_KEY,
                    c.ORDINAL_POSITION,
                    k.REFERENCED_TABLE_NAME,
                    k.REFERENCED_COLUMN_NAME
                FROM information_schema.COLUMNS c
                LEFT JOIN information_schema.KEY_COLUMN_USAGE k
                    ON k.TABLE_SCHEMA = c.TABLE_SCHEMA
                    AND k.TABLE_NAME = c.TABLE_NAME
                    AND k.COLUMN_NAME = c.COLUMN_NAME
                    AND k.REFERENCED_TABLE_NAME IS NOT NULL
                WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ?
                ORDER BY c.ORDINAL_POSITION
                """;

        try (PreparedStatement ps = conn.prepareStatement(colSql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(new ColumnMeta(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("DATA_TYPE"),
                            rs.getString("COLUMN_TYPE"),
                            getInt(rs, "CHARACTER_MAXIMUM_LENGTH"),
                            "YES".equals(rs.getString("IS_NULLABLE")),
                            "PRI".equals(rs.getString("COLUMN_KEY")),
                            rs.getString("COLUMN_DEFAULT"),
                            rs.getString("COLUMN_COMMENT"),
                            rs.getInt("ORDINAL_POSITION"),
                            rs.getString("REFERENCED_TABLE_NAME"),
                            rs.getString("REFERENCED_COLUMN_NAME")
                    ));
                }
            }
        }
        return columns;
    }

    private Integer getInt(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    // ==================== 转换 ====================

    private CachedColumnInfo toCachedColumnInfo(SchemaColumn sc) {
        return CachedColumnInfo.builder()
                .name(sc.getColumnName())
                .type(sc.getDataType())
                .primaryKey(sc.getPrimaryKey())
                .nullable(sc.getNullable())
                .defaultValue(sc.getDefaultValue())
                .comment(sc.getColumnComment())
                .ordinalPosition(sc.getOrdinalPosition())
                .build();
    }

    // ==================== 内部数据类 ====================

    private record TableMeta(String tableName, String tableComment, List<ColumnMeta> columns) {}

    private record ColumnMeta(
            String name,
            String dataType,
            String columnType,
            Integer maxLength,
            boolean nullable,
            boolean primaryKey,
            String defaultValue,
            String comment,
            int ordinalPosition,
            String foreignRefTable,
            String foreignRefColumn
    ) {}

    // ==================== 工具方法 ====================

    private String buildJdbcUrl(Datasource ds) {
        String dbType = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "mysql";
        return switch (dbType) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8"
                            + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                    ds.getHost(), ds.getPort(), ds.getDbName());
            default -> throw new BusinessException("暂不支持的数据库类型: " + ds.getDbType());
        };
    }
}
