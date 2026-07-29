-- ============================================================
-- V2: 数据源配置增强 — 添加 db_type 字段
-- ============================================================
ALTER TABLE datasource
    ADD COLUMN db_type VARCHAR(32) NOT NULL DEFAULT 'MySQL' COMMENT '数据库类型（MySQL/PostgreSQL等）' AFTER name;
