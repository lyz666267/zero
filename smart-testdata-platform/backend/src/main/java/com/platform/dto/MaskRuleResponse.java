package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脱敏规则响应 — 用于前端规则展示和可视化
 *
 * <p>包含规则的完整信息：类型、策略、描述、前后对比示例。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaskRuleResponse {

    /** 敏感字段类型（如 PHONE、EMAIL） */
    private String sensitiveType;

    /** 敏感字段类型中文标签 */
    private String typeLabel;

    /** 脱敏策略名称（如 PHONE_MASK） */
    private String strategy;

    /** 脱敏规则说明 */
    private String description;

    /** 示例原始值 */
    private String exampleInput;

    /** 示例脱敏后值 */
    private String exampleOutput;
}
