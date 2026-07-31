package com.platform.service;

import com.platform.entity.DataMaskTask;
import com.platform.exception.BusinessException;
import com.platform.mapper.DataMaskTaskMapper;
import com.platform.privacy.mask.MaskStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库脱敏服务测试
 *
 * <p>覆盖：SQL 生成、SQL 安全检查、执行流程（预览→确认→执行）</p>
 */
@SpringBootTest
@DisplayName("数据库脱敏服务测试")
class DatabaseMaskServiceTest {

    @Autowired
    private DatabaseMaskService maskService;

    @Autowired
    private DataMaskTaskMapper taskMapper;

    // ==================== SQL 生成测试 ====================

    @Nested
    @DisplayName("UPDATE SQL 生成 — 各策略")
    class SqlGeneration {

        @Test
        @DisplayName("手机号脱敏 SQL")
        void shouldGeneratePhoneMaskSql() {
            String sql = maskService.buildUpdateSql("users", "phone", MaskStrategy.PHONE_MASK);

            assertTrue(sql.contains("UPDATE `users`"));
            assertTrue(sql.contains("SET `phone`"));
            assertTrue(sql.contains("LEFT(`phone`, 3)"));
            assertTrue(sql.contains("'****'"));
            assertTrue(sql.contains("RIGHT(`phone`, 4)"));
            assertTrue(sql.contains("WHERE `phone` IS NOT NULL"));
            assertTrue(sql.contains("`phone` != ''"));
        }

        @Test
        @DisplayName("姓名脱敏 SQL")
        void shouldGenerateNameMaskSql() {
            String sql = maskService.buildUpdateSql("customers", "full_name", MaskStrategy.NAME_MASK);

            assertTrue(sql.contains("UPDATE `customers`"));
            assertTrue(sql.contains("SET `full_name`"));
            assertTrue(sql.contains("LEFT(`full_name`, 1)"));
            assertTrue(sql.contains("REPEAT('*', CHAR_LENGTH(`full_name`) - 1)"));
            assertTrue(sql.contains("WHERE `full_name` IS NOT NULL"));
        }

        @Test
        @DisplayName("身份证脱敏 SQL")
        void shouldGenerateIdCardMaskSql() {
            String sql = maskService.buildUpdateSql("users", "id_card", MaskStrategy.ID_CARD_MASK);

            assertTrue(sql.contains("UPDATE `users`"));
            assertTrue(sql.contains("SET `id_card`"));
            assertTrue(sql.contains("LEFT(`id_card`, 6)"));
            assertTrue(sql.contains("REPEAT('*', CHAR_LENGTH(`id_card`) - 10)"));
            assertTrue(sql.contains("RIGHT(`id_card`, 4)"));
        }

        @Test
        @DisplayName("邮箱脱敏 SQL")
        void shouldGenerateEmailMaskSql() {
            String sql = maskService.buildUpdateSql("users", "email", MaskStrategy.EMAIL_MASK);

            assertTrue(sql.contains("UPDATE `users`"));
            assertTrue(sql.contains("SET `email`"));
            assertTrue(sql.contains("INSTR(`email`, '@')"));
            assertTrue(sql.contains("'***'"));
        }

        @Test
        @DisplayName("地址脱敏 SQL")
        void shouldGenerateAddressMaskSql() {
            String sql = maskService.buildUpdateSql("users", "address", MaskStrategy.ADDRESS_MASK);

            assertTrue(sql.contains("UPDATE `users`"));
            assertTrue(sql.contains("SET `address`"));
            assertTrue(sql.contains("GREATEST(0, CHAR_LENGTH(`address`) - 6)"));
        }

        @Test
        @DisplayName("银行卡脱敏 SQL")
        void shouldGenerateBankCardMaskSql() {
            String sql = maskService.buildUpdateSql("users", "bank_card", MaskStrategy.BANK_CARD_MASK);

            assertTrue(sql.contains("UPDATE `users`"));
            assertTrue(sql.contains("SET `bank_card`"));
            assertTrue(sql.contains("'****'"));
            assertTrue(sql.contains("RIGHT(`bank_card`, 4)"));
        }

        @Test
        @DisplayName("所有策略均以 UPDATE 开头")
        void allStrategiesShouldStartWithUpdate() {
            for (MaskStrategy strategy : MaskStrategy.values()) {
                String sql = maskService.buildUpdateSql("t", "c", strategy);
                assertTrue(sql.startsWith("UPDATE "),
                        strategy.name() + " should start with UPDATE, got: " + sql);
            }
        }

        @Test
        @DisplayName("SQL 包含表名和列名的反引号转义")
        void sqlShouldEscapeIdentifiers() {
            String sql = maskService.buildUpdateSql("users", "phone", MaskStrategy.PHONE_MASK);

            assertTrue(sql.contains("`users`"), "table should be backtick-escaped");
            assertTrue(sql.contains("`phone`"), "column should be backtick-escaped");
        }
    }

