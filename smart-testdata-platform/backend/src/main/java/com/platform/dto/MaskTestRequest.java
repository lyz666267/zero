package com.platform.dto;

import lombok.Data;

/**
 * 脱敏测试请求 — 测试单个脱敏策略的效果
 *
 * <p>前端输入原始值和策略，后端返回脱敏后的值，用于实时预览。</p>
 */
@Data
public class MaskTestRequest {

    /** 脱敏策略名称（如 PHONE_MASK） */
    private String strategy;

    /** 待脱敏的原始值 */
    private String value;

    /** 敏感字段类型（可选，用于日志记录） */
    private String sensitiveType;

    public String getStrategy() { return strategy; }
    public String getValue() { return value; }
    public String getSensitiveType() { return sensitiveType; }
}
