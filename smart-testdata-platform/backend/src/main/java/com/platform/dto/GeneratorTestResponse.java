package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成器测试响应
 *
 * <pre>
 * {
 *   "success": true,
 *   "value": "test@qq.com"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratorTestResponse {

    /** 是否成功 */
    private boolean success;

    /** 生成的值 */
    private Object value;
}
