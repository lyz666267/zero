package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成计划查询响应
 *
 * <p>用于 GET /api/testdata/task/{taskId}/plan 接口，
 * 返回 AI 生成计划的解析结果。</p>
 *
 * <h3>响应示例</h3>
 * <pre>
 * {
 *   "success": true,
 *   "data": {
 *     "taskName": "生成测试数据",
 *     "tables": [...]
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationPlanResponse {

    /** 是否成功获取计划 */
    private boolean success;

    /** 计划数据（plan_json 解析结果），不存在时为 null */
    private Object data;
}
