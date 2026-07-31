package com.platform.controller;

import com.platform.dto.AgentLogResponse;
import com.platform.dto.ApiResponse;
import com.platform.service.AgentLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Agent 执行日志查询接口
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>GET /api/agent/log/{taskId} — 查询任务的 Agent 执行步骤</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentLogController {

    private final AgentLogService agentLogService;

    /**
     * 查询任务的 Agent 执行日志
     *
     * @param taskId 任务 ID
     * @return 任务的所有 Agent 执行步骤
     *
     * <h3>响应示例</h3>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "taskId": 1,
     *     "steps": [
     *       {
     *         "stepNumber": 1,
     *         "action": "需求解析",
     *         "stepType": "PARSE",
     *         "toolName": "",
     *         "status": "SUCCESS",
     *         "executionTime": 120
     *       }
     *     ]
     *   }
     * }
     * </pre>
     */
    @GetMapping("/log/{taskId}")
    public ApiResponse<AgentLogResponse> getLogs(@PathVariable Long taskId) {
        AgentLogResponse logs = agentLogService.getLogs(taskId);
        return ApiResponse.success(logs);
    }
}
