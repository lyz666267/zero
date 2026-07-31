package com.platform.generator.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.dto.DatabaseWriteRequest.TableData;
import com.platform.dto.GeneratePlanRequest;
import com.platform.dto.GeneratePlanResponse;
import com.platform.dto.GeneratePlanResponse.TablePlan;
import com.platform.dto.MultiTableGenerateResponse;
import com.platform.dto.MultiTableGenerateResponse.TableResult;
import com.platform.entity.TestDataTask;
import com.platform.entity.schema.SchemaColumn;
import com.platform.mapper.TestDataTaskMapper;
import com.platform.privacy.service.PrivacyAwareDataProcessor;
import com.platform.schema.SchemaCacheService;
import com.platform.service.AgentLogService;
import com.platform.service.DataQualityEvaluator;
import com.platform.service.TestDataResultService;
import com.platform.service.TestDataTaskPlanService;
import com.platform.service.TestdataService;
import com.platform.generator.persistence.MultiTableWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试数据生成任务异步执行器
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>查询任务 → 更新状态为 RUNNING</li>
 *   <li>从 Schema 缓存读取库表结构</li>
 *   <li>调用 AI 服务生成 {@link GeneratePlanResponse}（含 {@link TablePlan} 列表）</li>
 *   <li>{@link MultiTableDataGenerator} 按依赖顺序生成数据</li>
 *   <li>{@link MultiTableWriteService} 事务写入目标数据库</li>
 *   <li>成功 → status=SUCCESS，更新 successCount + finishTime</li>
 *   <li>失败 → status=FAILED，保存 errorMessage</li>
 * </ol>
 *
 * <h3>线程模型</h3>
 * <p>通过 {@code @Async("testdataTaskExecutor")} 在独立线程池中执行，
 * HTTP 请求创建任务后立即返回，不阻塞调用方。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataTaskExecutor {

    private final TestDataTaskMapper taskMapper;
    private final SchemaCacheService schemaCacheService;
    private final TestdataService testdataService;
    private final MultiTableDataGenerator multiTableDataGenerator;
    private final MultiTableWriteService multiTableWriteService;
    private final TestDataResultService testDataResultService;
    private final TestDataTaskPlanService testDataTaskPlanService;
    private final AgentLogService agentLogService;
    private final DataQualityEvaluator qualityEvaluator;
    private final PrivacyAwareDataProcessor privacyProcessor;

    /**
     * 异步执行测试数据生成任务
     *
     * @param taskId 任务 ID
     */
    @Async("testdataTaskExecutor")
    public void executeTask(Long taskId) {
        log.info("异步任务开始执行: taskId={}, thread={}",
                taskId, Thread.currentThread().getName());

        TestDataTask task = null;
        long taskStartTime = System.currentTimeMillis();
        try {
            // ========== Step 1: 需求解析 ==========
            long t1 = System.currentTimeMillis();
            task = taskMapper.selectById(taskId);
            if (task == null) {
                log.error("任务不存在: taskId={}", taskId);
                return;
            }
            logStep(taskId, 1, "PARSE", "需求解析",
                    Map.of("taskName", task.getTaskName() != null ? task.getTaskName() : ""),
                    Map.of("taskId", taskId, "datasourceId", task.getDatasourceId()),
                    "", System.currentTimeMillis() - t1);

            // 2. 更新状态 → RUNNING
            updateStatus(taskId, "RUNNING", null);
            log.info("任务状态更新: taskId={}, status=RUNNING", taskId);

            // ========== Step 2: Schema 分析 ==========
            long t2 = System.currentTimeMillis();
            Long datasourceId = task.getDatasourceId();
            Map<String, Object> schemaMap = buildSchemaMap(datasourceId);
            int tableCount = schemaMap.containsKey("tables")
                    ? ((List<?>) schemaMap.get("tables")).size() : 0;
            logStep(taskId, 2, "ANALYZE", "Schema分析",
                    Map.of("datasourceId", datasourceId),
                    Map.of("tableCount", tableCount),
                    "SchemaTool", System.currentTimeMillis() - t2);

            // ========== Step 3: 生成计划 ==========
            long t3 = System.currentTimeMillis();
            GeneratePlanRequest planRequest = new GeneratePlanRequest(
                    schemaMap,
                    task.getTaskName() != null ? task.getTaskName() : "生成测试数据"
            );
            GeneratePlanResponse planResponse = testdataService.generatePlan(planRequest);

            if (planResponse.getPlan() == null
                    || planResponse.getPlan().getTables() == null
                    || planResponse.getPlan().getTables().isEmpty()) {
                throw new RuntimeException("AI 服务未返回有效的表生成计划");
            }

            List<TablePlan> tablePlans = planResponse.getPlan().getTables();
            log.info("AI 计划获取成功: taskId={}, tables={}",
                    taskId, tablePlans.stream().map(TablePlan::getTable).collect(Collectors.toList()));
            logStep(taskId, 3, "PLAN", "生成计划",
                    Map.of("tableCount", tableCount),
                    Map.of("plannedTables", tablePlans.stream().map(TablePlan::getTable).collect(Collectors.toList()),
                           "mock", planResponse.isMock()),
                    "LLM Agent", System.currentTimeMillis() - t3);

            // 4.5. 保存生成计划（不阻塞主流程，异常仅记录日志）
            try {
                testDataTaskPlanService.savePlan(taskId, planResponse);
            } catch (Exception e) {
                log.warn("生成计划保存失败（不影响任务执行）: taskId={}, error={}",
                        taskId, e.getMessage());
            }

            // ========== Step 4: 调用数据生成工具 ==========
            long t4 = System.currentTimeMillis();
            MultiTableGenerateResponse genResult = multiTableDataGenerator.generate(tablePlans);

            if (!genResult.isSuccess() || genResult.getTables() == null) {
                throw new RuntimeException("多表数据生成失败");
            }

            int totalRows = genResult.getTables().stream().mapToInt(TableResult::getCount).sum();
            log.info("数据生成完成: taskId={}, tables={}, totalRows={}",
                    taskId, genResult.getTables().size(), totalRows);
            Map<String, Integer> tableRowCounts = genResult.getTables().stream()
                    .collect(Collectors.toMap(TableResult::getTable, TableResult::getCount));
            logStep(taskId, 4, "GENERATE", "调用数据生成工具",
                    Map.of("tablePlans", tablePlans.stream().map(tp ->
                            Map.of("table", tp.getTable(), "rowCount", tp.getCount())).collect(Collectors.toList())),
                    Map.of("tableResults", tableRowCounts, "totalRows", totalRows),
                    "MultiTableDataGenerator", System.currentTimeMillis() - t4);

            // ========== Step 5: 隐私处理（自动检测 + 脱敏，在写入 DB 之前执行） ==========
            long t5 = System.currentTimeMillis();
            int totalMaskedColumns = 0;
            try {
                Map<String, List<SchemaColumn>> tableColumnsMap = buildTableColumnsMap(datasourceId);
                for (TableResult tr : genResult.getTables()) {
                    List<SchemaColumn> columns = tableColumnsMap.get(tr.getTable());
                    if (columns != null && !columns.isEmpty()) {
                        List<Map<String, Object>> maskedData = privacyProcessor.processAuto(tr.getData(), columns);
                        tr.setData(maskedData);  // 替换为脱敏后的数据
                        totalMaskedColumns += columns.size();
                    }
                }
                log.info("隐私脱敏完成: taskId={}, tables={}, maskedColumns={}",
                        taskId, genResult.getTables().size(), totalMaskedColumns);
                logStep(taskId, 5, "PRIVACY", "调用隐私处理工具",
                        Map.of("tables", genResult.getTables().stream().map(TableResult::getTable).collect(Collectors.toList())),
                        Map.of("status", "SUCCESS", "maskedColumns", totalMaskedColumns),
                        "PrivacyAwareDataProcessor", System.currentTimeMillis() - t5);
            } catch (Exception e) {
                log.warn("隐私处理失败（不影响任务完成，写入原始数据）: taskId={}, error={}", taskId, e.getMessage());
                logStep(taskId, 5, "PRIVACY", "调用隐私处理工具",
                        Map.of("tables", genResult.getTables().stream().map(TableResult::getTable).collect(Collectors.toList())),
                        Map.of("status", "FAILED", "error", e.getMessage() != null ? e.getMessage() : "未知错误"),
                        "PrivacyAwareDataProcessor", System.currentTimeMillis() - t5);
            }

            // 6. 事务写入目标数据库（写入脱敏后的数据）
            List<TableData> tableDataList = new ArrayList<>();
            for (TableResult tr : genResult.getTables()) {
                TableData td = new TableData();
                td.setTable(tr.getTable());
                td.setData(tr.getData());
                tableDataList.add(td);
            }

            multiTableWriteService.writeAll(datasourceId, tableDataList);
            log.info("数据库写入完成: taskId={}", taskId);

            // 6.5. 保存生成结果（不阻塞主流程，异常仅记录日志）
            try {
                for (TableResult tr : genResult.getTables()) {
                    testDataResultService.saveResult(taskId, tr.getTable(), tr.getData());
                }
                log.info("生成结果保存完成: taskId={}, tables={}", taskId, genResult.getTables().size());
            } catch (Exception e) {
                log.error("保存生成结果失败（不影响任务状态）: taskId={}, error={}",
                        taskId, e.getMessage());
            }

            // ========== Step 5.5: 数据质量评估 ==========
            long t5_5 = System.currentTimeMillis();
            try {
                var qualityReport = qualityEvaluator.evaluate(taskId, task.getDatasourceId());
                log.info("质量评估完成: taskId={}, totalScore={}, grade={}",
                        taskId, qualityReport.getTotalScore(), qualityReport.getGrade());
                logStep(taskId, 5, "QUALITY", "数据质量评估",
                        Map.of("note", "五项指标综合评估"),
                        Map.of("totalScore", qualityReport.getTotalScore(),
                               "grade", qualityReport.getGrade(),
                               "metrics", qualityReport.getMetrics()),
                        "DataQualityEvaluator", System.currentTimeMillis() - t5_5);
            } catch (Exception e) {
                log.error("质量评估失败（不影响任务状态）: taskId={}, error={}",
                        taskId, e.getMessage());
                logStep(taskId, 5, "QUALITY", "数据质量评估",
                        Map.of("note", "五项指标综合评估"),
                        Map.of("status", "FAILED", "error", e.getMessage() != null ? e.getMessage() : "未知错误"),
                        "DataQualityEvaluator", System.currentTimeMillis() - t5_5);
            }

            // 7. 成功 → 更新状态
            updateStatus(taskId, "SUCCESS", null);
            taskMapper.update(null, new LambdaUpdateWrapper<TestDataTask>()
                    .eq(TestDataTask::getId, taskId)
                    .set(TestDataTask::getSuccessCount, totalRows)
                    .set(TestDataTask::getFinishTime, LocalDateTime.now()));
            log.info("任务执行成功: taskId={}, totalRows={}", taskId, totalRows);

            // ========== Step 6: 任务完成 ==========
            logStep(taskId, 6, "COMPLETE", "任务完成",
                    Map.of("totalRows", totalRows),
                    Map.of("status", "SUCCESS"),
                    "", System.currentTimeMillis() - taskStartTime);

        } catch (Exception e) {
            log.error("任务执行失败: taskId={}, error={}", taskId, e.getMessage(), e);
            updateStatus(taskId, "FAILED", e.getMessage());

            // 记录失败步骤
            try {
                logStep(taskId, 99, "COMPLETE", "任务失败",
                        Map.of("error", e.getMessage() != null ? e.getMessage() : "未知错误"),
                        Map.of("status", "FAILED"),
                        "", System.currentTimeMillis() - taskStartTime);
            } catch (Exception ignored) {
                // 日志记录本身失败不处理
            }
        }
    }

    /**
     * 记录 Agent 执行步骤（内部委托给 AgentLogService，异常安全）
     */
    private void logStep(Long taskId, int stepNumber, String stepType, String action,
                         Object inputData, Object outputData, String toolName, long executionTime) {
        try {
            agentLogService.logStep(taskId, stepNumber, stepType, action,
                    inputData, outputData, toolName, "SUCCESS", executionTime, "ToolAgent");
        } catch (Exception e) {
            log.warn("Agent 日志记录异常（不影响任务执行）: {}", e.getMessage());
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 更新任务状态
     */
    private void updateStatus(Long taskId, String status, String errorMessage) {
        LambdaUpdateWrapper<TestDataTask> wrapper = new LambdaUpdateWrapper<TestDataTask>()
                .eq(TestDataTask::getId, taskId)
                .set(TestDataTask::getStatus, status);

        if (errorMessage != null) {
            wrapper.set(TestDataTask::getErrorMessage,
                    errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
        }
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            // finishTime 在 SUCCESS 分支单独设置（含 successCount）
            if ("FAILED".equals(status)) {
                wrapper.set(TestDataTask::getFinishTime, LocalDateTime.now());
            }
        }

        taskMapper.update(null, wrapper);
    }

    /**
     * 从 Schema 缓存构建 AI 服务需要的 schema JSON 结构
     *
     * <p>格式：{ "database": "...", "tables": [ { "name": "...", "columns": [...] } ] }</p>
     * <p>缓存不存在时自动同步。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSchemaMap(Long datasourceId) {
        if (!schemaCacheService.hasCache(datasourceId)) {
            log.info("Schema 缓存不存在，自动同步: datasourceId={}", datasourceId);
            schemaCacheService.sync(datasourceId);
        }

        // 读取缓存的完整结构，转换为简化的 Map 格式（避免循环引用）
        var cached = schemaCacheService.getSchema(datasourceId);
        List<Map<String, Object>> tables = new ArrayList<>();
        for (var tableInfo : cached.getTables()) {
            Map<String, Object> tableMap = new LinkedHashMap<>();
            tableMap.put("name", tableInfo.getTableName());
            tableMap.put("comment", tableInfo.getTableComment() != null ? tableInfo.getTableComment() : "");

            List<Map<String, Object>> columns = new ArrayList<>();
            for (var col : tableInfo.getColumns()) {
                Map<String, Object> colMap = new LinkedHashMap<>();
                colMap.put("name", col.getName());
                colMap.put("type", col.getType());
                colMap.put("primaryKey", col.getPrimaryKey());
                colMap.put("nullable", col.getNullable());
                colMap.put("comment", col.getComment() != null ? col.getComment() : "");
                columns.add(colMap);
            }
            tableMap.put("columns", columns);
            tables.add(tableMap);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("database", "");
        schema.put("tables", tables);
        return schema;
    }

    /**
     * 从 Schema 缓存构建表名 → 列元数据的映射（供隐私脱敏使用）
     */
    private Map<String, List<SchemaColumn>> buildTableColumnsMap(Long datasourceId) {
        var cached = schemaCacheService.getSchema(datasourceId);
        Map<String, List<SchemaColumn>> result = new LinkedHashMap<>();
        for (var tableInfo : cached.getTables()) {
            List<SchemaColumn> columns = tableInfo.getColumns().stream().map(ci -> {
                SchemaColumn col = new SchemaColumn();
                col.setColumnName(ci.getName());
                col.setColumnType(ci.getType());
                col.setColumnComment(ci.getComment());
                return col;
            }).collect(Collectors.toList());
            result.put(tableInfo.getTableName(), columns);
        }
        return result;
    }
}
