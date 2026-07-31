package com.platform.privacy.executor;

import com.platform.privacy.mask.MaskStrategy;

/**
 * 脱敏执行器接口
 *
 * <p>根据 {@link MaskStrategy} 对原始字段值进行脱敏处理。
 * 实现类负责具体的字符串转换逻辑。</p>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>只负责值转换，不涉及字段识别（由 {@code SensitiveFieldDetector} 负责）</li>
 *   <li>不涉及策略选择（由 {@code MaskRuleRegistry} 负责）</li>
 * </ul>
 */
public interface MaskExecutor {

    /**
     * 对原始值执行脱敏
     *
     * @param value    原始字段值
     * @param strategy 脱敏策略
     * @return 脱敏后的值；输入为 {@code null} 或空字符串时返回原值
     */
    String mask(String value, MaskStrategy strategy);
}
