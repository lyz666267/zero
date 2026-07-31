package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 枚举值生成器 — generator: "enum.values"
 * <p>从 params.values 列表中随机选择一个值。</p>
 *
 * <pre>
 * FieldPlan.params:
 * {
 *   "values": ["ACTIVE", "INACTIVE", "PENDING"]
 * }
 * </pre>
 */
@Component
public class EnumGenerator implements Generator {

    @SuppressWarnings("unchecked")
    @Override
    public Object generate(FieldPlan fieldPlan) {
        Map<String, Object> params = fieldPlan.getParams();
        if (params == null || !params.containsKey("values")) {
            throw new IllegalArgumentException("enum.values 需要 params.values 参数");
        }

        Object valuesObj = params.get("values");
        if (!(valuesObj instanceof List)) {
            throw new IllegalArgumentException("params.values 必须是数组");
        }

        List<Object> values = (List<Object>) valuesObj;
        if (values.isEmpty()) {
            throw new IllegalArgumentException("params.values 不能为空");
        }

        int index = ThreadLocalRandom.current().nextInt(values.size());
        return values.get(index);
    }
}
