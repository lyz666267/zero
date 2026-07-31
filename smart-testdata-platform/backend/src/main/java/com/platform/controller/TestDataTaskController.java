package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.dto.CreateTaskRequest;
import com.platform.dto.TaskResponse;
import com.platform.service.TestDataTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 测试数据生成任务管理接口
 */
@RestController
@RequestMapping("/api/testdata/task")
@RequiredArgsConstructor
public class TestDataTaskController {

    private final TestDataTaskService testDataTaskService;

    /**
     * 创建测试数据生成任务
     * <p>
     * POST /api/testdata/task
     * <p>
     * 请求体:
     * <pre>
     * { "taskName": "生成用户测试数据", "totalCount": 1000 }
     * </pre>
     * <p>
     * 返回:
     * <pre>
     * { "code": 200, "data": { "id": 1, "status": "PENDING", ... } }
     * </pre>
     */
    @PostMapping
    public ApiResponse<TaskResponse> createTask(@RequestBody CreateTaskRequest request) {
        TaskResponse result = testDataTaskService.createTask(request);
        return ApiResponse.success(result);
    }

    /**
     * 查询任务状态
     * <p>
     * GET /api/testdata/task/{id}
     * <p>
     * 返回:
     * <pre>
     * { "code": 200, "data": { "id": 1, "status": "RUNNING", "successCount": 500, ... } }
     * </pre>
     */
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> getTask(@PathVariable Long id) {
        TaskResponse result = testDataTaskService.getTask(id);
        return ApiResponse.success(result);
    }
}
