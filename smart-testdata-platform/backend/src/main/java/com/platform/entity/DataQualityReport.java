package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据质量评分报告实体 — 映射 data_quality_report 表
 *
 * <p>记录每次测试数据生成任务完成后的五项质量指标评估结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("data_quality_report")
public class DataQualityReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务 ID */
    private Long taskId;

    /** 综合评分 (0-100) */
    private Double totalScore;

    /** 等级：优秀 / 良好 / 合格 / 不合格 */
    private String grade;

    /** 数据完整性评分 */
    private Double completenessScore;

    /** 数据唯一性评分 */
    private Double uniquenessScore;

    /** 数据关联一致性评分 */
    private Double consistencyScore;

    /** 数据格式合法性评分 */
    private Double validityScore;

    /** 隐私安全评分 */
    private Double privacyScore;

    /** 详细信息（JSON：问题列表 + 改进建议） */
    private String detailJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
