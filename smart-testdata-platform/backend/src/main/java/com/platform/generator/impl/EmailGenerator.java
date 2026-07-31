package com.platform.generator.impl;

import com.github.javafaker.Faker;
import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 邮箱生成器 — generator: "faker.email"
 * <p>使用 JavaFaker 生成随机邮箱地址。</p>
 */
@Component
public class EmailGenerator implements Generator {

    private final Faker faker = new Faker(new Locale("zh-CN"));

    @Override
    public Object generate(FieldPlan fieldPlan) {
        return faker.internet().emailAddress();
    }
}
