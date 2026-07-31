package com.platform.privacy;

/**
 * 敏感字段类型枚举
 *
 * <p>定义可能包含个人隐私数据的字段类别，用于敏感字段识别器
 * {@link SensitiveFieldDetector} 的分类输出。</p>
 */
public enum SensitiveFieldType {

    /** 手机/电话号码 */
    PHONE,

    /** 电子邮箱 */
    EMAIL,

    /** 身份证号 */
    ID_CARD,

    /** 姓名/用户名 */
    NAME,

    /** 地址 */
    ADDRESS,

    /** 银行卡号 */
    BANK_CARD,

    /** 未知敏感类型（暂不对外返回） */
    UNKNOWN
}
