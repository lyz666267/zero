-- H2 test schema (MySQL compatibility mode)
-- Kept in sync with Flyway V1-V10 for SpringBootTest service tests.

CREATE TABLE sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(256) NOT NULL,
    nickname    VARCHAR(64),
    email       VARCHAR(128),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_project_user ON project(user_id);

CREATE TABLE datasource (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id         BIGINT       NOT NULL,
    name               VARCHAR(128) NOT NULL,
    db_type            VARCHAR(32)  NOT NULL DEFAULT 'MySQL',
    host               VARCHAR(256) NOT NULL,
    port               INT          NOT NULL DEFAULT 3306,
    db_name            VARCHAR(128) NOT NULL,
    username           VARCHAR(128) NOT NULL,
    password_encrypted VARCHAR(512) NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'UNCONNECTED',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_datasource_project ON datasource(project_id);

CREATE TABLE schema_table (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id      BIGINT       NOT NULL,
    table_name         VARCHAR(128) NOT NULL,
    table_comment      VARCHAR(512),
    column_count       INT          NOT NULL DEFAULT 0,
    sync_time          TIMESTAMP,
    row_count_estimate BIGINT,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_schema_table UNIQUE(datasource_id, table_name)
);

CREATE TABLE schema_column (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id           BIGINT       NOT NULL,
    column_name        VARCHAR(128) NOT NULL,
    data_type          VARCHAR(64)  NOT NULL,
    column_type        VARCHAR(256),
    max_length         INT,
    is_nullable        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_primary_key     BOOLEAN      NOT NULL DEFAULT FALSE,
    default_value      VARCHAR(512),
    column_comment     VARCHAR(512),
    ordinal_position   INT          NOT NULL DEFAULT 0,
    foreign_ref_table  VARCHAR(128),
    foreign_ref_column VARCHAR(128),
    sensitive_type     VARCHAR(64),
    sample_data_json   CLOB,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_schema_column_table ON schema_column(table_id);

CREATE TABLE testdata_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name     VARCHAR(100) NOT NULL,
    datasource_id BIGINT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_count   INT          NOT NULL DEFAULT 0,
    success_count INT          NOT NULL DEFAULT 0,
    fail_count    INT          NOT NULL DEFAULT 0,
    error_message TEXT,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time   TIMESTAMP
);
CREATE INDEX idx_testdata_task_status ON testdata_task(status);
CREATE INDEX idx_testdata_task_datasource ON testdata_task(datasource_id);

CREATE TABLE testdata_result (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT NOT NULL,
    table_name  VARCHAR(100),
    data_json   CLOB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_testdata_result_task ON testdata_result(task_id);

CREATE TABLE testdata_task_plan (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT NOT NULL,
    plan_json   CLOB   NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_testdata_task_plan_task ON testdata_task_plan(task_id);

CREATE TABLE agent_execution_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id        BIGINT NOT NULL,
    agent_name     VARCHAR(100) DEFAULT 'ToolAgent',
    step_number    INT    NOT NULL DEFAULT 0,
    step_type      VARCHAR(50)  DEFAULT '',
    action         VARCHAR(255) DEFAULT '',
    input_data     CLOB,
    output_data    CLOB,
    tool_name      VARCHAR(100) DEFAULT '',
    status         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    execution_time BIGINT DEFAULT 0,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_agent_log_task ON agent_execution_log(task_id);

CREATE TABLE data_quality_report (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id            BIGINT NOT NULL,
    total_score        DECIMAL(5,2) NOT NULL DEFAULT 0,
    grade              VARCHAR(20)  NOT NULL DEFAULT '',
    completeness_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    uniqueness_score   DECIMAL(5,2) NOT NULL DEFAULT 0,
    consistency_score  DECIMAL(5,2) NOT NULL DEFAULT 0,
    validity_score     DECIMAL(5,2) NOT NULL DEFAULT 0,
    privacy_score      DECIMAL(5,2) NOT NULL DEFAULT 0,
    detail_json        CLOB,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quality_task UNIQUE(task_id)
);

CREATE TABLE data_mask_task (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id  BIGINT NOT NULL,
    table_name     VARCHAR(128) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PREVIEW',
    sql_preview    TEXT,
    execute_result TEXT,
    affected_rows  INT DEFAULT 0,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_data_mask_datasource ON data_mask_task(datasource_id);
CREATE INDEX idx_data_mask_status ON data_mask_task(status);
