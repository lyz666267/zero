package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL INSERT 生成请求
 *
 * <pre>
 * {
 *   "table": "user",
 *   "data": [
 *     { "name": "test", "age": 20 }
 *   ]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlGenerateRequest {

    /** 表名 */
    private String table;

    /** 数据行列表（每行为字段名→值的映射） */
    private List<Map<String, Object>> data;
}
