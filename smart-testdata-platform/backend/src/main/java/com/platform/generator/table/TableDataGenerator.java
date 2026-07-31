package com.platform.generator.table;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.dto.GeneratePlanResponse.TablePlan;
import com.platform.generator.GeneratorEngine;
import com.platform.generator.context.GenerationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单表测试数据生成器 — 根据 TablePlan 批量生成测试数据
 *
 * <p>输入 TablePlan（表名、行数、字段计划），循环调用 {@link GeneratorEngine#execute(FieldPlan)}
 * 逐字段生成，返回 {@code List<Map<String, Object>>}。</p>
 *
 * <p>支持 {@link GenerationContext}：传入 context 时，标记了 primaryKey=true 的字段
 * 值会自动写入上下文，供后续外键关联字段引用。</p>
 *
 * <p>不连接数据库，纯内存生成。</p>
 *
 * <pre>
 * 输入:
 *   TablePlan { table: "user", count: 10, fields: [...] }
 *
 * 输出:
 *   [
 *     { "username": "张三", "email": "test@qq.com" },
 *     { "username": "李四", "email": "admin@qq.com" }
 *   ]
 * </pre>
 */
@Slf4j
@Service
public class TableDataGenerator {

    private final GeneratorEngine generatorEngine;

    public TableDataGenerator(GeneratorEngine generatorEngine) {
        this.generatorEngine = generatorEngine;
    }

    /**
     * 根据表计划生成指定数量的测试数据（无上下文）
     *
     * @param tablePlan 表生成计划（表名 + 行数 + 字段列表）
     * @return 生成的数据行列表
     */
    public List<Map<String, Object>> generate(TablePlan tablePlan) {
        return generate(tablePlan, null);
    }

    /**
     * 根据表计划生成指定数量的测试数据（带上下文）
     *
     * <p>如果传入 {@link GenerationContext}，标记了 <code>params.primaryKey = true</code>
     * 的字段值会在生成后自动写入上下文。</p>
     *
     * @param tablePlan 表生成计划（表名 + 行数 + 字段列表）
     * @param context   多表生成上下文，可为 null
     * @return 生成的数据行列表
     */
    public List<Map<String, Object>> generate(TablePlan tablePlan, GenerationContext context) {
        List<FieldPlan> fields = tablePlan.getFields();
        int count = tablePlan.getCount();
        String tableName = tablePlan.getTable();

        // 预扫描主键字段
        FieldPlan pkField = findPrimaryKeyField(fields);

        log.info("开始生成表数据: table={}, count={}, fields={}, pkField={}",
                tableName, count, fields.size(),
                pkField != null ? pkField.getName() : "none");

        List<Map<String, Object>> rows = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (FieldPlan field : fields) {
                Object value = generatorEngine.execute(field, context);
                row.put(field.getName(), value);
            }
            rows.add(row);

            // 自动写入主键到上下文
            if (context != null && pkField != null) {
                Object pkValue = row.get(pkField.getName());
                context.addGeneratedId(tableName, pkValue);
            }
        }

        log.info("表数据生成完成: table={}, 实际行数={}", tableName, rows.size());
        return rows;
    }

    /**
     * 扫描字段列表，找到标记了 primaryKey=true 的字段
     */
    private FieldPlan findPrimaryKeyField(List<FieldPlan> fields) {
        for (FieldPlan field : fields) {
            if (isPrimaryKey(field)) {
                return field;
            }
        }
        return null;
    }

    /**
     * 判断字段是否标记为主键
     * <p>条件：params 中包含 "primaryKey" 且值为 true</p>
     */
    private boolean isPrimaryKey(FieldPlan field) {
        Map<String, Object> params = field.getParams();
        if (params == null) {
            return false;
        }
        Object pk = params.get("primaryKey");
        return pk instanceof Boolean && (Boolean) pk;
    }
}
