-- ============================================================
-- V3: Schema 缓存增强 — 添加同步时间、字段计数等
-- ============================================================

-- schema_table: 添加 column_count（字段数）和 sync_time（同步时间）
ALTER TABLE schema_table
    ADD COLUMN column_count INT NOT NULL DEFAULT 0 COMMENT '字段数量' AFTER table_comment,
    ADD COLUMN sync_time    DATETIME DEFAULT NULL COMMENT '最后同步时间' AFTER column_count;

-- schema_column: 添加 column_type（完整列类型如 varchar(64)）和 default_value
ALTER TABLE schema_column
    ADD COLUMN column_type   VARCHAR(256) DEFAULT NULL COMMENT '完整列类型（如 varchar(64)）' AFTER data_type,
    ADD COLUMN default_value VARCHAR(512) DEFAULT NULL COMMENT '默认值' AFTER is_primary_key;

-- 添加唯一约束：(datasource_id, table_name)，防止重复缓存
ALTER TABLE schema_table
    ADD UNIQUE KEY uk_datasource_table (datasource_id, table_name);

-- 修改 is_nullable → nullable 语义一致（保留原字段，业务层映射）
-- 不删除原有列以保持向后兼容
