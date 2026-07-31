-- ============================================================
-- V5: testdata_task 增加数据源关联
-- ============================================================
ALTER TABLE testdata_task
    ADD COLUMN datasource_id BIGINT DEFAULT NULL COMMENT '目标数据源 ID' AFTER task_name,
    ADD KEY idx_datasource_id (datasource_id);
