-- ============================================================
-- V1: 平台初始化建表
-- 基于大模型 Agent 的智能测试数据生成与隐私脱敏平台
-- ============================================================

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    password    VARCHAR(256) NOT NULL COMMENT '密码（BCrypt 加密）',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用状态：1-启用 0-禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台用户';

-- ------------------------------------------------------------
-- 2. 项目表
-- ------------------------------------------------------------
CREATE TABLE project (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '所属用户',
    name        VARCHAR(128) NOT NULL COMMENT '项目名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '项目描述',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目';

-- ------------------------------------------------------------
-- 3. 数据源配置表（密码 AES 加密存储）
-- ------------------------------------------------------------
CREATE TABLE datasource (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id          BIGINT       NOT NULL COMMENT '所属项目',
    name                VARCHAR(128) NOT NULL COMMENT '数据源名称',
    host                VARCHAR(256) NOT NULL COMMENT '主机地址',
    port                INT          NOT NULL DEFAULT 3306 COMMENT '端口号',
    db_name             VARCHAR(128) NOT NULL COMMENT '数据库名',
    username            VARCHAR(128) NOT NULL COMMENT '数据库用户名',
    password_encrypted  VARCHAR(512) NOT NULL COMMENT '密码（AES 加密）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'UNCONNECTED' COMMENT '状态：UNCONNECTED/CONNECTED/ERROR',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源配置';

-- ------------------------------------------------------------
-- 4. Schema 缓存 — 表（避免每次扫描 information_schema）
-- ------------------------------------------------------------
CREATE TABLE schema_table (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    datasource_id       BIGINT       NOT NULL COMMENT '所属数据源',
    table_name          VARCHAR(128) NOT NULL COMMENT '表名',
    table_comment       VARCHAR(512) DEFAULT NULL COMMENT '表注释',
    row_count_estimate  BIGINT       DEFAULT NULL COMMENT '预估行数',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '缓存时间',
    PRIMARY KEY (id),
    KEY idx_datasource_id (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Schema 缓存 - 表';

-- ------------------------------------------------------------
-- 5. Schema 缓存 — 列
-- ------------------------------------------------------------
CREATE TABLE schema_column (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    table_id            BIGINT        NOT NULL COMMENT '所属表',
    column_name         VARCHAR(128)  NOT NULL COMMENT '列名',
    data_type           VARCHAR(64)   NOT NULL COMMENT '数据类型（如 VARCHAR/INT/DATETIME）',
    max_length          INT           DEFAULT NULL COMMENT '最大长度',
    is_nullable         TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否可空',
    is_primary_key      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否主键',
    column_comment      VARCHAR(512)  DEFAULT NULL COMMENT '列注释',
    ordinal_position    INT           NOT NULL DEFAULT 0 COMMENT '字段序号',
    foreign_ref_table   VARCHAR(128)  DEFAULT NULL COMMENT '外键引用表',
    foreign_ref_column  VARCHAR(128)  DEFAULT NULL COMMENT '外键引用列',
    sensitive_type      VARCHAR(64)   DEFAULT NULL COMMENT '敏感类型：NAME/PHONE/EMAIL/ID_CARD/BANK_CARD/ADDRESS/NONE',
    sample_data_json    JSON          DEFAULT NULL COMMENT '采样数据（JSON 数组）',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '缓存时间',
    PRIMARY KEY (id),
    KEY idx_table_id (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Schema 缓存 - 列';

-- ------------------------------------------------------------
-- 6. 生成任务表
-- ------------------------------------------------------------
CREATE TABLE generate_task (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id      BIGINT        NOT NULL COMMENT '所属项目',
    datasource_id   BIGINT        NOT NULL COMMENT '目标数据源',
    tables_json     JSON          NOT NULL COMMENT '要生成的表列表（JSON 数组）',
    row_count       INT           NOT NULL DEFAULT 100 COMMENT '目标行数',
    generated_rows  INT           NOT NULL DEFAULT 0 COMMENT '已生成行数',
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/COMPLETED/FAILED/CANCELLED',
    progress        INT           NOT NULL DEFAULT 0 COMMENT '进度百分比（0-100）',
    error_msg       TEXT          DEFAULT NULL COMMENT '错误信息',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_at     DATETIME      DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据生成任务';

-- ------------------------------------------------------------
-- 7. 生成策略表（保存 LLM 输出的 generation_plan.json）
-- ------------------------------------------------------------
CREATE TABLE generation_strategy (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id         BIGINT        NOT NULL COMMENT '所属任务',
    table_name      VARCHAR(128)  NOT NULL COMMENT '目标表名',
    column_name     VARCHAR(128)  NOT NULL COMMENT '目标列名',
    strategy_type   VARCHAR(64)   NOT NULL COMMENT '策略类型：faker/formula/sequence/regex/fixed/null/concat/enum',
    strategy_json   JSON          NOT NULL COMMENT '策略配置 JSON',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据生成策略';

-- ------------------------------------------------------------
-- 8. 任务日志表（WebSocket 事件落库）
-- ------------------------------------------------------------
CREATE TABLE task_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL COMMENT '所属任务',
    event_type  VARCHAR(64)  NOT NULL COMMENT '事件类型：INFO/WARN/ERROR/PROGRESS/COMPLETE',
    message     TEXT         NOT NULL COMMENT '日志内容',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务日志';

-- ------------------------------------------------------------
-- 9. 脱敏规则表
-- ------------------------------------------------------------
CREATE TABLE mask_rule (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id      BIGINT        NOT NULL COMMENT '所属项目',
    datasource_id   BIGINT        DEFAULT NULL COMMENT '数据源（NULL 表示全局规则）',
    table_name      VARCHAR(128)  DEFAULT NULL COMMENT '表名（NULL 表示适用所有表）',
    column_name     VARCHAR(128)  DEFAULT NULL COMMENT '列名（NULL 表示适用所有列）',
    rule_type       VARCHAR(64)   NOT NULL COMMENT '脱敏类型：REPLACE/MASK/TRUNCATE/SHUFFLE/FAKE/EMPTY',
    rule_config_json JSON         NOT NULL COMMENT '脱敏配置 JSON',
    enabled         TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_datasource_id (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='隐私脱敏规则';

-- ============================================================
-- 索引优化：外键关联查询高频索引
-- ============================================================
CREATE INDEX idx_schema_column_sensitive ON schema_column(sensitive_type);
CREATE INDEX idx_generation_strategy_table_col ON generation_strategy(task_id, table_name, column_name);
