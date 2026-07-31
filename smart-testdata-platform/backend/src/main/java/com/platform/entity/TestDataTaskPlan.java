package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试数据生成计划实体 — 映射 testdata_task_plan 表
 *
 * <p>保存 LLM Agent 生成的完整 GenerationPlan（JSON 格式），
 * 用于前端展示 AI 生成过程的中间结果。</p>
 */
@Data
@TableName("testdata_task_plan")
public class TestDataTaskPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务 ID */
    private Long taskId;

    /** AI 生成的计划 JSON */
    private String planJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
