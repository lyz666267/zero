package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        Object valuesObj = params != null ? params.get("values") : null;
        List<Object> values = valuesObj instanceof List ? (List<Object>) valuesObj : null;

        if (values == null || values.isEmpty()) {
            values = defaultValues(fieldPlan.getName());
        }

        int index = ThreadLocalRandom.current().nextInt(values.size());
        return values.get(index);
    }

    private List<Object> defaultValues(String fieldName) {
        String name = fieldName == null ? "" : fieldName.toLowerCase().trim();
        List<String> defaults = switch (name) {
            case "status", "state", "type", "category" ->
                    List.of("ACTIVE", "INACTIVE", "PENDING");
            case "gender", "sex" -> List.of("MALE", "FEMALE");
            case "role" -> List.of("USER", "ADMIN", "MANAGER");
            case "level" -> List.of("LOW", "MEDIUM", "HIGH");
            default -> List.of("VALUE_1", "VALUE_2", "VALUE_3");
        };
        return new ArrayList<>(defaults);
    }
}
