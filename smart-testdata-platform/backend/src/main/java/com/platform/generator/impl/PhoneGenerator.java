package com.platform.generator.impl;

import com.github.javafaker.Faker;
import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 手机号生成器 — generator: "faker.phone"
 * <p>使用 JavaFaker 生成随机中国手机号。</p>
 */
@Component
public class PhoneGenerator implements Generator {

    private final Faker faker = new Faker(new Locale("zh-CN"));

    @Override
    public Object generate(FieldPlan fieldPlan) {
        return faker.phoneNumber().cellPhone();
    }
}
