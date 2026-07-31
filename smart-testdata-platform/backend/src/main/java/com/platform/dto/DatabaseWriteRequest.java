package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据库写入请求
 *
 * <pre>
 * {
 *   "datasourceId": 2,
 *   "tables": [
 *     {
 *       "table": "department",
 *       "data": [
 *         { "id": 1, "name": "研发部" },
 *         { "id": 2, "name": "市场部" }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseWriteRequest {

    /** 数据源 ID */
    private Long datasourceId;

    /** 要写入的表列表 */
    private List<TableData> tables;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableData {
        /** 表名 */
        private String table;
        /** 数据行 */
        private List<Map<String, Object>> data;
    }
}
