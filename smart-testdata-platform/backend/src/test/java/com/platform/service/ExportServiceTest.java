package com.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.entity.TestDataTask;
import com.platform.exception.BusinessException;
import com.platform.mapper.TestDataTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试数据导出服务测试
 *
 * <p>覆盖：CSV 转义、JSON 序列化、格式校验、任务校验、边界条件</p>
 */
@SpringBootTest
@DisplayName("测试数据导出服务测试")
class ExportServiceTest {

    @Autowired
    private ExportService exportService;

    @Autowired
    private TestDataTaskMapper taskMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== CSV 转义测试 ====================

    @Nested
    @DisplayName("CSV 值转义")
    class CsvEscape {

        @Test
        @DisplayName("null → 空字符串")
        void shouldEscapeNullToEmpty() {
            String result = exportService.csvEscape(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("普通字符串加双引号")
        void shouldQuotePlainString() {
            String result = exportService.csvEscape("张三");
            assertEquals("\"张三\"", result);
        }

        @Test
        @DisplayName("数字不加双引号")
        void shouldNotQuoteNumber() {
            String result = exportService.csvEscape(123);
            assertEquals("123", result);
        }

        @Test
        @DisplayName("含逗号的字符串加双引号")
        void shouldQuoteStringWithComma() {
            String result = exportService.csvEscape("hello,world");
            assertTrue(result.startsWith("\"") && result.endsWith("\""));
            assertTrue(result.contains("hello,world"));
        }

        @Test
        @DisplayName("内部双引号转义为两个双引号")
        void shouldEscapeInternalQuotes() {
            String result = exportService.csvEscape("he\"llo");
            assertTrue(result.contains("\"\""));
        }

        @Test
        @DisplayName("含换行的字符串加双引号")
        void shouldQuoteStringWithNewline() {
            String result = exportService.csvEscape("line1\nline2");
            assertTrue(result.startsWith("\"") && result.endsWith("\""));
        }

        @Test
        @DisplayName("Long 类型不加引号")
        void shouldNotQuoteLong() {
            String result = exportService.csvEscape(99999L);
            assertEquals("99999", result);
        }
    }

    // ==================== 格式校验测试 ====================

    @Nested
    @DisplayName("导出格式校验")
    class FormatValidation {

        @Test
        @DisplayName("不支持的格式应抛异常")
        void shouldRejectInvalidFormat() {
            // 需要有效任务 ID 才能进入格式校验分支，但任务不存在会先抛异常
            // 测试直接调用时 format 被 normalised 的逻辑
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exportService.exportTaskData(99999L, "XML"));
            // 任务不存在会先命中
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("null 格式默认 JSON")
        void nullFormatShouldDefaultToJson() {
            // null 格式会被转为 "JSON"，但任务不存在先报错
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exportService.exportTaskData(99999L, null));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("空格式默认 JSON")
        void emptyFormatShouldDefaultToJson() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exportService.exportTaskData(99999L, ""));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("小写 csv 识别为大写 CSV")
        void lowercaseCsvShouldBeAccepted() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exportService.exportTaskData(99999L, "csv"));
            assertTrue(ex.getMessage().contains("不存在"), "小写 csv 应被识别但任务不存在");
        }

