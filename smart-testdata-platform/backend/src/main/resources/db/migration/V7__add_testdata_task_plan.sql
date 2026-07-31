-- ============================================================
-- V7: 测试数据生成计划表
-- 保存 LLM Agent 生成的 GenerationPlan，用于前端展示 AI 生成过程
-- ============================================================

CREATE TABLE testdata_task_plan (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL COMMENT '关联任务 ID (testdata_task.id)',
    plan_json   JSON         NOT NULL COMMENT 'AI 生成的计划（完整 JSON）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试数据生成计划';
