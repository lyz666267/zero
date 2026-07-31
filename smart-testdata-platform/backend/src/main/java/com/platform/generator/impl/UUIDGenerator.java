package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * UUID 生成器 — generator: "uuid"
 * <p>生成随机 UUID 字符串（36 字符，含连字符）。</p>
 */
@Component
public class UUIDGenerator implements Generator {

    @Override
    public Object generate(FieldPlan fieldPlan) {
        return UUID.randomUUID().toString();
    }
}
