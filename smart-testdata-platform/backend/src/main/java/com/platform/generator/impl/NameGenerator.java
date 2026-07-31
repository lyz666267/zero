package com.platform.generator.impl;

import com.github.javafaker.Faker;
import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 姓名生成器 — generator: "faker.name"
 * <p>使用 JavaFaker 生成随机姓名（中文）。</p>
 */
@Component
public class NameGenerator implements Generator {

    private final Faker faker = new Faker(new Locale("zh-CN"));

    @Override
    public Object generate(FieldPlan fieldPlan) {
        return faker.name().fullName();
    }
}
