package com.platform.privacy.detector;

import com.platform.dto.SensitiveField;
import com.platform.privacy.SensitiveFieldType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 敏感字段检测结果
 *
 * <p>比 {@link SensitiveField} 更丰富，额外包含检测来源、原因说明和匹配值。
 * 提供 {@link #toSensitiveField()} 方法用于向后兼容。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectionResult {

    /** 数据库列名（原始名称） */
    private String columnName;

    /** 识别出的敏感类型 */
    private SensitiveFieldType type;

    /** 匹配置信度（0.0 ~ 1.0） */
    private double confidence;

    /** 检测来源 */
    private DetectionSource source;

    /** 人类可读的原因说明（LLM 检测时由模型生成） */
    private String reason;

    /** 触发匹配的实际数据值（正则检测时记录） */
    private String matchedValue;

    // ==================== 工厂方法 ====================

    /**
     * 从现有的 {@link SensitiveField} 创建 Keyword 来源的结果
     */
    public static DetectionResult fromKeyword(SensitiveField sf) {
        return new DetectionResult(
                sf.getColumnName(), sf.getType(), sf.getConfidence(),
                DetectionSource.KEYWORD, null, null
        );
    }

    /**
     * 创建 Regex 来源的结果
     */
    public static DetectionResult fromRegex(String columnName, SensitiveFieldType type,
                                            double confidence, String matchedValue) {
        return new DetectionResult(
                columnName, type, confidence,
                DetectionSource.REGEX, null, matchedValue
        );
    }

    /**
     * 创建 LLM 来源的结果
     */
    public static DetectionResult fromLLM(String columnName, SensitiveFieldType type,
                                          double confidence, String reason) {
        return new DetectionResult(
                columnName, type, confidence,
                DetectionSource.LLM, reason, null
        );
    }

    // ==================== 向后兼容 ====================

    /**
     * 转换为简洁的 {@link SensitiveField}，丢弃来源和原因信息
     */
    public SensitiveField toSensitiveField() {
        return new SensitiveField(columnName, type, confidence);
    }
}
