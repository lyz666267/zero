package com.platform.generator;

import com.platform.dto.GeneratePlanResponse.FieldPlan;

/**
 * 字段生成器接口 — 所有生成器必须实现此接口
 *
 * <p>每个生成器对应一种 generator 名称（如 "faker.name"），
 * 根据 FieldPlan 中的参数生成单个测试数据值。</p>
 */
public interface Generator {

    /**
     * 根据字段计划生成一个测试数据值
     *
     * @param fieldPlan 字段生成计划（包含 generator 名称、range、params）
     * @return 生成的值（String / Integer / Boolean 等）
     */
    Object generate(FieldPlan fieldPlan);
}
