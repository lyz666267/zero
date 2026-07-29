package com.platform.connector;

import com.platform.dto.SchemaResponse;
import com.platform.dto.SchemaResponse.ColumnInfo;
import com.platform.dto.SchemaResponse.TableInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库元数据读取器 — 通过 JDBC 查询 information_schema
 */
@Slf4j
@Component
public class MetadataReader {

    /**
     * 读取指定数据库的完整 Schema 信息
     *
     * @param url      JDBC 连接 URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @param dbName   数据库名
     * @param dbType   数据库类型
     * @return Schema 结构信息
     */
    public SchemaResponse readSchema(String url, String username, String password,
                                     String dbName, String dbType) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            List<TableInfo> tables = readTables(conn, dbName);
            return SchemaResponse.builder()
                    .database(dbName)
                    .dbType(dbType)
                    .tables(tables)
                    .build();
        }
    }

    /**
     * 测试数据库连接
     */
    public boolean testConnection(String url, String username, String password) {
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            return conn.isValid(3);
        } catch (SQLException e) {
            log.warn("数据库连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 读取数据库中所有用户表
     */
    private List<TableInfo> readTables(Connection conn, String dbName) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();

        String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String comment = rs.getString("TABLE_COMMENT");

                    List<ColumnInfo> columns = readColumns(conn, dbName, tableName);
                    tables.add(TableInfo.builder()
                            .tableName(tableName)
                            .comment(comment)
                            .columns(columns)
                            .build());
                }
            }
        }
        return tables;
    }

    /**
     * 读取单表的所有列信息
     */
    private List<ColumnInfo> readColumns(Connection conn, String dbName,
                                         String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();

        String sql = """
                SELECT
                    c.COLUMN_NAME,
                    c.DATA_TYPE,
                    c.CHARACTER_MAXIMUM_LENGTH,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.COLUMN_COMMENT,
                    c.COLUMN_KEY,
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

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean isPrimary = "PRI".equals(rs.getString("COLUMN_KEY"));
                    columns.add(ColumnInfo.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .type(rs.getString("DATA_TYPE"))
                            .length(getInt(rs, "CHARACTER_MAXIMUM_LENGTH"))
                            .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                            .defaultValue(rs.getString("COLUMN_DEFAULT"))
                            .primary(isPrimary)
                            .comment(rs.getString("COLUMN_COMMENT"))
                            .foreignRefTable(rs.getString("REFERENCED_TABLE_NAME"))
                            .foreignRefColumn(rs.getString("REFERENCED_COLUMN_NAME"))
                            .build());
                }
            }
        }
        return columns;
    }

    private Integer getInt(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }
}
