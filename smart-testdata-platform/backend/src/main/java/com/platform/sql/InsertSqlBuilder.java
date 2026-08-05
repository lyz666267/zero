package com.platform.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SQL INSERT 语句构建器 — 将生成的数据转换为可执行的 INSERT SQL
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>单行 / 批量 INSERT 语句生成</li>
 *   <li>自动类型识别：String、Integer、Long、BigDecimal、Boolean、Date</li>
 *   <li>SQL 字符串转义（单引号 → 双引号）</li>
 *   <li>NULL 值处理</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>
 * 输入: table="user", data=[{name:"张三", age:20}]
 * 输出: INSERT INTO user (name, age) VALUES ('张三', 20);
 * </pre>
 */
@Slf4j
@Service
public class InsertSqlBuilder {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 构建 INSERT SQL 语句
     *
     * @param tableName 目标表名
     * @param data      数据行列表
     * @return INSERT SQL 语句（多条数据合并为单条批量 INSERT）
     * @throws IllegalArgumentException 数据为空时抛出
     */
    public String build(String tableName, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("数据不能为空");
        }
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("表名不能为空");
        }

        // 1. 收集所有列名（保持首次出现顺序）
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : data) {
            if (row != null) {
                for (String col : row.keySet()) {
                    if (seen.add(col)) {
                        columns.add(col);
                    }
                }
            }
        }

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("未找到任何列");
        }

        // 2. 构建列名部分
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(quoteIdentifier(tableName)).append("\n(");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteIdentifier(columns.get(i)));
        }
        sql.append(")\nVALUES\n");

        // 3. 构建值部分（批量）
        List<String> valueRows = new ArrayList<>();
        for (Map<String, Object> row : data) {
            valueRows.add(buildValueRow(columns, row));
        }
        sql.append(String.join(",\n", valueRows));
        sql.append(";");

        return sql.toString();
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 构建单行值
     */
    private String buildValueRow(List<String> columns, Map<String, Object> row) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            String col = columns.get(i);
            Object value = row != null ? row.get(col) : null;
            sb.append(formatValue(value));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 将 Java 值格式化为 SQL 字面量
     *
     * <ul>
     *   <li>null → NULL</li>
     *   <li>String → 'escaped'</li>
     *   <li>Integer / Long / BigDecimal → 直接输出数字</li>
     *   <li>Boolean → 1 / 0</li>
     *   <li>Date → 'yyyy-MM-dd HH:mm:ss'</li>
     *   <li>其他类型 → toString() 后加引号转义</li>
     * </ul>
     */
    String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String s) {
            return "'" + escapeSqlString(s) + "'";
        }

        if (value instanceof Integer || value instanceof Long || value instanceof BigDecimal) {
            return value.toString();
        }

        if (value instanceof Boolean b) {
            return b ? "1" : "0";
        }

        if (value instanceof Date d) {
            return "'" + DATE_FORMAT.format(d) + "'";
        }

        // 兜底：转为字符串并加引号转义
        return "'" + escapeSqlString(value.toString()) + "'";
    }

    /**
     * SQL 字符串转义：单引号 → 两个单引号
     *
     * <pre>
     * "张三'测试" → "张三''测试"
     * </pre>
     */
    String escapeSqlString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("'", "''");
    }
}
