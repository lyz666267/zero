package com.platform.generator;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成器注册中心 — 维护 generator 名称 → Generator 实例的映射
 *
 * <p>Spring 自动发现所有 {@link Generator} 实现类，
 * 通过 Bean 名称映射到对应的 generator 标识符。</p>
 *
 * <pre>
 * Map 示例:
 * {
 *   "faker.name"        → nameGenerator,
 *   "faker.email"       → emailGenerator,
 *   "random.integer"    → integerGenerator,
 *   "faker.word"        → wordGenerator,
 *   "random.boolean"    → booleanGenerator,
 *   "enum.values"       → enumGenerator,
 *   "random.decimal"    → decimalGenerator,
 *   "time.past_datetime" → dateTimeGenerator,
 *   "uuid"              → uuidGenerator,
 *   "faker.phone"       → phoneGenerator,
 *   "constant.value"    → constantGenerator,
 *   "constant.null"     → nullGenerator
 * }
 * </pre>
 */
@Slf4j
@Component
public class GeneratorRegistry {

    private final List<Generator> generators;
    private final Map<String, Generator> registry = new HashMap<>();

    public GeneratorRegistry(List<Generator> generators) {
        this.generators = generators;
    }

    /**
     * Spring 启动时自动注册所有 Generator
     * <p>映射规则：取类名去掉"Generator"后缀并转换为 generator 标识符</p>
     * <ul>
     *   <li>NameGenerator → "faker.name"</li>
     *   <li>EmailGenerator → "faker.email"</li>
     *   <li>IntegerGenerator → "random.integer"</li>
     *   <li>WordGenerator → "faker.word"</li>
     *   <li>BooleanGenerator → "random.boolean"</li>
     *   <li>EnumGenerator → "enum.values"</li>
     *   <li>DecimalGenerator → "random.decimal"</li>
     *   <li>DateTimeGenerator → "time.past_datetime"</li>
     *   <li>UUIDGenerator → "uuid"</li>
     *   <li>PhoneGenerator → "faker.phone"</li>
     *   <li>ConstantGenerator → "constant.value"</li>
     *   <li>NullGenerator → "constant.null"</li>
     * </ul>
     */
    @PostConstruct
    public void init() {
        for (Generator generator : generators) {
            String beanName = generator.getClass().getSimpleName();
            String key = beanNameToGeneratorKey(beanName);
            registry.put(key, generator);
            log.info("Registered generator: {} → {}", key, beanName);
        }
    }

    /**
     * 类名 → generator 标识符映射
     */
    private String beanNameToGeneratorKey(String beanName) {
        return switch (beanName) {
            case "NameGenerator"     -> "faker.name";
            case "EmailGenerator"    -> "faker.email";
            case "IntegerGenerator"  -> "random.integer";
            case "WordGenerator"     -> "faker.word";
            case "BooleanGenerator"  -> "random.boolean";
            case "EnumGenerator"    -> "enum.values";
            case "DecimalGenerator"  -> "random.decimal";
            case "DateTimeGenerator" -> "time.past_datetime";
            case "UUIDGenerator"     -> "uuid";
            case "PhoneGenerator"    -> "faker.phone";
            case "ConstantGenerator" -> "constant.value";
            case "NullGenerator"     -> "constant.null";
            default                  -> beanName.replace("Generator", "").toLowerCase();
        };
    }

    /**
     * 根据 generator 名称获取对应的生成器实例
     *
     * @param generatorName 如 "faker.name"、"random.integer"
     * @return Generator 实例，未找到返回 null
     */
    public Generator get(String generatorName) {
        return registry.get(normalizeGeneratorName(generatorName));
    }

    /**
     * 检查是否已注册指定名称的生成器
     */
    public boolean contains(String generatorName) {
        return registry.containsKey(normalizeGeneratorName(generatorName));
    }

    /**
     * 获取所有已注册的生成器名称
     */
    public java.util.Set<String> registeredNames() {
        return registry.keySet();
    }

    /**
     * 兼容历史/AI 返回的生成器别名
     */
    private String normalizeGeneratorName(String generatorName) {
        if (generatorName == null) {
            return null;
        }
        if ("faker.phone_number".equals(generatorName)) {
            return "faker.phone";
        }
        if ("faker.uuid4".equals(generatorName)) {
            return "uuid";
        }
        if ("faker.first_name".equals(generatorName)
                || "faker.last_name".equals(generatorName)
                || "faker.user_name".equals(generatorName)
                || "faker.username".equals(generatorName)) {
            return "faker.name";
        }
        if ("faker.text".equals(generatorName)
                || "faker.sentence".equals(generatorName)
                || "faker.paragraph".equals(generatorName)
                || "faker.address".equals(generatorName)
                || "faker.city".equals(generatorName)
                || "faker.country".equals(generatorName)
                || "faker.postcode".equals(generatorName)
                || "faker.company".equals(generatorName)
                || "faker.job".equals(generatorName)
                || "faker.bs".equals(generatorName)
                || "faker.url".equals(generatorName)
                || "faker.ipv4".equals(generatorName)
                || "faker.domain_name".equals(generatorName)
                || "faker.ssn".equals(generatorName)
                || "faker.passport_number".equals(generatorName)
                || "faker.json".equals(generatorName)) {
            return "faker.word";
        }
        if ("time.past_date".equals(generatorName)
                || "time.date_this_year".equals(generatorName)
                || "time.time".equals(generatorName)) {
            return "time.past_datetime";
        }
        return generatorName;
    }
}
