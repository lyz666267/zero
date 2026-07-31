package com.platform.generator.impl;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.generator.Generator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 过去时间生成器 — generator: "time.past_datetime"
 * <p>生成过去 30 天内的随机时间，格式为 yyyy-MM-dd HH:mm:ss。</p>
 */
@Component
public class DateTimeGenerator implements Generator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_PAST_SECONDS = 30L * 24 * 60 * 60; // 30 天

    @Override
    public Object generate(FieldPlan fieldPlan) {
        long secondsAgo = ThreadLocalRandom.current().nextLong(MAX_PAST_SECONDS);
        LocalDateTime pastTime = LocalDateTime.now().minusSeconds(secondsAgo);
        return pastTime.format(FORMATTER);
    }
}
