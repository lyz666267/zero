package com.platform.generator.impl;

import com.github.javafaker.Faker;
import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 单词生成器 — generator: "faker.word"
 * <p>使用 JavaFaker 生成随机英文单词。</p>
 */
@Component
public class WordGenerator implements Generator {

    private final Faker faker = new Faker(Locale.ENGLISH);

    @Override
    public Object generate(FieldPlan fieldPlan) {
        return faker.lorem().word();
    }
}
