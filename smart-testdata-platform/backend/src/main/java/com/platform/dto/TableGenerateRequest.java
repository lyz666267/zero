package com.platform.dto;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单表测试数据生成请求
 *
 * <pre>
 * {
 *   "table": "user",
 *   "count": 10,
 *   "fields": [
 *     { "name": "username", "generator": "faker.name" },
 *     { "name": "email",    "generator": "faker.email" }
 *   ]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableGenerateRequest {

    /** 表名 */
    private String table;

    /** 生成行数 */
    private int count;

    /** 字段生成计划列表 */
    private List<FieldPlan> fields;
}
