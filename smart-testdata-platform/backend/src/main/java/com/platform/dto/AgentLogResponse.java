package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 执行日志查询响应
 *
 * <p>GET /api/agent/log/{taskId} 的返回结构。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLogResponse {

    /** 任务 ID */
    private Long taskId;

    /** 步骤列表 */
    private List<StepInfo> steps;

    /**
     * 单个步骤信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepInfo {

        /** 步骤序号 */
        private Integer stepNumber;

        /** 动作描述 */
        private String action;

        /** 步骤类型 */
        private String stepType;

        /** 工具名称 */
        private String toolName;

        /** 执行状态 */
        private String status;

        /** 执行耗时（毫秒） */
        private Long executionTime;

        /** 输入数据摘要 */
        private String inputData;

        /** 输出数据摘要 */
        private String outputData;
    }
}