    // ==================== SQL 安全检查测试 ====================

    @Nested
    @DisplayName("SQL 安全检查")
    class SqlSafetyValidation {

        @Test
        @DisplayName("合法 UPDATE SQL 应通过检查")
        void shouldPassValidUpdateSql() {
            String sql = "UPDATE `users` SET `phone` = CONCAT(LEFT(`phone`,3), '****', RIGHT(`phone`,4)) WHERE `phone` IS NOT NULL;";
            assertDoesNotThrow(() -> maskService.validateSqlSafety(sql, "users"));
        }

        @Test
        @DisplayName("多条合法 UPDATE 应通过检查")
        void shouldPassMultipleValidUpdates() {
            String sql = "UPDATE `users` SET `phone` = '***' WHERE `phone` IS NOT NULL;\n" +
                         "UPDATE `users` SET `email` = '***' WHERE `email` IS NOT NULL;";
            assertDoesNotThrow(() -> maskService.validateSqlSafety(sql, "users"));
        }

        @Test
        @DisplayName("包含 DROP 的 SQL 应被拒绝")
        void shouldRejectDropStatement() {
            String sql = "DROP TABLE users; UPDATE `users` SET `phone` = '***';";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("DROP"), "应该提示禁止 DROP");
        }

        @Test
        @DisplayName("包含 DELETE 的 SQL 应被拒绝")
        void shouldRejectDeleteStatement() {
            String sql = "DELETE FROM users; UPDATE `users` SET `phone` = '***';";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("DELETE"), "应该提示禁止 DELETE");
        }

        @Test
        @DisplayName("包含 TRUNCATE 的 SQL 应被拒绝")
        void shouldRejectTruncateStatement() {
            String sql = "TRUNCATE TABLE users;";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("TRUNCATE"), "应该提示禁止 TRUNCATE");
        }

        @Test
        @DisplayName("包含 ALTER 的 SQL 应被拒绝")
        void shouldRejectAlterStatement() {
            String sql = "ALTER TABLE users DROP COLUMN phone;";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("ALTER"), "应该提示禁止 ALTER");
        }

        @Test
        @DisplayName("包含 CREATE 的 SQL 应被拒绝")
        void shouldRejectCreateStatement() {
            String sql = "CREATE TABLE hack (id INT); UPDATE `users` SET `phone` = '***';";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("CREATE"), "应该提示禁止 CREATE");
        }

        @Test
        @DisplayName("包含 INSERT 的 SQL 应被拒绝")
        void shouldRejectInsertStatement() {
            String sql = "INSERT INTO users VALUES (1); UPDATE `users` SET `phone` = '***';";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("INSERT"), "应该提示禁止 INSERT");
        }

        @Test
        @DisplayName("表名不匹配的 UPDATE 应被拒绝")
        void shouldRejectWrongTableUpdate() {
            String sql = "UPDATE `orders` SET `phone` = '***' WHERE `phone` IS NOT NULL;";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("users"), "应该提示表名不匹配");
        }

        @Test
        @DisplayName("空 SQL 应被拒绝")
        void shouldRejectEmptySql() {
            assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety("", "users"));
            assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(null, "users"));
        }

        @Test
        @DisplayName("非 UPDATE 开头的语句应被拒绝")
        void shouldRejectNonUpdateStatement() {
            String sql = "SELECT * FROM `users`;";
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.validateSqlSafety(sql, "users"));
            assertTrue(ex.getMessage().contains("仅允许 UPDATE"), "应该提示仅允许 UPDATE");
        }
    }

    // ==================== 表名格式校验测试 ====================

    @Nested
    @DisplayName("表名格式校验 — SQL 注入防护")
    class TableNameValidation {

        // --- 合法表名 ---

        @Test
        @DisplayName("合法表名 user 应通过校验")
        void shouldAcceptValidTableName() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("user");

            // 会先通过表名校验，然后因为数据源（测试环境用ID=1大概率不存在）或连接失败而抛异常
            // 但不应抛出"表名格式不合法"
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertFalse(ex.getMessage().contains("表名格式不合法"),
                    "合法表名不应触发格式校验: " + ex.getMessage());
        }

        @Test
        @DisplayName("下划线开头的表名 _temp 应通过校验")
        void shouldAcceptUnderscorePrefix() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("_temp");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertFalse(ex.getMessage().contains("表名格式不合法"),
                    "下划线开头的表名应通过校验: " + ex.getMessage());
        }

        @Test
        @DisplayName("复合表名 t_order_2024 应通过校验")
        void shouldAcceptCompoundTableName() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("t_order_2024");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertFalse(ex.getMessage().contains("表名格式不合法"),
                    "合法的复合表名应通过校验: " + ex.getMessage());
        }

        // --- 恶意表名 ---

        @Test
        @DisplayName("SQL 注入 payload — DROP TABLE 应被拒绝")
        void shouldRejectDropTablePayload() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("users; DROP TABLE users;--");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("表名格式不合法"),
                    "含 DROP TABLE 的注入应被格式校验拒绝: " + ex.getMessage());
        }

        @Test
        @DisplayName("SQL 注入 payload — SELECT 子查询应被拒绝")
        void shouldRejectSelectPayload() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("users; SELECT * FROM information_schema.TABLES;--");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("表名格式不合法"),
                    "含 SELECT 的注入应被格式校验拒绝: " + ex.getMessage());
        }

        @Test
        @DisplayName("SQL 注入 payload — 反引号逃逸应被拒绝")
        void shouldRejectBacktickEscape() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("users`; DROP TABLE users;--");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("表名格式不合法"),
                    "含反引号逃逸的注入应被格式校验拒绝: " + ex.getMessage());
        }

        @Test
        @DisplayName("SQL 注入 payload — 空格分号应被拒绝")
        void shouldRejectSpaceAndSemicolon() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("users ; drop");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("表名格式不合法"),
                    "含空格的表名应被格式校验拒绝: " + ex.getMessage());
        }

        // --- 边界条件 ---

        @Test
        @DisplayName("纯数字表名应被拒绝")
        void shouldRejectNumericOnlyTableName() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("12345");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("表名格式不合法"),
                    "纯数字表名应被格式校验拒绝: " + ex.getMessage());
        }

        @Test
        @DisplayName("含特殊字符的表名应被拒绝（/**/注释注入）")
        void shouldRejectCommentInjection() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            request.setTableName("users/**/where");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("表名格式不合法"),
                    "含注释符号的表名应被格式校验拒绝: " + ex.getMessage());
        }
    }

    // ==================== 执行流程测试 ====================

    @Nested
    @DisplayName("执行流程 — 预览→确认→执行")
    class ExecutionFlow {

        @Test
        @DisplayName("预览失败 — 缺少 datasourceId")
        void previewShouldFailWithoutDatasourceId() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setTableName("users");
            // datasourceId = null

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("数据源"));
        }

        @Test
        @DisplayName("预览失败 — 缺少 tableName")
        void previewShouldFailWithoutTableName() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(1L);
            // tableName = null

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("表名"));
        }

        @Test
        @DisplayName("预览失败 — 数据源不存在")
        void previewShouldFailForMissingDatasource() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setDatasourceId(99999L);
            request.setTableName("users");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.preview(request));
            assertTrue(ex.getMessage().contains("数据源不存在"));
        }

        @Test
        @DisplayName("执行失败 — 缺少 taskId")
        void executeShouldFailWithoutTaskId() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            // taskId = null

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.execute(request));
            assertTrue(ex.getMessage().contains("任务 ID"));
        }

        @Test
        @DisplayName("执行失败 — 任务不存在")
        void executeShouldFailForMissingTask() {
            var request = new com.platform.dto.DatabaseMaskRequest();
            request.setTaskId(99999L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.execute(request));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("查询任务 — 不存在时抛异常")
        void getTaskShouldFailForMissingTask() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> maskService.getTask(99999L));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("执行失败 — 状态不是 PREVIEW")
        void executeShouldFailIfNotPreview() {
            // 创建一个非 PREVIEW 状态的任务
            DataMaskTask task = DataMaskTask.builder()
                    .datasourceId(1L)
                    .tableName("users")
                    .status("SUCCESS")
                    .sqlPreview("UPDATE `users` SET `phone` = '***' WHERE `phone` IS NOT NULL;")
                    .build();
            taskMapper.insert(task);

            try {
                var request = new com.platform.dto.DatabaseMaskRequest();
                request.setTaskId(task.getId());

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> maskService.execute(request));
                assertTrue(ex.getMessage().contains("PREVIEW"),
                        "应该提示需要 PREVIEW 状态, got: " + ex.getMessage());
            } finally {
                taskMapper.deleteById(task.getId());
            }
        }

        @Test
        @DisplayName("执行失败 — 无 SQL 预览")
        void executeShouldFailWithoutSqlPreview() {
            DataMaskTask task = DataMaskTask.builder()
                    .datasourceId(1L)
                    .tableName("users")
                    .status("PREVIEW")
                    .sqlPreview(null)
                    .build();
            taskMapper.insert(task);

            try {
                var request = new com.platform.dto.DatabaseMaskRequest();
                request.setTaskId(task.getId());

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> maskService.execute(request));
                assertTrue(ex.getMessage().contains("SQL"),
                        "应该提示没有可执行的 SQL");
            } finally {
                taskMapper.deleteById(task.getId());
            }
        }
    }
}
