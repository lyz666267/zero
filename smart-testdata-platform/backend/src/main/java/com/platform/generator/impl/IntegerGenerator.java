package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.dto.GeneratePlanResponse.Range;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 整数生成器 — generator: "random.integer"
 * <p>生成指定范围内的随机整数。</p>
 * <ul>
 *   <li>如果 FieldPlan 中指定了 range，使用 range.min ~ range.max</li>
 *   <li>否则默认生成 1 ~ 100</li>
 * </ul>
 */
@Component
public class IntegerGenerator implements Generator {

    private static final int DEFAULT_MIN = 1;
    private static final int DEFAULT_MAX = 100;

    @Override
    public Object generate(FieldPlan fieldPlan) {
        Range range = fieldPlan.getRange();
        int min = (range != null && range.getMin() != null) ? range.getMin() : DEFAULT_MIN;
        int max = (range != null && range.getMax() != null) ? range.getMax() : DEFAULT_MAX;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
