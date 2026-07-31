package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 生成计划响应 — 从 AI 服务返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePlanResponse {

    /** 是否成功 */
    private boolean success;

    /** 是否为 Mock 结果 */
    private boolean mock;

    /** 错误信息 */
    private String error;

    /** 生成计划对象 */
    private PlanData plan;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanData {
        private String taskName;
        private List<TablePlan> tables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TablePlan {
        private String table;
        private int count;
        private List<FieldPlan> fields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldPlan {
        private String name;
        private String generator;
        private Range range;
        private Map<String, Object> params;
        /** 外键信息 — 非 null 时表示该字段为外键，从关联表已生成主键中随机选取 */
        private ForeignKeyInfo foreignKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Range {
        private Integer min;
        private Integer max;
    }
}
