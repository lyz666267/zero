package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 常量值生成器 — generator: "constant.value"
 * <p>从 params.value 返回固定值；未配置时默认返回 1。</p>
 */
@Component
public class ConstantGenerator implements Generator {

    @Override
    public Object generate(FieldPlan fieldPlan) {
        Map<String, Object> params = fieldPlan.getParams();
        if (params == null || !params.containsKey("value")) {
            return 1;
        }
        return params.get("value");
    }
}
