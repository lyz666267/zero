package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

/**
 * NULL 值生成器 — generator: "constant.null"
 */
@Component
public class NullGenerator implements Generator {

    @Override
    public Object generate(FieldPlan fieldPlan) {
        return null;
    }
}
