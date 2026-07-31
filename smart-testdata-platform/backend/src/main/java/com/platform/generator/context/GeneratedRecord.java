package com.platform.generator.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 已生成的表数据记录 — 包含表名和完整数据行
 *
 * <p>用于在多表生成流程中保存每个表的生成结果。</p>
 *
 * <pre>
 * {
 *   "table": "department",
 *   "data": [
 *     { "id": 1, "name": "技术部" },
 *     { "id": 2, "name": "市场部" }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedRecord {

    /** 表名 */
    private String table;

    /** 生成的数据行 */
    private List<Map<String, Object>> data;
}
