-- ============================================================
-- V4: 测试数据生成任务表
-- 记录每次生成任务的元信息和执行状态
-- ============================================================

CREATE TABLE testdata_task (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_name      VARCHAR(100) NOT NULL COMMENT '任务名称',
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
    total_count    INT          NOT NULL DEFAULT 0 COMMENT '目标生成总数',
    success_count  INT          NOT NULL DEFAULT 0 COMMENT '成功生成数',
    fail_count     INT          NOT NULL DEFAULT 0 COMMENT '失败数',
    error_message  TEXT         DEFAULT NULL COMMENT '错误信息',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finish_time    DATETIME     DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试数据生成任务';
