package com.platform.service;

import com.platform.entity.TestDataTaskPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试数据生成计划服务测试
 *
 * <p>验证计划保存、查询、降级处理。</p>
 */
@SpringBootTest
@DisplayName("测试数据生成计划服务测试")
class TestDataTaskPlanServiceTest {

    @Autowired
    private TestDataTaskPlanService planService;

    private static long uniqueTaskIdCounter = System.currentTimeMillis();

    // ==================== 1. 保存 plan ====================

    @Test
    @DisplayName("保存生成计划，查询应返回计划 JSON")
    void testSaveAndQueryPlan() {
        Long taskId = newTaskId();
        Map<String, Object> plan = buildTestPlan("test-task", List.of("users", "orders"));

        planService.savePlan(taskId, plan);

        TestDataTaskPlan result = planService.getPlan(taskId);
        assertNotNull(result, "应能查询到已保存的计划");
        assertEquals(taskId, result.getTaskId());
        assertNotNull(result.getPlanJson(), "plan_json 不应为空");
        assertTrue(result.getPlanJson().contains("test-task"), "JSON 应包含任务名");
        assertTrue(result.getPlanJson().contains("users"), "JSON 应包含表名 users");
        assertTrue(result.getPlanJson().contains("orders"), "JSON 应包含表名 orders");
        assertNotNull(result.getCreateTime(), "create_time 不应为空");
    }

    // ==================== 2. 查询 plan ====================

    @Test
    @DisplayName("getPlan 应返回完整的 JSON 计划内容")
    void testGetPlanContent() {
        Long taskId = newTaskId();
        Map<String, Object> plan = buildTestPlan("integration-test",
                List.of("department", "employee"));

        planService.savePlan(taskId, plan);

        TestDataTaskPlan result = planService.getPlan(taskId);
        assertNotNull(result);
        String json = result.getPlanJson();
        assertTrue(json.contains("true"), "JSON 应包含 success=true 的 true 值");
        assertTrue(json.contains("false"), "JSON 应包含 mock=false 的 false 值");
        assertTrue(json.contains("department"), "JSON 应包含 department 表");
        assertTrue(json.contains("employee"), "JSON 应包含 employee 表");
    }

    @Test
    @DisplayName("查询不存在的任务应返回 null")
    void testQueryNonExistentTaskReturnsNull() {
        TestDataTaskPlan result = planService.getPlan(-99999L);
        assertNull(result, "不存在的任务应返回 null");
    }

    // ==================== 3. 保存失败降级 ====================

    @Test
    @DisplayName("null 计划不保存，不抛异常")
    void testNullPlanNotSaved() {
        Long taskId = newTaskId();
        // savePlan(taskId, null) 不应抛出异常
        assertDoesNotThrow(() -> planService.savePlan(taskId, null),
                "null plan 不应导致异常");

        TestDataTaskPlan result = planService.getPlan(taskId);
        assertNull(result, "null plan 不应产生任何记录");
    }

    @Test
    @DisplayName("保存计划应稳定不抛异常（降级测试）")
    void testSavePlanDegradesGracefully() {
        Long taskId = newTaskId();
        // 传入超大对象也不应抛异常
        Map<String, Object> largePlan = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            largePlan.put("key_" + i, "value_" + i);
        }

