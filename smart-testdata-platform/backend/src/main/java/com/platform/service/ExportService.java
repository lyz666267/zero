package com.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.platform.entity.TestDataTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.dto.TaskResponse;
import com.platform.entity.TestDataTask;
import com.platform.exception.BusinessException;
import com.platform.mapper.TestDataTaskMapper;
import com.platform.sql.InsertSqlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试数据导出服务 — 支持 CSV / SQL INSERT / JSON 三种格式
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>从已有任务结果读取数据（复用 TestDataResultService）</li>
 *   <li>按指定格式转换并返回字符串内容</li>
 *   <li>不修改核心生成逻辑</li>
 * </ul>
 *
 * <h3>支持的导出格式</h3>
 * <table>
 *   <tr><th>格式</th><th>说明</th></tr>
 *   <tr><td>CSV</td><td>逗号分隔值，首行为表头，字符串自动加引号转义</td></tr>
 *   <tr><td>SQL</td><td>INSERT INTO ... VALUES 批量语句，复用 InsertSqlBuilder</td></tr>
 *   <tr><td>JSON</td><td>按表分组的标准 JSON 数组，美化输出</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final TestDataTaskMapper taskMapper;
    private final TestDataResultService resultService;
    private final InsertSqlBuilder sqlBuilder;
    private final ObjectMapper objectMapper;

    /** 导出文件时间戳格式 */
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 列出可导出任务（仅 SUCCESS 状态，按创建时间倒序）
     *
     * @return 已完成任务的 TaskResponse 列表
     */
    public List<TaskResponse> listExportableTasks() {
        List<TestDataTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<TestDataTask>()
                        .eq(TestDataTask::getStatus, "SUCCESS")
                        .orderByDesc(TestDataTask::getCreateTime)
        );
        return tasks.stream().map(TaskResponse::fromEntity).collect(Collectors.toList());
    }

    /**
     * 按任务 ID 导出生成数据
     *
     * @param taskId 任务 ID
     * @param format 导出格式：CSV / SQL / JSON（大小写不敏感）
     * @return 格式化的导出内容字符串
     * @throws BusinessException 任务不存在 / 格式不支持 / 无数据可导出
     */
    public String exportTaskData(Long taskId, String format) {
        // 1. 校验任务存在
        TestDataTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在: id=" + taskId);
        }

        // 2. 加载数据
        LinkedHashMap<String, List<Map<String, Object>>> dataMap = resultService.findDataByTaskId(taskId);
        if (dataMap.isEmpty()) {
            throw new BusinessException(400, "任务无数据可导出: taskId=" + taskId);
        }
        // 检查是否所有表都为空
        long totalRows = dataMap.values().stream().mapToLong(List::size).sum();
        if (totalRows == 0) {
            throw new BusinessException(400, "任务无数据可导出: taskId=" + taskId);
        }

        // 3. 按格式转换
        String fmt = format != null ? format.toUpperCase().trim() : "JSON";

        return switch (fmt) {
            case "CSV" -> toCsv(dataMap, task);
            case "SQL" -> toSqlInsert(dataMap);
            case "JSON" -> toJson(dataMap, task);
            default -> throw new BusinessException(400,
                    "不支持的导出格式: " + format + "，可选: CSV / SQL / JSON");
        };
    }

    /**
     * 生成默认文件名
     */
    public String generateFileName(Long taskId, String format) {
        String ts = LocalDateTime.now().format(TS_FORMAT);
        String ext = switch (format.toUpperCase().trim()) {
            case "CSV" -> ".csv";
            case "SQL" -> ".sql";
            default -> ".json";
        };
        return "task_" + taskId + "_" + ts + ext;
    }

    // ==================== CSV 导出 ====================

    /**
     * 转换为 CSV 格式
     *
     * <p>多表之间用空行 + 表名注释分隔。
     * 每张表首行为列名，后续行为数据。</p>
     *
     * <pre>
     * # table: sys_user
     * id,name,phone
     * 1,"张三","138****1234"
     * 2,"李四","139****5678"
     * </pre>
     */
    String toCsv(LinkedHashMap<String, List<Map<String, Object>>> dataMap, TestDataTask task) {
        StringBuilder sb = new StringBuilder();

        // 文件头注释
        sb.append("# 任务: ").append(task.getTaskName()).append("\n");
        sb.append("# 导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("# 格式: CSV\n\n");

        boolean first = true;
        for (Map.Entry<String, List<Map<String, Object>>> entry : dataMap.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) continue;

            if (!first) sb.append("\n");
            first = false;

            sb.append("# table: ").append(tableName).append("\n");

            // 收集列名（保持首次出现顺序）
            List<String> columns = collectColumns(rows);
            if (columns.isEmpty()) continue;

            // 表头
            sb.append(String.join(",", columns)).append("\n");

            // 数据行
            for (Map<String, Object> row : rows) {
                sb.append(csvRow(columns, row)).append("\n");
            }
        }

        return sb.toString();
    }

    /** 从数据行中收集所有列名（去重，保持首次出现顺序） */
    private List<String> collectColumns(List<Map<String, Object>> rows) {
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if (row != null) {
                for (String key : row.keySet()) {
                    seen.add(key);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /** 构建单行 CSV */
    private String csvRow(List<String> columns, Map<String, Object> row) {
        return columns.stream()
                .map(col -> csvEscape(row != null ? row.get(col) : null))
                .collect(Collectors.joining(","));
    }

    /** CSV 值转义（字符串加双引号，内部双引号转义为两个双引号） */
    String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        // 包含逗号、双引号或换行 → 加双引号包裹
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        // 纯数字不加引号
        if (value instanceof Number) {
            return s;
        }
        // 其他字符串加双引号
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ==================== SQL INSERT 导出 ====================

    /**
     * 转换为 SQL INSERT 语句
     *
     * <p>复用 InsertSqlBuilder 生成每条 INSERT。
     * 每张表一条 INSERT 语句，批量 VALUES。</p>
     *
     * <pre>
     * -- table: sys_user (2 rows)
     * INSERT INTO sys_user (id, name, phone)
     * VALUES
     * (1, '张三', '138****1234'),
     * (2, '李四', '139****5678');
     * </pre>
     */
    String toSqlInsert(LinkedHashMap<String, List<Map<String, Object>>> dataMap) {
        StringBuilder sb = new StringBuilder();

        sb.append("-- ========================================\n");
        sb.append("-- 测试数据导出 SQL\n");
        sb.append("-- 导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("-- 格式: SQL INSERT\n");
        sb.append("-- ========================================\n\n");

        for (Map.Entry<String, List<Map<String, Object>>> entry : dataMap.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) continue;

            sb.append("-- table: ").append(tableName)
                    .append(" (").append(rows.size()).append(" rows)\n");

            try {
                String sql = sqlBuilder.build(tableName, rows);
                sb.append(sql).append("\n\n");
            } catch (IllegalArgumentException e) {
                log.warn("SQL 生成跳过表 {}: {}", tableName, e.getMessage());
                sb.append("-- SKIPPED: ").append(e.getMessage()).append("\n\n");
            }
        }

        return sb.toString();
    }

    // ==================== JSON 导出 ====================

    /**
     * 转换为 JSON 格式
     *
     * <p>按表分组的嵌套结构，美化输出。</p>
     *
     * <pre>
     * {
     *   "taskId": 1,
     *   "taskName": "...",
     *   "tables": {
     *     "sys_user": [
     *       { "id": 1, "name": "张三" }
     *     ]
     *   }
     * }
     * </pre>
     */
    String toJson(LinkedHashMap<String, List<Map<String, Object>>> dataMap, TestDataTask task) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("taskId", task.getId());
        root.put("taskName", task.getTaskName());
        root.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        root.put("format", "JSON");

        // 按表分组
        Map<String, Object> tables = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : dataMap.entrySet()) {
            tables.put(entry.getKey(), entry.getValue());
        }
        root.put("tables", tables);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: taskId={}", task.getId(), e);
            throw new BusinessException(500, "JSON 导出序列化失败: " + e.getMessage());
        }
    }
}
