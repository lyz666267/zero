package com.platform.generator.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INSERT 语句构建器安全测试 — 验证标识符白名单校验防 SQL 注入
 */
@DisplayName("INSERT 语句构建器 — 标识符安全校验")
class InsertStatementBuilderTest {

    private final InsertStatementBuilder builder = new InsertStatementBuilder();

    // ==================== 合法标识符测试 ====================

    @Nested
    @DisplayName("合法标识符应通过")
    class ValidIdentifiers {

        @ParameterizedTest
        @ValueSource(strings = {
                "users",
                "test_user",
                "department",
                "_private",
                "t1",
                "USER_TABLE",
                "a",
                "abc123_def456"
        })
        @DisplayName("合法表名应通过校验")
        void shouldAcceptValidTableNames(String tableName) {
            assertDoesNotThrow(() ->
                    builder.buildSql(tableName, List.of("id", "name")),
                    "合法表名 \"" + tableName + "\" 应通过校验");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "id",
                "username",
                "first_name",
                "_id",
                "c1",
                "COLUMN_NAME",
                "department_id"
        })
        @DisplayName("合法列名应通过校验")
        void shouldAcceptValidColumnNames(String colName) {
            assertDoesNotThrow(() ->
                    builder.buildSql("users", List.of(colName)),
                    "合法列名 \"" + colName + "\" 应通过校验");
        }

        @Test
        @DisplayName("多列合法标识符应全部通过")
        void shouldAcceptMultipleValidColumns() {
            String sql = assertDoesNotThrow(() ->
                    builder.buildSql("users", List.of("id", "username", "age", "email")));
            assertTrue(sql.contains("INSERT INTO users"));
            assertTrue(sql.contains("VALUES (?, ?, ?, ?)"));
        }
    }

    // ==================== 非法标识符测试 ====================

    @Nested
    @DisplayName("非法标识符应被拒绝（防 SQL 注入）")
    class InvalidIdentifiers {

        @ParameterizedTest
        @ValueSource(strings = {
                "",                    // 空字符串
                "users; DROP TABLE",   // 分号 + DROP
                "users--",             // SQL 注释
                "users; --",           // 分号 + 注释
                "users/*comment*/",    // 块注释
                "u s e r s",           // 空格
                "users'",              // 单引号
                "users\"",             // 双引号
                ";",                   // 纯分号
                "1=1",                 // 条件表达式
                "x) VALUES (1); --",   // 闭合括号注入
                "admin'--",            // 字符串闭合注入
        })
        @DisplayName("SQL 注入表名应被拒绝")
        void shouldRejectDangerousTableNames(String tableName) {
            assertThrows(IllegalArgumentException.class, () ->
                    builder.buildSql(tableName, List.of("id")),
                    "危险表名应被拒绝: \"" + tableName + "\"");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "col; DROP TABLE",
                "col--",
                "col name",
                "col'",
                "col\"",
                "1=1",
                "x) VALUES (1); --",
                "x, (SELECT password FROM users)--",
        })
        @DisplayName("SQL 注入列名应被拒绝")
        void shouldRejectDangerousColumnNames(String colName) {
            assertThrows(IllegalArgumentException.class, () ->
                    builder.buildSql("users", List.of(colName)),
                    "危险列名应被拒绝: \"" + colName + "\"");
        }

        @Test
        @DisplayName("混合合法/非法列名 — 非法列名应触发拒绝")
        void shouldRejectMixedColumns() {
            assertThrows(IllegalArgumentException.class, () ->
                    builder.buildSql("users", List.of("id", "name; DROP TABLE users--", "age")));
        }

        @Test
        @DisplayName("null 表名应被拒绝")
        void shouldRejectNullTableName() {
            assertThrows(IllegalArgumentException.class, () ->
                    builder.buildSql(null, List.of("id")));
        }

        @Test
        @DisplayName("null 列名列表应被拒绝")
        void shouldRejectNullColumns() {
            assertThrows(IllegalArgumentException.class, () ->
                    builder.buildSql("users", null));
        }

        @Test
        @DisplayName("空列名列表应被拒绝")
        void shouldRejectEmptyColumns() {
            assertThrows(IllegalArgumentException.class, () ->
                    builder.buildSql("users", List.of()));
        }
    }

    // ==================== validateIdentifier 单元测试 ====================

    @Nested
    @DisplayName("validateIdentifier 方法")
    class ValidateIdentifierMethod {

        @Test
        @DisplayName("null 标识符应抛异常")
        void shouldRejectNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    InsertStatementBuilder.validateIdentifier(null, "测试"));
        }

        @Test
        @DisplayName("仅空白字符应抛异常")
        void shouldRejectBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    InsertStatementBuilder.validateIdentifier("   ", "测试"));
        }

        @Test
        @DisplayName("错误消息应包含标签和非法值")
        void errorMessageShouldContainLabelAndValue() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    InsertStatementBuilder.validateIdentifier("bad;name", "列名"));
            assertTrue(ex.getMessage().contains("列名"), "应包含标签");
            assertTrue(ex.getMessage().contains("bad;name"), "应包含非法值");
        }
    }
}
