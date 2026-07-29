package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 缓存的 Schema 查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedSchemaResponse {

    private List<CachedTableInfo> tables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CachedTableInfo {

        private String tableName;
        private String tableComment;
        private List<CachedColumnInfo> columns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CachedColumnInfo {

        private String name;
        private String type;
        private Boolean primaryKey;
        private Boolean nullable;
        private String defaultValue;
        private String comment;
        private Integer ordinalPosition;
    }
}
