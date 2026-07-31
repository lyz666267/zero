package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成器测试请求 — 测试单个生成器
 *
 * <pre>
 * {
 *   "generator": "faker.email"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratorTestRequest {

    /** 生成器名称，如 "faker.name"、"random.integer" */
    private String generator;
}
