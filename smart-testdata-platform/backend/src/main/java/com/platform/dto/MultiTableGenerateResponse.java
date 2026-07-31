package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 多表测试数据生成响应
 *
 * <pre>
 * {
 *   "success": true,
 *   "tables": [
 *     { "table": "department", "count": 3, "data": [...] },
 *     { "table": "employee",   "count": 5, "data": [...] }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiTableGenerateResponse {

    /** 是否成功 */
    private boolean success;

    /** 各表生成结果（按生成顺序排列） */
    private List<TableResult> tables;

    // ==================== 内嵌类型 ====================

    /**
     * 单个表的生成结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableResult {

        /** 表名 */
        private String table;

        /** 实际生成行数 */
        private int count;

        /** 生成的数据行 */
        private List<Map<String, Object>> data;
    }
}
