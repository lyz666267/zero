package com.platform.schema.relation;

import com.platform.dto.RelationAnalysisResponse.RelationItem;
import com.platform.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 表生成顺序服务 — 基于拓扑排序确定数据生成顺序
 *
 * <h3>算法</h3>
 * <p>使用 Kahn 算法进行拓扑排序：</p>
 * <ol>
 *   <li>计算每张表的入度（依赖了几张其他表）</li>
 *   <li>入度为 0 的表不依赖任何表，可以首先生成</li>
 *   <li>移除已排序的表，减少被依赖方的入度</li>
 *   <li>重复直到所有表都已排序</li>
 * </ol>
 *
 * <h3>循环依赖检测</h3>
 * <p>排序完成后，如果已排序表数 ≠ 总表数，说明存在循环依赖。
 * 循环依赖的表永远无法入度为 0，会在队列中永远等待。</p>
 *
 * <h3>示例</h3>
 * <pre>
 *   user.department_id → department.id
 *
 *   拓扑排序结果: ["department", "user"]
 *   含义: 先为 department 生成数据，再为 user 生成数据
 * </pre>
 */
@Slf4j
@Service
public class TableOrderService {

    /**
     * 拓扑排序 — 根据外键关系确定表生成的先后顺序
     *
     * @param relations 外键关系列表
     * @return 按生成顺序排列的表名（被依赖的表在前）
     * @throws BusinessException 存在循环依赖时抛出
     */
    public List<String> topologicalSort(List<RelationItem> relations) {
        // 无外键关系 → 任意顺序均可
        if (relations == null || relations.isEmpty()) {
            log.info("无外键关系，不需要拓扑排序");
            return Collections.emptyList();
        }

        // ---- 构建邻接表 ----
        // dependencies: table → 它依赖哪些表
        // dependents:   table → 哪些表依赖它（反向边，用于 Kahn 算法中传播入度减少）
        Set<String> allTables = new LinkedHashSet<>();
        Map<String, Set<String>> dependencies = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();

        for (RelationItem r : relations) {
            allTables.add(r.getTable());
            allTables.add(r.getReferencedTable());

            dependencies.computeIfAbsent(r.getTable(), k -> new HashSet<>())
                    .add(r.getReferencedTable());
            dependents.computeIfAbsent(r.getReferencedTable(), k -> new HashSet<>())
                    .add(r.getTable());

            // 确保所有表在两个映射中都有条目
            dependencies.putIfAbsent(r.getReferencedTable(), new HashSet<>());
            dependents.putIfAbsent(r.getTable(), new HashSet<>());
        }

        // ---- Kahn 算法 ----
        // 入度 = 该表依赖的其他表的数量
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Queue<String> queue = new LinkedList<>();

        for (String table : allTables) {
            int degree = dependencies.getOrDefault(table, Collections.emptySet()).size();
            inDegree.put(table, degree);
            if (degree == 0) {
                queue.offer(table);
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);

            // 对每个依赖 current 的表，将其入度减 1
            for (String dependent : dependents.getOrDefault(current, Collections.emptySet())) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                if (newDegree == 0) {
                    queue.offer(dependent);
                }
            }
        }

        // ---- 循环依赖检测 ----
        if (order.size() != allTables.size()) {
            Set<String> cycled = new LinkedHashSet<>(allTables);
            cycled.removeAll(order);
            String tables = String.join(", ", cycled);
            log.error("检测到循环依赖: tables={}", tables);
            throw new BusinessException("存在循环依赖，无法确定生成顺序。涉及表: " + tables);
        }

        log.info("拓扑排序完成: {} 张表, order={}", order.size(), order);
        return order;
    }
}
