package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 布尔值生成器 — generator: "random.boolean"
 * <p>随机返回 true 或 false。</p>
 */
@Component
public class BooleanGenerator implements Generator {

    @Override
    public Object generate(FieldPlan fieldPlan) {
        return ThreadLocalRandom.current().nextBoolean();
    }
}
