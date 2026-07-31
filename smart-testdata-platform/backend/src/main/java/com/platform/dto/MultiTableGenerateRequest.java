package com.platform.dto;

import com.platform.dto.GeneratePlanResponse.TablePlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 多表测试数据生成请求
 *
 * <pre>
 * {
 *   "tables": [
 *     {
 *       "table": "department",
 *       "count": 3,
 *       "fields": [
 *         { "name": "id", "generator": "random.integer", "params": { "primaryKey": true } },
 *         { "name": "name", "generator": "faker.name" }
 *       ]
 *     },
 *     {
 *       "table": "employee",
 *       "count": 5,
 *       "fields": [
 *         { "name": "name", "generator": "faker.name" },
 *         { "name": "department_id", "foreignKey": { "table": "department", "column": "id" } }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiTableGenerateRequest {

    /** 表生成计划列表 */
    private List<TablePlan> tables;
}
