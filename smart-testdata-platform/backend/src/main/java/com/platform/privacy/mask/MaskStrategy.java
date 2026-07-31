package com.platform.privacy.mask;

/**
 * 脱敏策略枚举
 *
 * <p>每种策略对应一种敏感字段类型的脱敏算法。
 * 策略仅定义脱敏方式，不包含具体实现逻辑（识别与执行分离）。</p>
 *
 * <p>映射关系由 {@link MaskRuleRegistry} 维护。</p>
 */
public enum MaskStrategy {

    /** 手机号脱敏：保留前3后4，中间用 **** 替换（如 138****1234） */
    PHONE_MASK,

    /** 邮箱脱敏：保留首字符和 @ 后域名，中间用 *** 替换（如 u***@example.com） */
    EMAIL_MASK,

    /** 身份证号脱敏：保留前6后4，中间用 **** 替换（如 110101****1234） */
    ID_CARD_MASK,

    /** 姓名脱敏：保留首字，其余用 * 替换（如 张* / 张**） */
    NAME_MASK,

    /** 地址脱敏：保留省市，详细地址用 ** 替换 */
    ADDRESS_MASK,

    /** 银行卡号脱敏：保留后4位，其余用 **** 替换（如 ****1234） */
    BANK_CARD_MASK
}
