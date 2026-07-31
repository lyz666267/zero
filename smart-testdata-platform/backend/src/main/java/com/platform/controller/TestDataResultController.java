package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.dto.ResultResponse;
import com.platform.service.TestDataResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 测试数据生成结果查询接口
 */
@RestController
@RequestMapping("/api/testdata")
@RequiredArgsConstructor
public class TestDataResultController {

    private final TestDataResultService testDataResultService;

    /**
     * 查询任务生成结果
     * <p>
     * GET /api/testdata/task/{taskId}/result
     * <p>
     * 返回:
     * <pre>
     * {
     *   "code": 200,
     *   "data": {
     *     "success": true,
     *     "tables": [
     *       { "tableName": "sys_user", "rows": [...] }
     *     ]
     *   }
     * }
     * </pre>
     * <p>
     * 异常:
     * <ul>
     *   <li>任务不存在 → 404</li>
     *   <li>任务存在但无结果 → 返回空 tables</li>
     * </ul>
     */
    @GetMapping("/task/{taskId}/result")
    public ApiResponse<ResultResponse> getResult(@PathVariable Long taskId) {
        ResultResponse result = testDataResultService.getResultByTaskId(taskId);
        return ApiResponse.success(result);
    }
}