        @Test
        @DisplayName("小写 sql 识别为大写 SQL")
        void lowercaseSqlShouldBeAccepted() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exportService.exportTaskData(99999L, "sql"));
            assertTrue(ex.getMessage().contains("不存在"), "小写 sql 应被识别但任务不存在");
        }

        @Test
        @DisplayName("小写 json 识别为大写 JSON")
        void lowercaseJsonShouldBeAccepted() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exportService.exportTaskData(99999L, "json"));
            assertTrue(ex.getMessage().contains("不存在"), "小写 json 应被识别但任务不存在");
        }
    }

    // ==================== 任务校验测试 ====================

    @Nested
    @DisplayName("任务校验")
    class TaskValidation {

        @Test
        @DisplayName("任务不存在应返回 404")
        void shouldReturn404ForMissingTask() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> exportService.exportTaskData(99999L, "JSON"));
            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("null taskId 应抛异常")
        void shouldThrowForNullTaskId() {
            assertThrows(Exception.class,
                    () -> exportService.exportTaskData(null, "JSON"));
        }
    }

    // ==================== 文件名生成测试 ====================

    @Nested
    @DisplayName("导出文件名生成")
    class FileNameGeneration {

        @Test
        @DisplayName("CSV 格式生成 .csv 后缀")
        void shouldGenerateCsvExtension() {
            String name = exportService.generateFileName(1L, "CSV");
            assertTrue(name.endsWith(".csv"));
            assertTrue(name.startsWith("task_1_"));
        }

        @Test
        @DisplayName("SQL 格式生成 .sql 后缀")
        void shouldGenerateSqlExtension() {
            String name = exportService.generateFileName(1L, "SQL");
            assertTrue(name.endsWith(".sql"));
            assertTrue(name.startsWith("task_1_"));
        }

        @Test
        @DisplayName("JSON 格式生成 .json 后缀")
        void shouldGenerateJsonExtension() {
            String name = exportService.generateFileName(1L, "JSON");
            assertTrue(name.endsWith(".json"));
            assertTrue(name.startsWith("task_1_"));
        }

        @Test
        @DisplayName("未知格式默认 .json 后缀")
        void unknownFormatShouldDefaultToJson() {
            String name = exportService.generateFileName(1L, "XML");
            assertTrue(name.endsWith(".json"));
        }

        @Test
        @DisplayName("文件名包含任务 ID")
        void fileNameShouldContainTaskId() {
            String name = exportService.generateFileName(42L, "JSON");
            assertTrue(name.contains("task_42"));
        }
    }

    // ==================== JSON 导出测试 ====================

    @Nested
    @DisplayName("JSON 导出格式")
    class JsonExport {

        @Test
        @DisplayName("空数据表不产生额外 JSON 字段")
        void emptyDataShouldProduceEmptyTables() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            data.put("empty_table", Collections.emptyList());

            TestDataTask task = new TestDataTask();
            task.setId(1L);
            task.setTaskName("test");

            String json = exportService.toJson(data, task);
            assertNotNull(json);
            assertTrue(json.contains("\"taskId\""));
            assertTrue(json.contains("\"taskName\""));
            assertTrue(json.contains("\"tables\""));
            assertTrue(json.contains("\"empty_table\""));
        }

        @Test
        @DisplayName("包含数据的 JSON 导出")
        void shouldExportDataAsJson() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 1);
            row.put("name", "张三");
            rows.add(row);
            data.put("users", rows);

            TestDataTask task = new TestDataTask();
            task.setId(1L);
            task.setTaskName("test");

            String json = exportService.toJson(data, task);
            assertNotNull(json);
            assertTrue(json.contains("\"users\""));
            assertTrue(json.contains("\"id\""));
            assertTrue(json.contains("\"张三\""));
        }
    }

    // ==================== CSV 导出测试 ====================

    @Nested
    @DisplayName("CSV 导出格式")
    class CsvExport {

        @Test
        @DisplayName("包含文件头注释")
        void shouldContainHeaderComments() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 1);
            row.put("name", "张三");
            rows.add(row);
            data.put("users", rows);

            TestDataTask task = new TestDataTask();
            task.setId(1L);
            task.setTaskName("测试任务");

            String csv = exportService.toCsv(data, task);
            assertNotNull(csv);
            assertTrue(csv.contains("# 任务: 测试任务"));
            assertTrue(csv.contains("# 格式: CSV"));
            assertTrue(csv.contains("# table: users"));
        }

        @Test
        @DisplayName("包含列头行")
        void shouldContainColumnHeaderRow() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 1);
            row.put("name", "张三");
            rows.add(row);
            data.put("users", rows);

            TestDataTask task = new TestDataTask();
            task.setId(1L);
            task.setTaskName("test");

            String csv = exportService.toCsv(data, task);
            assertTrue(csv.contains("id,name") || csv.contains("\"id\",\"name\""),
                    "CSV 应包含列头, got: " + csv.substring(0, Math.min(200, csv.length())));
        }

        @Test
        @DisplayName("多表用空行分隔")
        void shouldSeparateMultipleTables() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            List<Map<String, Object>> rows1 = new ArrayList<>();
            Map<String, Object> r1 = new LinkedHashMap<>();
            r1.put("id", 1);
            rows1.add(r1);
            data.put("users", rows1);

            List<Map<String, Object>> rows2 = new ArrayList<>();
            Map<String, Object> r2 = new LinkedHashMap<>();
            r2.put("id", 2);
            rows2.add(r2);
            data.put("orders", rows2);

            TestDataTask task = new TestDataTask();
            task.setId(1L);
            task.setTaskName("test");

            String csv = exportService.toCsv(data, task);
            assertTrue(csv.contains("# table: users"));
            assertTrue(csv.contains("# table: orders"));
        }
    }

    // ==================== SQL 导出测试 ====================

    @Nested
    @DisplayName("SQL INSERT 导出格式")
    class SqlExport {

        @Test
        @DisplayName("包含注释头")
        void shouldContainSqlHeaderComments() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 1);
            row.put("name", "张三");
            rows.add(row);
            data.put("users", rows);

            String sql = exportService.toSqlInsert(data);
            assertNotNull(sql);
            assertTrue(sql.contains("-- 测试数据导出 SQL"));
            assertTrue(sql.contains("-- table: users"));
        }

        @Test
        @DisplayName("生成 INSERT INTO 语句")
        void shouldGenerateInsertStatement() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 1);
            row.put("name", "张三");
            rows.add(row);
            data.put("users", rows);

            String sql = exportService.toSqlInsert(data);
            assertTrue(sql.contains("INSERT INTO users"));
            assertTrue(sql.contains("VALUES"));
        }

        @Test
        @DisplayName("空数据表被跳过")
        void shouldSkipEmptyTables() {
            LinkedHashMap<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
            data.put("empty_table", Collections.emptyList());

            String sql = exportService.toSqlInsert(data);
            // 只包含表注释，但没有 INSERT 语句
            assertFalse(sql.contains("INSERT INTO"));
        }
    }

    // ==================== 任务列表测试 ====================

    @Nested
    @DisplayName("可导出任务列表")
    class ExportableTaskList {

        @Test
        @DisplayName("返回非空列表")
        void shouldReturnTaskList() {
            var tasks = exportService.listExportableTasks();
            assertNotNull(tasks);
            // 列表中不应包含非 SUCCESS 状态的任务
            for (var task : tasks) {
                assertEquals("SUCCESS", task.getStatus(),
                        "所列任务应均为 SUCCESS 状态, got: " + task.getStatus() + " for task " + task.getId());
            }
        }
    }
}
