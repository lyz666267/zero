package com.platform.dto;

import com.platform.privacy.SensitiveFieldType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 敏感字段识别结果
 *
 * <p>封装字段名、敏感类型及匹配置信度。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveField {

    /** 数据库列名（原始名称） */
    private String columnName;

    /** 识别出的敏感类型 */
    private SensitiveFieldType type;

    /** 匹配置信度（0.0 ~ 1.0），精确匹配 0.95，包含匹配 0.8 */
    private double confidence;
}
