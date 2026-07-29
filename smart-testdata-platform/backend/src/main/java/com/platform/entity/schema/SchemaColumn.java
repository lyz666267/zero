package com.platform.entity.schema;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Schema 缓存 — 列（映射 schema_column）
 */
@Data
@TableName("schema_column")
public class SchemaColumn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 schema_table.id */
    private Long tableId;

    /** 列名 */
    private String columnName;

    /** 数据类型（如 varchar/int/datetime） */
    private String dataType;

    /** 完整列类型（如 varchar(64)） */
    private String columnType;

    /** 最大长度 */
    private Integer maxLength;

    /** 是否可空 */
    @TableField("is_nullable")
    private Boolean nullable;

    /** 是否主键 */
    @TableField("is_primary_key")
    private Boolean primaryKey;

    /** 默认值 */
    private String defaultValue;

    /** 列注释 */
    private String columnComment;

    /** 字段序号 */
    private Integer ordinalPosition;

    /** 外键引用表 */
    private String foreignRefTable;

    /** 外键引用列 */
    private String foreignRefColumn;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
