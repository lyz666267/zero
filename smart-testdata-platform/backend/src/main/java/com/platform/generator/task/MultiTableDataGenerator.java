package com.platform.generator.task;

import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.dto.GeneratePlanResponse.TablePlan;
import com.platform.dto.ForeignKeyInfo;
import com.platform.dto.MultiTableGenerateResponse;
import com.platform.dto.MultiTableGenerateResponse.TableResult;
import com.platform.dto.RelationAnalysisResponse.RelationItem;
import com.platform.exception.BusinessException;
import com.platform.generator.context.GenerationContext;
import com.platform.generator.table.TableDataGenerator;
import com.platform.schema.relation.TableOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多表测试数据生成调度器 — 根据外键依赖关系自动排序并批量生成多表数据
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>扫描所有 {@link TablePlan} 中标记了 {@code foreignKey} 的字段</li>
 *   <li>构建 {@link RelationItem} 列表</li>
 *   <li>调用 {@link TableOrderService#topologicalSort} 获取生成顺序</li>
 *   <li>创建 {@link GenerationContext} 共享上下文</li>
 *   <li>按序调用 {@link TableDataGenerator#generate(TablePlan, GenerationContext)}</li>
 *   <li>返回全部生成结果</li>
 * </ol>
 *
 * <h3>依赖关系提取</h3>
 * <pre>
 *   FieldPlan { name: "dept_id", foreignKey: { table: "department", column: "id" } }
 *     ↓
 *   RelationItem { table: "employee", column: "dept_id",
 *                  referencedTable: "department", referencedColumn: "id" }
 * </pre>
 *
 * <h3>排序规则</h3>
 * <ul>
 *   <li>有外键依赖的表 → 按拓扑排序（被依赖表在前）</li>
 *   <li>无外键依赖的表 → 排在最后（原始顺序）</li>
 *   <li>循环依赖 → 抛出 {@link BusinessException}</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>
 * 输入: [employee (FK→department), department]
 * 排序: [department, employee]
 * 生成: department → ctx: {department: [1,2,3]} → employee.department_id ∈ {1,2,3}
 * </pre>
 */
@Slf4j
@Service
public class MultiTableDataGenerator {

    private final TableOrderService tableOrderService;
    private final TableDataGenerator tableDataGenerator;

    public MultiTableDataGenerator(TableOrderService tableOrderService,
                                   TableDataGenerator tableDataGenerator) {
        this.tableOrderService = tableOrderService;
        this.tableDataGenerator = tableDataGenerator;
    }

    /**
     * 按依赖顺序生成多表测试数据
     *
     * @param tablePlans 表生成计划列表（任意顺序）
     * @return 各表生成结果（按生成顺序排列）
     * @throws BusinessException 存在循环依赖时抛出
     */
    public MultiTableGenerateResponse generate(List<TablePlan> tablePlans) {
        if (tablePlans == null || tablePlans.isEmpty()) {
            return MultiTableGenerateResponse.builder()
                    .success(true)
                    .tables(List.of())
                    .build();
        }

        // 1. 建立表名 → TablePlan 映射（保持插入顺序）
        Map<String, TablePlan> planMap = new LinkedHashMap<>();
        for (TablePlan plan : tablePlans) {
            planMap.put(plan.getTable(), plan);
        }

        // 2. 提取所有外键关系
        List<RelationItem> relations = extractRelations(tablePlans);
        log.info("提取到 {} 条外键关系: {}", relations.size(),
                relations.stream().map(r -> r.getTable() + "." + r.getColumn()
                        + " → " + r.getReferencedTable() + "." + r.getReferencedColumn())
                        .toList());

        // 3. 拓扑排序
        List<String> sortedOrder;
        try {
            sortedOrder = tableOrderService.topologicalSort(relations);
        } catch (BusinessException e) {
            log.error("循环依赖检测: {}", e.getMessage());
            throw e;
        }

        // 4. 构建最终生成顺序：排序表在前，无依赖表在后
        List<String> finalOrder = new ArrayList<>(sortedOrder);
        for (String tableName : planMap.keySet()) {
            if (!finalOrder.contains(tableName)) {
                finalOrder.add(tableName);
            }
        }
        log.info("最终生成顺序: {}", finalOrder);

        // 5. 创建共享上下文，按序生成
        GenerationContext context = new GenerationContext();
        List<TableResult> results = new ArrayList<>();

        for (String tableName : finalOrder) {
            TablePlan plan = planMap.get(tableName);
            log.info("开始生成表: table={}, count={}", tableName, plan.getCount());

            List<Map<String, Object>> data = tableDataGenerator.generate(plan, context);

            results.add(TableResult.builder()
                    .table(tableName)
                    .count(data.size())
                    .data(data)
                    .build());

            log.info("表生成完成: table={}, rows={}, contextKeys={}",
                    tableName, data.size(), context.tableNames());
        }

        log.info("多表生成完成: {} 张表, {} 条数据",
                results.size(),
                results.stream().mapToInt(TableResult::getCount).sum());

        return MultiTableGenerateResponse.builder()
                .success(true)
                .tables(results)
                .build();
    }

    /**
     * 从所有表计划中提取外键关系
     *
     * <p>遍历每个 TablePlan 的每个 FieldPlan，如果字段标记了 foreignKey，
     * 则构建对应的 RelationItem。</p>
     *
     * @param tablePlans 表生成计划列表
     * @return 外键关系列表
     */
    private List<RelationItem> extractRelations(List<TablePlan> tablePlans) {
        List<RelationItem> relations = new ArrayList<>();

        for (TablePlan plan : tablePlans) {
            if (plan.getFields() == null) {
                continue;
            }
            for (FieldPlan field : plan.getFields()) {
                ForeignKeyInfo fk = field.getForeignKey();
                if (fk == null && "fk.reference".equals(field.getGenerator())) {
                    Map<String, Object> params = field.getParams();
                    if (params != null && params.get("refTable") != null) {
                        fk = ForeignKeyInfo.builder()
                                .table(String.valueOf(params.get("refTable")))
                                .column(params.get("refColumn") != null
                                        ? String.valueOf(params.get("refColumn")) : "id")
                                .build();
                    }
                }
                if (fk != null) {
                    relations.add(RelationItem.builder()
                            .table(plan.getTable())
                            .column(field.getName())
                            .referencedTable(fk.getTable())
                            .referencedColumn(fk.getColumn())
                            .build());
                }
            }
        }

        return relations;
    }
}
