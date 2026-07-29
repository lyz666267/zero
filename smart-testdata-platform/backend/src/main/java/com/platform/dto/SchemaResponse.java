package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Schema 分析响应 — 数据库结构信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaResponse {

    /** 数据库名 */
    private String database;

    /** 数据库类型 */
    private String dbType;

    /** 表列表 */
    private List<TableInfo> tables;

    /**
     * 表信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableInfo {

        /** 表名 */
        private String tableName;

        /** 表注释 */
        private String comment;

        /** 列信息 */
        private List<ColumnInfo> columns;
    }

    /**
     * 列信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {

        /** 字段名 */
        private String name;

        /** 字段类型（varchar、int、datetime 等） */
        private String type;

        /** 字段长度 */
        private Integer length;

        /** 是否可空 */
        private Boolean nullable;

        /** 默认值 */
        private String defaultValue;

        /** 是否主键 */
        private Boolean primary;

        /** 字段注释 */
        private String comment;

        /** 外键引用 — 表名 */
        private String foreignRefTable;

        /** 外键引用 — 列名 */
        private String foreignRefColumn;
    }
}
