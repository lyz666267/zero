package com.platform.generator.relation;

import com.platform.dto.ForeignKeyInfo;
import com.platform.exception.BusinessException;
import com.platform.generator.context.GenerationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 外键字段生成器 — 根据 foreignKey 信息从 {@link GenerationContext} 中随机选取关联表的主键
 *
 * <p>调用链：</p>
 * <pre>
 *   FieldPlan { name: "department_id", foreignKey: { table: "department", column: "id" } }
 *       ↓
 *   ForeignKeyGenerator.generate(foreignKey, context)
 *       ↓
 *   context.getIds("department") → [1, 2, 3]
 *       ↓
 *   ThreadLocalRandom → 随机返回 1/2/3
 * </pre>
 */
@Slf4j
@Component
public class ForeignKeyGenerator {

    /**
     * 根据外键信息从上下文中随机选择一个主键值
     *
     * @param foreignKey 外键信息（目标表 + 目标列）
     * @param context    多表生成上下文（包含已生成表的主键）
     * @return 随机选取的主键值
     * @throws BusinessException 如果关联表没有生成数据
     */
    public Object generate(ForeignKeyInfo foreignKey, GenerationContext context) {
        String refTable = foreignKey.getTable();

        if (!context.hasTable(refTable)) {
            throw new BusinessException("关联表 " + refTable + " 没有生成数据，请先生成 " + refTable + " 表");
        }

        List<Object> ids = context.getIds(refTable);

        if (ids.isEmpty()) {
            throw new BusinessException("关联表 " + refTable + " 的数据为空");
        }

        int index = ThreadLocalRandom.current().nextInt(ids.size());
        Object selected = ids.get(index);

        log.debug("外键生成: → {}.{} 随机选取 {}[{}] = {}",
                refTable, foreignKey.getColumn(), refTable, index, selected);
        return selected;
    }
}
