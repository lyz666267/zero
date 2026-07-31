package com.platform.service;

import com.platform.entity.TestDataResult;
import com.platform.entity.TestDataTask;
import com.platform.exception.BusinessException;
import com.platform.mapper.TestDataTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试数据生成结果服务测试
 *
 * <p>验证结果保存、查询、多表支持、空数据处理。</p>
 */
@SpringBootTest
@DisplayName("测试数据生成结果服务测试")
class TestDataResultServiceTest {

    @Autowired
    private TestDataResultService resultService;

    @Autowired
    private TestDataTaskMapper taskMapper;

    private static long uniqueTaskIdCounter = System.currentTimeMillis();

    // ==================== 1. 保存结果 ====================

    @Test
    @DisplayName("保存单表结果，查询应返回对应记录")
    void testSaveAndQueryResult() {
        Long taskId = newTaskId();
        List<Map<String, Object>> data = List.of(
                createRow("id", 1, "name", "张三", "phone", "13812345678"),
                createRow("id", 2, "name", "李四", "phone", "13900001111")
        );

        resultService.saveResult(taskId, "users", data);

        List<TestDataResult> results = resultService.findByTaskId(taskId);
        assertEquals(1, results.size());
        TestDataResult result = results.get(0);
        assertEquals(taskId, result.getTaskId());
        assertEquals("users", result.getTableName());
        assertNotNull(result.getDataJson());
        assertTrue(result.getDataJson().contains("张三"), "JSON 应包含数据 '张三'");
        assertTrue(result.getDataJson().contains("13900001111"), "JSON 应包含数据 '13900001111'");
        assertNotNull(result.getCreateTime());
    }

    // ==================== 2. 多表结果 ====================

    @Test
    @DisplayName("一个任务保存多张表，查询应返回多条记录")
    void testMultipleTableResults() {
        Long taskId = newTaskId();

        List<Map<String, Object>> deptData = List.of(
                createRow("dept_id", 1, "dept_name", "技术部")
        );
        List<Map<String, Object>> empData = List.of(
                createRow("emp_id", 101, "emp_name", "王五", "dept_id", 1),
                createRow("emp_id", 102, "emp_name", "赵六", "dept_id", 1)
        );

        resultService.saveResult(taskId, "department", deptData);
        resultService.saveResult(taskId, "employee", empData);

        List<TestDataResult> results = resultService.findByTaskId(taskId);
        assertEquals(2, results.size());
        assertEquals("department", results.get(0).getTableName());
        assertEquals("employee", results.get(1).getTableName());

        // 验证数据 JSON 内容
        assertTrue(results.get(0).getDataJson().contains("技术部"));
        assertTrue(results.get(1).getDataJson().contains("赵六"));
    }

    // ==================== 3. 按 taskId 隔离 ====================

    @Test
    @DisplayName("不同任务 ID 的结果应相互隔离")
    void testTaskIsolation() {
        Long taskIdA = newTaskId();
        Long taskIdB = newTaskId();

        resultService.saveResult(taskIdA, "table_a",
                List.of(createRow("col", "valueA")));
        resultService.saveResult(taskIdB, "table_b",
                List.of(createRow("col", "valueB")));

        List<TestDataResult> resultsA = resultService.findByTaskId(taskIdA);
        List<TestDataResult> resultsB = resultService.findByTaskId(taskIdB);

        assertEquals(1, resultsA.size());
        assertEquals(1, resultsB.size());
        assertEquals("table_a", resultsA.get(0).getTableName());
        assertEquals("table_b", resultsB.get(0).getTableName());
        assertNotEquals(taskIdB, resultsA.get(0).getTaskId());
    }

    // ==================== 4. null / 空数据安全 ====================

    @Test
    @DisplayName("空数据列表不保存，不抛异常")
    void testEmptyDataNotSaved() {
        Long taskId = newTaskId();
        resultService.saveResult(taskId, "empty_table", List.of());
        List<TestDataResult> results = resultService.findByTaskId(taskId);
        assertTrue(results.isEmpty(), "空数据不应产生任何记录");
    }

    @Test
    @DisplayName("null 数据不保存，不抛异常")
    void testNullDataNotSaved() {
        Long taskId = newTaskId();
        resultService.saveResult(taskId, "null_table", null);
        List<TestDataResult> results = resultService.findByTaskId(taskId);
        assertTrue(results.isEmpty(), "null 数据不应产生任何记录");
    }

    // ==================== 5. 数据反序列化 ====================

