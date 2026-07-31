package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.AgentLogResponse;
import com.platform.dto.AgentLogResponse.StepInfo;
import com.platform.entity.AgentExecutionLog;
import com.platform.mapper.AgentExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 执行日志服务
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>记录 Agent 执行步骤（被 TestDataTaskExecutor 调用）</li>
 *   <li>查询任务的所有执行日志（供 AgentTrace.vue 使用）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLogService {

    private final AgentExecutionLogMapper logMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 记录 Agent 执行步骤
     *
     * @param taskId        任务 ID
     * @param stepNumber    步骤序号
     * @param stepType      步骤类型
     * @param action        动作描述
     * @param inputData     输入数据（对象，会序列化为 JSON）
     * @param outputData    输出数据（对象，会序列化为 JSON）
     * @param toolName      工具名称
     * @param status        执行状态
     * @param executionTime 执行耗时（毫秒）
     * @param agentName     Agent 名称
     */
    public void logStep(Long taskId, int stepNumber, String stepType, String action,
                        Object inputData, Object outputData, String toolName,
                        String status, long executionTime, String agentName) {
        try {
            AgentExecutionLog logEntry = new AgentExecutionLog();
            logEntry.setTaskId(taskId);
            logEntry.setAgentName(agentName != null ? agentName : "ToolAgent");
            logEntry.setStepNumber(stepNumber);
            logEntry.setStepType(stepType);
            logEntry.setAction(action);
            logEntry.setInputData(toJson(inputData));
            logEntry.setOutputData(toJson(outputData));
            logEntry.setToolName(toolName != null ? toolName : "");
            logEntry.setStatus(status != null ? status : "SUCCESS");
            logEntry.setExecutionTime(executionTime);
            logEntry.setCreatedAt(LocalDateTime.now());

            logMapper.insert(logEntry);
            log.debug("Agent 日志记录: taskId={}, step={}, action={}, status={}",
                    taskId, stepNumber, action, status);

        } catch (Exception e) {
            // 日志记录失败不影响主流程
            log.warn("Agent 日志记录失败（不影响任务执行）: taskId={}, step={}, error={}",
                    taskId, stepNumber, e.getMessage());
        }
    }

    /**
     * 查询任务的所有执行日志
     *
     * @param taskId 任务 ID
     * @return Agent 日志响应（含步骤列表）
     */
    public AgentLogResponse getLogs(Long taskId) {
        List<AgentExecutionLog> logs = logMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionLog>()
                        .eq(AgentExecutionLog::getTaskId, taskId)
                        .orderByAsc(AgentExecutionLog::getStepNumber)
        );

        List<StepInfo> steps = logs.stream()
                .map(log -> StepInfo.builder()
                        .stepNumber(log.getStepNumber())
                        .action(log.getAction())
                        .stepType(log.getStepType())
                        .toolName(log.getToolName())
                        .status(log.getStatus())
                        .executionTime(log.getExecutionTime())
                        .inputData(log.getInputData())
                        .outputData(log.getOutputData())
                        .build())
                .collect(Collectors.toList());

        return AgentLogResponse.builder()
                .taskId(taskId)
                .steps(steps)
                .build();
    }

    // ==================== 私有工具方法 ====================

    /**
     * 对象转 JSON 字符串（安全，不抛异常）
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return obj.toString();
        }
    }
}
