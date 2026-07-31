package com.platform.generator.persistence;

import com.platform.dto.WriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 数据库批量写入器 — 通过 JdbcTemplate 将测试数据批量写入目标数据库
 *
 * <h3>安全性</h3>
 * <p>使用参数化 SQL（? 占位符）+ {@code JdbcTemplate.batchUpdate}，
 * 不拼接用户数据，安全防 SQL 注入。</p>
 *
 * <h3>示例</h3>
 * <pre>
 * WriteResult result = databaseWriter.write(dataSource, "user", List.of(
 *     Map.of("name", "张三", "age", 20)
 * ));
 * // → WriteResult { success=true, table="user", insertCount=1 }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseWriter {

    private final InsertStatementBuilder statementBuilder;

    /**
     * 将数据批量写入指定表
     *
     * @param dataSource 目标数据源
     * @param tableName  目标表名
     * @param data       数据行列表
     * @return 写入结果
     */
    public WriteResult write(DataSource dataSource, String tableName, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return WriteResult.builder()
                    .success(true)
                    .table(tableName)
                    .insertCount(0)
                    .build();
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // 1. 收集所有列名（保持首次出现顺序）
        List<String> columns = collectColumns(data);

        // 2. 构建参数化 SQL
        String sql = statementBuilder.buildSql(tableName, columns);

        // 3. 转换为批量参数
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                values[i] = row.get(columns.get(i));
            }
            batchArgs.add(values);
        }

        // 4. 执行批量写入
        int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);
        int totalInserted = results.length;

        log.info("批量写入完成: table={}, rows={}", tableName, totalInserted);

        return WriteResult.builder()
                .success(true)
                .table(tableName)
                .insertCount(totalInserted)
                .build();
    }

    /**
     * 从数据行中收集所有列名，保持首次出现顺序
     */
    private List<String> collectColumns(List<Map<String, Object>> data) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : data) {
            if (row != null) {
                seen.addAll(row.keySet());
            }
        }
        return new ArrayList<>(seen);
    }
}