    @Test
    @DisplayName("findDataByTaskId 应将 JSON 反序列化为原始列表")
    void testFindDataByTaskIdDeserializesCorrectly() {
        Long taskId = newTaskId();
        List<Map<String, Object>> original = List.of(
                createRow("code", "A001", "name", "产品A", "price", 99.99),
                createRow("code", "A002", "name", "产品B", "price", 199.00)
        );

        resultService.saveResult(taskId, "product", original);

        LinkedHashMap<String, List<Map<String, Object>>> dataMap =
                resultService.findDataByTaskId(taskId);
        assertTrue(dataMap.containsKey("product"));
        List<Map<String, Object>> deserialized = dataMap.get("product");
        assertEquals(2, deserialized.size());
        assertEquals("产品A", deserialized.get(0).get("name"));
        assertEquals(99.99, deserialized.get(0).get("price"));
    }

    // ==================== 6. 查询不存在的任务 ====================

    @Test
    @DisplayName("查询不存在的结果应返回空列表")
    void testQueryNonExistentTaskReturnsEmpty() {
        List<TestDataResult> results = resultService.findByTaskId(-99999L);
        assertTrue(results.isEmpty());
    }

    // ==================== 7. getResultByTaskId — 单表结果 ====================

    @Test
    @DisplayName("getResultByTaskId 应返回按表名聚合的 ResultResponse")
    void testGetResultByTaskIdSingleTable() {
        Long taskId = newTaskId();
        createTask(taskId);

        List<Map<String, Object>> data = List.of(
                createRow("id", 1, "name", "张三", "phone", "13812345678"),
                createRow("id", 2, "name", "李四", "phone", "13900001111")
        );
        resultService.saveResult(taskId, "users", data);

        var response = resultService.getResultByTaskId(taskId);
        assertTrue(response.isSuccess());
        assertNotNull(response.getTables());
        assertEquals(1, response.getTables().size());

        var table = response.getTables().get(0);
        assertEquals("users", table.getTableName());
        assertEquals(2, table.getRows().size());
        assertEquals("张三", table.getRows().get(0).get("name"));
    }

    // ==================== 8. getResultByTaskId — 多表结果 ====================

    @Test
    @DisplayName("getResultByTaskId 应按表名聚合多表结果")
    void testGetResultByTaskIdMultiTable() {
        Long taskId = newTaskId();
        createTask(taskId);

        List<Map<String, Object>> deptData = List.of(
                createRow("dept_id", 1, "dept_name", "技术部")
        );
        List<Map<String, Object>> empData = List.of(
                createRow("emp_id", 101, "emp_name", "王五"),
                createRow("emp_id", 102, "emp_name", "赵六")
        );

        resultService.saveResult(taskId, "department", deptData);
        resultService.saveResult(taskId, "employee", empData);

        var response = resultService.getResultByTaskId(taskId);
        assertTrue(response.isSuccess());
        assertEquals(2, response.getTables().size());

        assertEquals("department", response.getTables().get(0).getTableName());
        assertEquals("employee", response.getTables().get(1).getTableName());
        assertEquals(1, response.getTables().get(0).getRows().size());
        assertEquals(2, response.getTables().get(1).getRows().size());
    }

    // ==================== 9. getResultByTaskId — 任务不存在 ====================

    @Test
    @DisplayName("getResultByTaskId 对不存在的任务应抛出 BusinessException(404)")
    void testGetResultByTaskIdTaskNotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                resultService.getResultByTaskId(-1L));
        assertEquals(404, ex.getCode());
        assertEquals("任务不存在", ex.getMessage());
    }

    // ==================== 10. getResultByTaskId — 空结果 ====================

    @Test
    @DisplayName("getResultByTaskId 对存在但无结果的任务应返回空 tables")
    void testGetResultByTaskIdEmptyResult() {
        Long taskId = newTaskId();
        createTask(taskId);

        var response = resultService.getResultByTaskId(taskId);
        assertTrue(response.isSuccess());
        assertNotNull(response.getTables());
        assertTrue(response.getTables().isEmpty(), "无结果时应返回空列表");
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

    /** 生成唯一 taskId，避免测试间冲突 */
    private static long newTaskId() {
        return ++uniqueTaskIdCounter;
    }

    /** 创建一个最小化的 TestDataTask 记录，供 getResultByTaskId 校验 */
    private void createTask(Long taskId) {
        TestDataTask task = new TestDataTask();
        task.setId(taskId);
        task.setTaskName("test-task-" + taskId);
        task.setDatasourceId(1L); // 非空约束，填充占位值
        task.setStatus("SUCCESS");
        taskMapper.insert(task);
    }
}
