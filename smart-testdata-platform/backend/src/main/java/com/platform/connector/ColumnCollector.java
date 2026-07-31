package com.platform.connector;

import com.platform.entity.schema.SchemaColumn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库列信息收集器 — 统一的 information_schema.COLUMNS 读取入口
 *
 * <h3>职责</h3>
 * <p>从 information_schema 读取表的列元数据并返回 {@link SchemaColumn} 实体列表。
 * 项目中所有需要读取列信息的服务应通过此类统一获取，避免重复的 JDBC 查询代码。</p>
 *
 * <h3>安全</h3>
 * <ul>
 *   <li>使用参数化查询（PreparedStatement）防止 SQL 注入</li>
 *   <li>仅执行 SELECT 查询，不修改目标库数据</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Autowired
 * private ColumnCollector columnCollector;
 *
 * List&lt;SchemaColumn&gt; columns = columnCollector.readColumns(conn, dbName, tableName);
 * </pre>
 */
@Slf4j
@Component
public class ColumnCollector {

    /**
     * 读取指定表的所有列信息
     *
     * @param conn      JDBC 连接（调用方负责关闭）
     * @param dbName    数据库名
     * @param tableName 表名
     * @return 列信息列表，按 ORDINAL_POSITION 排序
     * @throws SQLException 数据库查询失败
     */
    public List<SchemaColumn> readColumns(Connection conn, String dbName, String tableName)
            throws SQLException {
        String sql = """
                SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;

        List<SchemaColumn> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SchemaColumn col = new SchemaColumn();
                    col.setColumnName(rs.getString("COLUMN_NAME"));
                    col.setDataType(rs.getString("DATA_TYPE"));
                    col.setColumnComment(rs.getString("COLUMN_COMMENT"));
                    col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                    col.setPrimaryKey("PRI".equals(rs.getString("COLUMN_KEY")));
                    columns.add(col);
                }
            }
        }
        log.debug("ColumnCollector: 表 {}.{} → {} 列", dbName, tableName, columns.size());
        return columns;
    }
}
