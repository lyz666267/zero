package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.dto.GenerationPlanResponse;
import com.platform.entity.TestDataTask;
import com.platform.exception.BusinessException;
import com.platform.mapper.TestDataTaskMapper;
import com.platform.service.TestDataTaskPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 生成计划查询接口
 *
 * <p>提供任务对应的 AI 生成计划（GenerationPlan）查询功能。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/testdata/task")
@RequiredArgsConstructor
public class GenerationPlanController {

    private final TestDataTaskMapper taskMapper;
    private final TestDataTaskPlanService planService;

    /**
     * 查询任务的生成计划
     *
     * <p>GET /api/testdata/task/{taskId}/plan</p>
     *
     * <h3>返回值</h3>
     * <pre>
     * 有 plan:
     * { "code": 200, "message": "success", "data": { "success": true, "data": { "taskName": "...", "tables": [...] } } }
     *
     * 无 plan:
     * { "code": 200, "message": "success", "data": { "success": true, "data": null } }
     *
     * 任务不存在:
     * { "code": 404, "message": "任务不存在", "data": null }
     * </pre>
     *
     * @param taskId 任务 ID
     * @return 生成计划响应
     */
    @GetMapping("/{taskId}/plan")
    public ApiResponse<GenerationPlanResponse> getPlan(@PathVariable Long taskId) {
        // 1. 检查任务是否存在
        TestDataTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 2. 查询并解析计划
        Object planData = planService.getPlanByTaskId(taskId);

        GenerationPlanResponse response = GenerationPlanResponse.builder()
                .success(true)
                .data(planData)
                .build();

        return ApiResponse.success(response);
    }
}