        assertDoesNotThrow(() -> planService.savePlan(taskId, largePlan),
                "savePlan 不应因任何原因抛出异常");
    }

    @Test
    @DisplayName("不同任务 ID 的计划应相互隔离")
    void testTaskIsolation() {
        Long taskIdA = newTaskId();
        Long taskIdB = newTaskId();

        Map<String, Object> planA = buildTestPlan("task-A", List.of("table_a"));
        Map<String, Object> planB = buildTestPlan("task-B", List.of("table_b"));

        planService.savePlan(taskIdA, planA);
        planService.savePlan(taskIdB, planB);

        TestDataTaskPlan resultA = planService.getPlan(taskIdA);
        TestDataTaskPlan resultB = planService.getPlan(taskIdB);

        assertNotNull(resultA);
        assertNotNull(resultB);
        assertNotEquals(taskIdB, resultA.getTaskId());
        assertTrue(resultA.getPlanJson().contains("table_a"),
                "taskA 应包含 table_a");
        assertTrue(resultB.getPlanJson().contains("table_b"),
                "taskB 应包含 table_b");
        assertFalse(resultA.getPlanJson().contains("table_b"),
                "taskA 不应包含 table_b");
    }

    // ==================== 4. getPlanByTaskId ====================

    @Test
    @DisplayName("getPlanByTaskId 应返回解析后的 Map 结构")
    void testGetPlanByTaskId_ExistingPlan() {
        Long taskId = newTaskId();
        Map<String, Object> plan = buildTestPlan("query-test", List.of("users", "orders"));

        planService.savePlan(taskId, plan);

        Object result = planService.getPlanByTaskId(taskId);
        assertNotNull(result, "应返回解析后的计划对象");
        assertInstanceOf(Map.class, result, "返回类型应为 Map");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue((Boolean) resultMap.get("success"), "success 应为 true");

        @SuppressWarnings("unchecked")
        Map<String, Object> planData = (Map<String, Object>) resultMap.get("plan");
        assertNotNull(planData, "plan 字段不应为空");
        assertEquals("query-test", planData.get("taskName"), "taskName 应匹配");
    }

    @Test
    @DisplayName("getPlanByTaskId 多表计划应返回完整结构")
    void testGetPlanByTaskId_MultiTablePlan() {
        Long taskId = newTaskId();
        Map<String, Object> plan = buildTestPlan("multi-table-test",
                List.of("department", "employee", "task"));

        planService.savePlan(taskId, plan);

        Object result = planService.getPlanByTaskId(taskId);
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        @SuppressWarnings("unchecked")
        Map<String, Object> planData = (Map<String, Object>) resultMap.get("plan");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) planData.get("tables");
        assertNotNull(tables, "tables 不应为空");
        assertEquals(3, tables.size(), "应包含 3 张表");

        // 验证表名
        List<String> tableNames = tables.stream()
                .map(t -> (String) t.get("table"))
                .toList();
        assertTrue(tableNames.contains("department"), "应包含 department");
        assertTrue(tableNames.contains("employee"), "应包含 employee");
        assertTrue(tableNames.contains("task"), "应包含 task");
    }

    @Test
    @DisplayName("getPlanByTaskId 不存在任务应返回 null")
    void testGetPlanByTaskId_NonExistentTask() {
        Object result = planService.getPlanByTaskId(-88888L);
        assertNull(result, "不存在的任务应返回 null");
    }

    @Test
    @DisplayName("getPlanByTaskId 无计划任务应返回 null")
    void testGetPlanByTaskId_NoPlan() {
        Long taskId = newTaskId();
        // 没有调用 savePlan，直接查询
        Object result = planService.getPlanByTaskId(taskId);
        assertNull(result, "无计划的任务应返回 null");
    }

    // ==================== 辅助方法 ====================

    /** 生成唯一 taskId，避免测试间冲突 */
    private static long newTaskId() {
        return ++uniqueTaskIdCounter;
    }

    /** 构建模拟的 GeneratePlanResponse 风格的测试计划 */
    private static Map<String, Object> buildTestPlan(String taskName, List<String> tableNames) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("success", true);
        plan.put("mock", false);
        plan.put("error", null);

        Map<String, Object> planData = new LinkedHashMap<>();
        planData.put("taskName", taskName);

        List<Map<String, Object>> tables = new java.util.ArrayList<>();
        for (String tableName : tableNames) {
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("table", tableName);
            table.put("count", 10);
            tables.add(table);
        }
        planData.put("tables", tables);
        plan.put("plan", planData);

        return plan;
    }
}
