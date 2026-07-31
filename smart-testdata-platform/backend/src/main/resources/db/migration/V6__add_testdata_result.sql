-- ============================================================
-- V6: 测试数据生成结果表
-- 保存每次任务产生的测试数据，用于前端展示和回溯
-- ============================================================

CREATE TABLE testdata_result (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL COMMENT '关联任务 ID (testdata_task.id)',
    table_name  VARCHAR(100) DEFAULT NULL COMMENT '来源表名',
    data_json   JSON         DEFAULT NULL COMMENT '生成的数据行（JSON 数组格式）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试数据生成结果';
