package com.platform.privacy.mask;

import com.platform.privacy.SensitiveFieldType;

/**
 * 脱敏规则
 *
 * <p>定义敏感字段类型到脱敏策略的映射关系。</p>
 *
 * @param type        敏感字段类型
 * @param strategy    对应的脱敏策略
 * @param description 规则描述
 */
public record MaskRule(
        SensitiveFieldType type,
        MaskStrategy strategy,
        String description
) {
}
