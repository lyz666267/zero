package com.platform.generator.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多表生成上下文 — 保存各表已生成的主键值，供外键关联字段引用
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} 存储。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 *   GenerationContext ctx = new GenerationContext();
 *
 *   // 生成 department 表 3 条数据
 *   tableDataGenerator.generate(departmentPlan, ctx);
 *   // → ctx 自动记录: { "department": [1, 2, 3] }
 *
 *   // 后续生成 employee 表时，外键字段可引用
 *   List&lt;Object&gt; deptIds = ctx.getIds("department");
 *   // → [1, 2, 3]
 * </pre>
 *
 * <p>存储结构：</p>
 * <pre>
 * {
 *   "department" → [1, 2, 3],
 *   "category"   → [10, 11]
 * }
 * </pre>
 */
public class GenerationContext {

    private final ConcurrentHashMap<String, List<Object>> tableIds = new ConcurrentHashMap<>();

    /**
     * 记录一条生成的主键值
     *
     * @param tableName 表名
     * @param id        主键值
     */
    public void addGeneratedId(String tableName, Object id) {
        tableIds.computeIfAbsent(tableName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(id);
    }

    /**
     * 获取某表已生成的所有主键值
     *
     * @param tableName 表名
     * @return 主键列表（不可变），未生成过则返回空列表
     */
    public List<Object> getIds(String tableName) {
        List<Object> ids = tableIds.get(tableName);
        if (ids == null) {
            return Collections.emptyList();
        }
        // 返回快照副本，避免外部并发修改
        synchronized (ids) {
            return List.copyOf(ids);
        }
    }

    /**
     * 检查是否已有某表的生成记录
     *
     * @param tableName 表名
     * @return true 如果该表已生成过主键
     */
    public boolean hasTable(String tableName) {
        List<Object> ids = tableIds.get(tableName);
        return ids != null && !ids.isEmpty();
    }

    /**
     * 获取所有已记录的表名
     *
     * @return 表名集合
     */
    public java.util.Set<String> tableNames() {
        return tableIds.keySet();
    }

    /**
     * 获取某表已生成主键的数量
     *
     * @param tableName 表名
     * @return 主键数量
     */
    public int idCount(String tableName) {
        List<Object> ids = tableIds.get(tableName);
        return ids == null ? 0 : ids.size();
    }

    /**
     * 清空所有记录
     */
    public void clear() {
        tableIds.clear();
    }
}
