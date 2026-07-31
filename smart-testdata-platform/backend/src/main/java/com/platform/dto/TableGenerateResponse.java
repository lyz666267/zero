package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 单表测试数据生成响应
 *
 * <pre>
 * {
 *   "success": true,
 *   "table": "user",
 *   "count": 3,
 *   "data": [
 *     { "username": "张三", "email": "xxx@qq.com" },
 *     { "username": "李四", "email": "yyy@qq.com" }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableGenerateResponse {

    /** 是否成功 */
    private boolean success;

    /** 表名 */
    private String table;

    /** 生成行数 */
    private int count;

    /** 生成的数据列表 */
    private List<Map<String, Object>> data;
}
