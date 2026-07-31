package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 质量评分报告响应 DTO — 返回给前端的完整质量评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityReportResponse {

    /** 任务 ID */
    private Long taskId;

    /** 综合评分 (0-100) */
    private Double totalScore;

    /** 等级：优秀 / 良好 / 合格 / 不合格 */
    private String grade;

    /** 五项指标得分 */
    private Map<String, Double> metrics;

    /** 问题明细列表 */
    private List<QualityIssue> details;

    /**
     * 单个质量问题
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityIssue {
        /** 指标类别：completeness/uniqueness/consistency/validity/privacy */
        private String category;
        /** 问题级别：error / warning */
        private String level;
        /** 关联表名 */
        private String tableName;
        /** 关联字段名 */
        private String fieldName;
        /** 问题描述 */
        private String message;
        /** 改进建议 */
        private String suggestion;
    }
}
