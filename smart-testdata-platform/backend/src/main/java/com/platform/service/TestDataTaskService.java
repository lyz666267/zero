package com.platform.service;

import com.platform.dto.CreateTaskRequest;
import com.platform.dto.TaskResponse;
import com.platform.entity.TestDataTask;
import com.platform.exception.BusinessException;
import com.platform.generator.task.TestDataTaskExecutor;
import com.platform.mapper.TestDataTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 测试数据生成任务管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataTaskService {

    private final TestDataTaskMapper testDataTaskMapper;
    private final TestDataTaskExecutor testDataTaskExecutor;

    /**
     * 创建生成任务 — 保存 PENDING 后立即提交异步执行
     *
     * <p>HTTP 请求在任务入库后立即返回，不等待生成完成。
     * 调用方通过 GET /api/testdata/task/{id} 轮询状态。</p>
     */
    public TaskResponse createTask(CreateTaskRequest request) {
        TestDataTask task = new TestDataTask();
        task.setTaskName(request.getTaskName());
        task.setDatasourceId(request.getDatasourceId());
        task.setTotalCount(request.getTotalCount() != null ? request.getTotalCount() : 0);
        task.setStatus("PENDING");
        task.setSuccessCount(0);
        task.setFailCount(0);

        testDataTaskMapper.insert(task);
        log.info("测试数据生成任务已创建: id={}, taskName={}", task.getId(), task.getTaskName());

        // 提交异步执行
        testDataTaskExecutor.executeTask(task.getId());

        return TaskResponse.fromEntity(task);
    }

    /**
     * 查询任务状态
     */
    public TaskResponse getTask(Long id) {
        TestDataTask task = testDataTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return TaskResponse.fromEntity(task);
    }
}
