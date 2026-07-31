-- ============================================================
-- V9: 数据质量评分报告表
-- 存储测试数据生成后的五项质量指标评估结果
-- ============================================================

CREATE TABLE data_quality_report (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id             BIGINT       NOT NULL COMMENT '关联任务 ID (testdata_task.id)',
    total_score         DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '综合评分 (0-100)',
    grade               VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '等级：优秀/良好/合格/不合格',
    completeness_score  DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '完整性评分',
    uniqueness_score    DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '唯一性评分',
    consistency_score   DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '关联一致性评分',
    validity_score      DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '格式合法性评分',
    privacy_score       DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '隐私安全评分',
    detail_json         JSON         COMMENT '详细信息（问题列表、改进建议等）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_total_score (total_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据质量评分报告';
