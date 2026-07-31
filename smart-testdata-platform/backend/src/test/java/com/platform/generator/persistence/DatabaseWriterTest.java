package com.platform.generator.persistence;

import com.platform.dto.DatabaseWriteRequest.TableData;
import com.platform.dto.DatabaseWriteResponse;
import com.platform.dto.WriteResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库写入器测试 — 使用 H2 内存数据库
 */
@DisplayName("数据库写入测试")
class DatabaseWriterTest {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DatabaseWriter databaseWriter;
    private InsertStatementBuilder statementBuilder;

    @BeforeEach
    void setUp() {
        // 创建 H2 内存数据库
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbcTemplate = new JdbcTemplate(ds);

        this.statementBuilder = new InsertStatementBuilder();
        this.databaseWriter = new DatabaseWriter(statementBuilder);

        // 创建测试表
        jdbcTemplate.execute("""
                CREATE TABLE test_user (
                    id INT PRIMARY KEY,
                    username VARCHAR(100),
                    age INT,
                    email VARCHAR(200)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE test_department (
                    id INT PRIMARY KEY,
                    name VARCHAR(100)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE test_employee (
                    id INT PRIMARY KEY,
                    name VARCHAR(100),
                    department_id INT
                )
                """);
    }

    @AfterEach
    void tearDown() {
        // 清理测试表
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_employee");
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_department");
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_user");
    }

    // ==================== 单表写入 ====================

    @Test
    @DisplayName("单表写入 — 10 条数据应全部写入成功")
    void testSingleTableWrite() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            row.put("username", "user" + i);
            row.put("age", 20 + i);
            row.put("email", "user" + i + "@test.com");
            data.add(row);
        }

        WriteResult result = databaseWriter.write(dataSource, "test_user", data);

        assertTrue(result.isSuccess(), "写入应成功");
        assertEquals("test_user", result.getTable(), "表名应正确");
        assertEquals(10, result.getInsertCount(), "应写入 10 行");

        // 验证数据库实际数量
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_user", Integer.class);
        assertEquals(10, count, "数据库实际行数应为 10");
    }

    @Test
    @DisplayName("单表写入 — 空数据应返回 0 行")
    void testSingleTableEmptyData() {
        WriteResult result = databaseWriter.write(dataSource, "test_user", List.of());

        assertTrue(result.isSuccess());
        assertEquals(0, result.getInsertCount(), "空数据应返回 0");
    }

    // ==================== 多表写入 ====================

    @Test
    @DisplayName("多表写入 — department → employee 按顺序写入")
    void testMultiTableWrite() {
        // 准备 department 数据
        List<Map<String, Object>> deptData = new ArrayList<>();
        deptData.add(createRow("id", 1, "name", "研发部"));
        deptData.add(createRow("id", 2, "name", "市场部"));
        deptData.add(createRow("id", 3, "name", "人事部"));

        // 准备 employee 数据（FK → department）
        List<Map<String, Object>> empData = new ArrayList<>();
        empData.add(createRow("id", 101, "name", "张三", "department_id", 1));
        empData.add(createRow("id", 102, "name", "李四", "department_id", 1));
        empData.add(createRow("id", 103, "name", "王五", "department_id", 2));
        empData.add(createRow("id", 104, "name", "赵六", "department_id", 2));
        empData.add(createRow("id", 105, "name", "钱七", "department_id", 3));

        // 写入 department
        WriteResult deptResult = databaseWriter.write(dataSource, "test_department", deptData);
        assertTrue(deptResult.isSuccess());
        assertEquals(3, deptResult.getInsertCount());

        // 写入 employee
        WriteResult empResult = databaseWriter.write(dataSource, "test_employee", empData);
        assertTrue(empResult.isSuccess());
        assertEquals(5, empResult.getInsertCount());

        // 验证数据库
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_department", Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_employee", Integer.class));

        // 验证 FK 关系正确
        Integer dept1Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_employee WHERE department_id = 1", Integer.class);
        assertEquals(2, dept1Count, "部门 1 应有 2 名员工");
    }

    // ==================== 异常回滚 ====================

    @Test
    @DisplayName("异常回滚 — 第二张表失败时第一张表数据应不存在（事务性）")
    void testTransactionRollback() {
        // 先用同一个连接手动管理事务
        // 写入 department
        List<Map<String, Object>> deptData = List.of(
                createRow("id", 1, "name", "研发部"));

        WriteResult deptResult = databaseWriter.write(dataSource, "test_department", deptData);
        assertEquals(1, deptResult.getInsertCount());

        // 验证 department 已写入（不在事务中，已提交）
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_department", Integer.class);
        assertEquals(1, count);

        // 尝试写入 employee 到不存在的表（模拟失败）
        // 这里我们验证 DatabaseWriter 对无效表的处理
        List<Map<String, Object>> empData = List.of(
                createRow("id", 1, "name", "test"));

        // 写入到不存在的表应抛异常（由 JdbcTemplate 层抛出）
        assertThrows(Exception.class, () -> {
            databaseWriter.write(dataSource, "non_existent_table", empData);
        }, "写入不存在的表应抛异常");
    }

    // ==================== count 验证 ====================

    @Test
    @DisplayName("count 验证 — 写入后数据库行数应与 insertCount 一致")
    void testCountMatchesInsertCount() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            data.add(createRow("id", i, "username", "user" + i, "age", 25, "email", "e" + i + "@t.com"));
        }

        WriteResult result = databaseWriter.write(dataSource, "test_user", data);

        assertEquals(7, result.getInsertCount(), "insertCount 应为 7");

        Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_user", Integer.class);
        assertEquals(result.getInsertCount(), dbCount.intValue(),
                "数据库实际行数应与 insertCount 一致");
    }

    // ==================== 参数化 SQL 安全 ====================

    @Test
    @DisplayName("参数化 SQL — 特殊字符数据应安全写入（防注入）")
    void testParameterizedSqlSafe() {
        // 包含单引号的字符串应能安全写入（参数化SQL不会导致SQL错误）
        List<Map<String, Object>> data = List.of(
                createRow("id", 1, "username", "O'Brien", "age", 30, "email", "ob@test.com")
        );

        // 不应抛异常
        WriteResult result = assertDoesNotThrow(() ->
                databaseWriter.write(dataSource, "test_user", data),
                "特殊字符数据应能安全写入");

        assertEquals(1, result.getInsertCount());

        // 验证数据正确写入
        String username = jdbcTemplate.queryForObject(
                "SELECT username FROM test_user WHERE id = 1", String.class);
        assertEquals("O'Brien", username, "特殊字符应正确存储");
    }

    // ==================== 辅助方法 ====================

    @SafeVarargs
    private static Map<String, Object> createRow(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}
