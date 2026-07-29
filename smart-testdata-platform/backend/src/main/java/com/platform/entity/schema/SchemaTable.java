package com.platform.entity.schema;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Schema 缓存 — 表（映射 schema_table）
 */
@Data
@TableName("schema_table")
public class SchemaTable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属数据源 ID */
    private Long datasourceId;

    /** 表名 */
    private String tableName;

    /** 表注释 */
    private String tableComment;

    /** 字段数量 */
    private Integer columnCount;

    /** 最后同步时间 */
    private LocalDateTime syncTime;

    /** 预估行数 */
    private Long rowCountEstimate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
