-- ============================================================
-- V10: 数据库脱敏任务表
-- Phase 8.4-1: 支持对已有数据库业务数据进行安全脱敏
-- 流程: 选择数据源 → 选择表 → 分析敏感字段 → 预览SQL → 确认 → 执行
-- ============================================================

CREATE TABLE data_mask_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    datasource_id   BIGINT       NOT NULL COMMENT '数据源 ID',
    table_name      VARCHAR(128) NOT NULL COMMENT '目标脱敏表名',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PREVIEW' COMMENT '状态: PREVIEW/EXECUTING/SUCCESS/FAILED',
    sql_preview     TEXT         COMMENT '预览生成的 UPDATE SQL 语句',
    execute_result  TEXT         COMMENT '执行结果消息',
    affected_rows   INT          DEFAULT 0 COMMENT '影响行数',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_datasource_id (datasource_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库脱敏任务';
