package com.platform.generator.task;

import com.platform.dto.CreateTaskRequest;
import com.platform.dto.TaskResponse;
import com.platform.entity.TestDataTask;
import com.platform.mapper.TestDataTaskMapper;
import com.platform.service.TestDataTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步任务执行器测试
 *
 * <p>验证异步任务生命周期：PENDING → RUNNING → SUCCESS/FAILED。
 * AI 服务不可用时预期走向 FAILED 分支并保存错误信息。</p>
 */
@SpringBootTest
@DisplayName("异步任务执行器测试")
class TestDataTaskExecutorTest {

    private static final int MAX_WAIT_MS = 15000;
    private static final int POLL_INTERVAL_MS = 500;

    @Autowired
    private TestDataTaskService taskService;

    @Autowired
    private TestDataTaskMapper taskMapper;

    // ==================== PENDING → FAILED 流程 ====================

    @Test
    @DisplayName("创建任务后立即返回 PENDING 状态")
    void testCreateTaskReturnsPendingImmediately() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTaskName("测试-立即返回PENDING");
        request.setTotalCount(100);

        TaskResponse response = taskService.createTask(request);

        // HTTP 请求立即返回，不阻塞
        assertNotNull(response, "创建响应不应为 null");
        assertNotNull(response.getId(), "任务 ID 不应为 null");
        assertEquals("PENDING", response.getStatus(), "创建后状态应为 PENDING");
    }

    @Test
    @DisplayName("异步执行后状态变更 — 从 PENDING 变为非 PENDING")
    void testTaskStatusChangesAfterAsyncExecution() throws InterruptedException {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTaskName("测试-状态变更");
        request.setTotalCount(50);
        request.setDatasourceId(999L); // 不存在的数据源，触发快速失败

        TaskResponse response = taskService.createTask(request);
        Long taskId = response.getId();
        assertEquals("PENDING", response.getStatus());

        // 轮询等待状态变更
        String finalStatus = waitForStatusChange(taskId);
        assertNotEquals("PENDING", finalStatus,
                "异步执行后状态应变更（不应仍为 PENDING）");
    }

    @Test
    @DisplayName("失败场景 — errorMessage 应被保存")
    void testFailedTaskHasErrorMessage() throws InterruptedException {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTaskName("测试-失败保存错误信息");
        request.setTotalCount(10);
        request.setDatasourceId(999L); // 不存在的数据源

        TaskResponse response = taskService.createTask(request);
        Long taskId = response.getId();

        // 轮询等待终态
        String finalStatus = waitForStatusChange(taskId);
        assertEquals("FAILED", finalStatus,
                "无 AI 服务 + 无效数据源时应为 FAILED");

        TestDataTask task = taskMapper.selectById(taskId);
        assertNotNull(task, "任务应存在");
        assertNotNull(task.getErrorMessage(), "失败应有错误信息");
        assertFalse(task.getErrorMessage().isEmpty(), "错误信息不应为空");
    }

    @Test
    @DisplayName("任务查询 — 不存在的任务应抛 404")
    void testGetNonExistentTask() {
        com.platform.exception.BusinessException ex = assertThrows(
                com.platform.exception.BusinessException.class,
                () -> taskService.getTask(-1L),
                "查询不存在的任务应抛 BusinessException");
        assertEquals(404, ex.getCode());
        assertEquals("任务不存在", ex.getMessage());
    }

    // ==================== 辅助方法 ====================

    /**
     * 轮询等待任务状态从 PENDING 变更，返回最终状态
     */
    private String waitForStatusChange(Long taskId) throws InterruptedException {
        long start = System.currentTimeMillis();
        String status;
        do {
            Thread.sleep(POLL_INTERVAL_MS);
            TestDataTask task = taskMapper.selectById(taskId);
            status = task != null ? task.getStatus() : null;
        } while ("PENDING".equals(status) || "RUNNING".equals(status)
                && System.currentTimeMillis() - start < MAX_WAIT_MS);

        return status;
    }
}
