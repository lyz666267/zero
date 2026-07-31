package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 测试数据生成结果查询响应
 *
 * <pre>
 * {
 *   "success": true,
 *   "tables": [
 *     { "tableName": "sys_user", "rows": [{"id": 1, "name": "张三"}, ...] }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultResponse {

    /** 是否成功 */
    private boolean success;

    /** 各表生成结果（按生成顺序排列） */
    private List<ResultTable> tables;

    // ==================== 内嵌类型 ====================

    /**
     * 单个表的结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultTable {

        /** 表名 */
        private String tableName;

        /** 生成的数据行 */
        private List<Map<String, Object>> rows;
    }
}
