-- ============================================================
-- V8: Agent 执行日志表
-- 记录 LLM Agent 每一步的执行轨迹，用于前端时间线展示
-- ============================================================

CREATE TABLE agent_execution_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id         BIGINT       NOT NULL COMMENT '关联任务 ID (testdata_task.id)',
    agent_name      VARCHAR(100) DEFAULT 'ToolAgent' COMMENT 'Agent 名称',
    step_number     INT          NOT NULL DEFAULT 0 COMMENT '步骤序号（1-based）',
    step_type       VARCHAR(50)  DEFAULT '' COMMENT '步骤类型：PARSE/ANALYZE/PLAN/GENERATE/PRIVACY/COMPLETE',
    action          VARCHAR(255) DEFAULT '' COMMENT '动作描述（人类可读）',
    input_data      JSON         COMMENT '输入数据（JSON 格式）',
    output_data     JSON         COMMENT '输出数据（JSON 格式）',
    tool_name       VARCHAR(100) DEFAULT '' COMMENT '使用的工具名称',
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '执行状态：SUCCESS/FAILED/RUNNING',
    execution_time  BIGINT       DEFAULT 0 COMMENT '执行耗时（毫秒）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 执行日志';
