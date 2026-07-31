package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脱敏测试响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaskTestResponse {

    /** 脱敏策略 */
    private String strategy;

    /** 原始值 */
    private String originalValue;

    /** 脱敏后的值 */
    private String maskedValue;
}
