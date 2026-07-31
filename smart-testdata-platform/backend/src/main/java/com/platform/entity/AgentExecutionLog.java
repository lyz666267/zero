package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 执行日志实体 — 映射 agent_execution_log 表
 *
 * <p>记录 LLM Agent 每一步的执行轨迹，支持前端时间线展示。</p>
 *
 * <h3>典型步骤</h3>
 * <ol>
 *   <li>需求解析（PARSE）</li>
 *   <li>Schema 分析（ANALYZE）</li>
 *   <li>生成计划（PLAN）</li>
 *   <li>调用数据生成工具（GENERATE）</li>
 *   <li>调用隐私处理工具（PRIVACY）</li>
 *   <li>任务完成（COMPLETE）</li>
 * </ol>
 */
@Data
@TableName("agent_execution_log")
public class AgentExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务 ID */
    private Long taskId;

    /** Agent 名称 */
    private String agentName;

    /** 步骤序号（1-based） */
    private Integer stepNumber;

    /** 步骤类型 */
    private String stepType;

    /** 动作描述（人类可读） */
    private String action;

    /** 输入数据（JSON 字符串） */
    @TableField("input_data")
    private String inputData;

    /** 输出数据（JSON 字符串） */
    @TableField("output_data")
    private String outputData;

    /** 使用的工具名称 */
    private String toolName;

    /** 执行状态：SUCCESS / FAILED / RUNNING */
    private String status;

    /** 执行耗时（毫秒） */
    private Long executionTime;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
