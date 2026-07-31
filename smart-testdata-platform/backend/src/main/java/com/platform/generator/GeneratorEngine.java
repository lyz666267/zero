package com.platform.generator;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.exception.BusinessException;
import com.platform.generator.context.GenerationContext;
import com.platform.generator.relation.ForeignKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 生成器引擎 — 根据 FieldPlan 中的 generator 名称选择对应生成器并执行
 *
 * <p>支持两种生成模式：</p>
 * <ol>
 *   <li><b>外键模式</b>：fieldPlan.foreignKey != null → 从 {@link GenerationContext} 随机选取关联表主键</li>
 *   <li><b>生成器模式</b>：通过 generator 名称查找注册的生成器执行</li>
 * </ol>
 *
 * <p>调用链：</p>
 * <pre>
 *   FieldPlan: { name: "email", generator: "faker.email" }
 *       ↓
 *   GeneratorEngine.execute(fieldPlan)
 *       ↓
 *   GeneratorRegistry.get("faker.email") → EmailGenerator
 *       ↓
 *   EmailGenerator.generate(fieldPlan) → "test@qq.com"
 * </pre>
 */
@Slf4j
@Service
public class GeneratorEngine {

    private final GeneratorRegistry registry;
    private final ForeignKeyGenerator foreignKeyGenerator;

    public GeneratorEngine(GeneratorRegistry registry, ForeignKeyGenerator foreignKeyGenerator) {
        this.registry = registry;
        this.foreignKeyGenerator = foreignKeyGenerator;
    }

    /**
     * 根据字段计划生成一个测试数据值（无上下文）
     *
     * @param fieldPlan 字段生成计划
     * @return 生成的值
     */
    public Object execute(FieldPlan fieldPlan) {
        return execute(fieldPlan, null);
    }

    /**
     * 根据字段计划生成一个测试数据值（带多表上下文）
     *
     * <p>优先判断：如果 fieldPlan.foreignKey != null 且 context != null，
     * 则调用 {@link ForeignKeyGenerator} 从上下文中随机选取关联表主键；
     * 否则走原有 GeneratorRegistry 注册的生成器。</p>
     *
     * @param fieldPlan 字段生成计划
     * @param context   多表生成上下文，可为 null
     * @return 生成的值
     * @throws BusinessException 如果对应的生成器未注册
     */
    public Object execute(FieldPlan fieldPlan, GenerationContext context) {
        // 优先：外键字段 → 从上下文获取关联表主键
        if (fieldPlan.getForeignKey() != null && context != null) {
            Object value = foreignKeyGenerator.generate(fieldPlan.getForeignKey(), context);
            log.debug("generate FK: {}.{} → {}", fieldPlan.getForeignKey().getTable(),
                    fieldPlan.getName(), value);
            return value;
        }

        // 默认：通过注册中心查找生成器
        String generatorName = fieldPlan.getGenerator();

        if (generatorName == null || generatorName.isBlank()) {
            throw new BusinessException("FieldPlan.generator 不能为空");
        }

        Generator generator = registry.get(generatorName);
        if (generator == null) {
            throw new BusinessException("未找到生成器: " + generatorName
                    + "，已注册: " + registry.registeredNames());
        }

        Object value = generator.generate(fieldPlan);
        log.debug("generate: {}.{} → {} = {}", generatorName, fieldPlan.getName(), value);
        return value;
    }
}
