package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.dto.GeneratePlanResponse.Range;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 小数生成器 — generator: "random.decimal"
 * <p>生成指定范围内的随机小数（BigDecimal），保留 2 位小数。</p>
 * <ul>
 *   <li>如果 FieldPlan 中指定了 range，使用 range.min ~ range.max</li>
 *   <li>否则默认生成 0.00 ~ 10000.00</li>
 * </ul>
 */
@Component
public class DecimalGenerator implements Generator {

    private static final double DEFAULT_MIN = 0.0;
    private static final double DEFAULT_MAX = 10000.0;

    @Override
    public Object generate(FieldPlan fieldPlan) {
        Range range = fieldPlan.getRange();
        double min = (range != null && range.getMin() != null) ? range.getMin().doubleValue() : DEFAULT_MIN;
        double max = (range != null && range.getMax() != null) ? range.getMax().doubleValue() : DEFAULT_MAX;

        double value = ThreadLocalRandom.current().nextDouble(min, max);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
