package com.platform.generator.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * INSERT 语句构建器 — 生成参数化的 INSERT SQL（使用 ? 占位符，安全防注入）
 *
 * <h3>示例</h3>
 * <pre>
 * tableName = "user", columns = ["name", "age"]
 *   → "INSERT INTO user (name, age) VALUES (?, ?)"
 * </pre>
 *
 * <p>返回的 SQL 使用 ? 占位符，配合 {@code JdbcTemplate.batchUpdate} 绑定参数值，
 * 不拼接用户数据，安全防 SQL 注入。</p>
 *
 * <h3>标识符安全校验</h3>
 * <p>表名和列名必须匹配 {@code ^[a-zA-Z_][a-zA-Z0-9_]*$}（标准 SQL 标识符），
 * 防止恶意输入如 {@code users; DROP TABLE x--} 被拼接入 SQL。</p>
 */
@Slf4j
@Component
public class InsertStatementBuilder {

    /** 合法 SQL 标识符模式：字母或下划线开头，后续为字母、数字、下划线 */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 构建参数化 INSERT SQL
     *
     * @param tableName 目标表名
     * @param columns   列名列表（保持顺序）
     * @return INSERT INTO table (col1, col2) VALUES (?, ?, ?)
     * @throws IllegalArgumentException 表名或列名包含非法字符
     */
    public String buildSql(String tableName, List<String> columns) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("列名不能为空");
        }

        // 安全校验：表名必须为合法 SQL 标识符
        validateIdentifier(tableName, "表名");

        // 安全校验：所有列名必须为合法 SQL 标识符
        for (String col : columns) {
            validateIdentifier(col, "列名");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");

        // 列名部分
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(columns.get(i));
        }

        sql.append(") VALUES (");

        // 占位符部分
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }

        sql.append(")");

        log.debug("构建参数化 SQL: {}", sql);
        return sql.toString();
    }

    /**
     * 校验 SQL 标识符是否合法
     *
     * <p>仅允许字母、数字、下划线；必须以字母或下划线开头。
     * 拒绝包含分号、空格、注释符、引号等可用于 SQL 注入的特殊字符。</p>
     *
     * @param identifier 待校验的标识符
     * @param label      错误提示标签（如"表名"、"列名"）
     * @throws IllegalArgumentException 标识符不合法
     */
    static void validateIdentifier(String identifier, String label) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (!VALID_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    label + "包含非法字符: \"" + identifier + "\"，仅允许字母、数字和下划线");
        }
    }
}
