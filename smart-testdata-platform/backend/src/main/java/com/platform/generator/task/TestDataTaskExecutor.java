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
import com.platform.generator.persistence.MultiTableWriteService;
import com.platform.mapper.TestDataTaskMapper;
import com.platform.privacy.service.PrivacyAwareDataProcessor;
import com.platform.schema.SchemaCacheService;
import com.platform.service.AgentLogService;
import com.platform.service.DataQualityEvaluator;
import com.platform.service.TestDataResultService;
import com.platform.service.TestDataTaskPlanService;
import com.platform.service.TestdataService;
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
 * 测试数据生成任务异步执行器。
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
     * 异步执行测试数据生成任务。
     *
     * @param taskId 任务 ID
     */
    @Async("testdataTaskExecutor")
    public void executeTask(Long taskId) {
        log.info("异步任务开始执行: taskId={}, thread={}",
                taskId, Thread.currentThread().getName());

        long taskStartTime = System.currentTimeMillis();
        try {
            TestDataTask task = loadAndValidateTask(taskId);
            if (task == null) {
                return;
            }

            Long datasourceId = task.getDatasourceId();

            // Step 2: Schema 分析
            long t2 = System.currentTimeMillis();
            Map<String, Object> schemaMap = buildSchemaMap(datasourceId);
            int tableCount = schemaMap.containsKey("tables")
                    ? ((List<?>) schemaMap.get("tables")).size() : 0;
            logStep(taskId, 2, "ANALYZE", "Schema分析",
                    Map.of("datasourceId", datasourceId),
                    Map.of("tableCount", tableCount),
                    "SchemaTool", System.currentTimeMillis() - t2);

            // Step 3: AI 生成计划
            GeneratePlanResponse planResponse = callAiService(task, schemaMap, tableCount);

            // Step 4-6: 生成、脱敏、写入
            MultiTableGenerateResponse genResult = generateAndWriteData(
                    taskId, datasourceId, planResponse.getPlan().getTables());
            int totalRows = genResult.getTables().stream()
                    .mapToInt(TableResult::getCount)
                    .sum();

            // Step 7: 保存结果
            saveResults(taskId, genResult);

            // Step 8: 质量评估
            runQualityEvaluation(taskId, datasourceId);

            // Step 9: 成功收尾
            updateStatus(taskId, "SUCCESS", null);
            taskMapper.update(null, new LambdaUpdateWrapper<TestDataTask>()
                    .eq(TestDataTask::getId, taskId)
                    .set(TestDataTask::getSuccessCount, totalRows)
                    .set(TestDataTask::getFinishTime, LocalDateTime.now()));
            log.info("任务执行成功: taskId={}, totalRows={}", taskId, totalRows);

            logStep(taskId, 6, "COMPLETE", "任务完成",
                    Map.of("totalRows", totalRows),
                    Map.of("status", "SUCCESS"),
                    "", System.currentTimeMillis() - taskStartTime);
        } catch (Exception e) {
            log.error("任务执行失败: taskId={}, error={}", taskId, e.getMessage(), e);
            updateStatus(taskId, "FAILED", e.getMessage());

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

    private TestDataTask loadAndValidateTask(Long taskId) {
        long t1 = System.currentTimeMillis();
        TestDataTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在: taskId={}", taskId);
            return null;
        }

        logStep(taskId, 1, "PARSE", "需求解析",
                Map.of("taskName", task.getTaskName() != null ? task.getTaskName() : ""),
                Map.of("taskId", taskId, "datasourceId", task.getDatasourceId()),
                "", System.currentTimeMillis() - t1);

        updateStatus(taskId, "RUNNING", null);
        log.info("任务状态更新: taskId={}, status=RUNNING", taskId);
        return task;
    }

    private GeneratePlanResponse callAiService(
            TestDataTask task,
            Map<String, Object> schemaMap,
            int tableCount) {
        Long taskId = task.getId();
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

        try {
            testDataTaskPlanService.savePlan(taskId, planResponse);
        } catch (Exception e) {
            log.warn("生成计划保存失败（不影响任务执行）: taskId={}, error={}",
                    taskId, e.getMessage());
        }
        return planResponse;
    }

    private MultiTableGenerateResponse generateAndWriteData(
            Long taskId,
            Long datasourceId,
            List<TablePlan> tablePlans) {
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
                        Map.of("table", tp.getTable(), "rowCount", tp.getCount()))
                        .collect(Collectors.toList())),
                Map.of("tableResults", tableRowCounts, "totalRows", totalRows),
                "MultiTableDataGenerator", System.currentTimeMillis() - t4);

        processPrivacy(taskId, datasourceId, genResult);

        List<TableData> tableDataList = new ArrayList<>();
        for (TableResult tr : genResult.getTables()) {
            TableData td = new TableData();
            td.setTable(tr.getTable());
            td.setData(tr.getData());
            tableDataList.add(td);
        }

        multiTableWriteService.writeAll(datasourceId, tableDataList);
        log.info("数据库写入完成: taskId={}", taskId);
        return genResult;
    }

    private void processPrivacy(
            Long taskId,
            Long datasourceId,
            MultiTableGenerateResponse genResult) {
        long t5 = System.currentTimeMillis();
        int totalMaskedColumns = 0;
        try {
            Map<String, List<SchemaColumn>> tableColumnsMap = buildTableColumnsMap(datasourceId);
            for (TableResult tr : genResult.getTables()) {
                List<SchemaColumn> columns = tableColumnsMap.get(tr.getTable());
                if (columns != null && !columns.isEmpty()) {
                    List<Map<String, Object>> maskedData =
                            privacyProcessor.processAuto(tr.getData(), columns);
                    tr.setData(maskedData);
                    totalMaskedColumns += columns.size();
                }
            }

            log.info("隐私脱敏完成: taskId={}, tables={}, maskedColumns={}",
                    taskId, genResult.getTables().size(), totalMaskedColumns);
            logStep(taskId, 5, "PRIVACY", "调用隐私处理工具",
                    Map.of("tables", genResult.getTables().stream()
                            .map(TableResult::getTable).collect(Collectors.toList())),
                    Map.of("status", "SUCCESS", "maskedColumns", totalMaskedColumns),
                    "PrivacyAwareDataProcessor", System.currentTimeMillis() - t5);
        } catch (Exception e) {
            log.warn("隐私处理失败（不影响任务完成，写入原始数据）: taskId={}, error={}",
                    taskId, e.getMessage());
            logStep(taskId, 5, "PRIVACY", "调用隐私处理工具",
                    Map.of("tables", genResult.getTables().stream()
                            .map(TableResult::getTable).collect(Collectors.toList())),
                    Map.of("status", "FAILED",
                            "error", e.getMessage() != null ? e.getMessage() : "未知错误"),
                    "PrivacyAwareDataProcessor", System.currentTimeMillis() - t5);
        }
    }

    private void saveResults(Long taskId, MultiTableGenerateResponse genResult) {
        try {
            for (TableResult tr : genResult.getTables()) {
                testDataResultService.saveResult(taskId, tr.getTable(), tr.getData());
            }
            log.info("生成结果保存完成: taskId={}, tables={}",
                    taskId, genResult.getTables().size());
        } catch (Exception e) {
            log.error("保存生成结果失败（不影响任务状态）: taskId={}, error={}",
                    taskId, e.getMessage());
        }
    }

    private void runQualityEvaluation(Long taskId, Long datasourceId) {
        long t5_5 = System.currentTimeMillis();
        try {
            var qualityReport = qualityEvaluator.evaluate(taskId, datasourceId);
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
                    Map.of("status", "FAILED",
                            "error", e.getMessage() != null ? e.getMessage() : "未知错误"),
                    "DataQualityEvaluator", System.currentTimeMillis() - t5_5);
        }
    }

    /**
     * 记录 Agent 执行步骤。
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

    private void updateStatus(Long taskId, String status, String errorMessage) {
        LambdaUpdateWrapper<TestDataTask> wrapper = new LambdaUpdateWrapper<TestDataTask>()
                .eq(TestDataTask::getId, taskId)
                .set(TestDataTask::getStatus, status);

        if (errorMessage != null) {
            wrapper.set(TestDataTask::getErrorMessage,
                    errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
        }
        if ("FAILED".equals(status)) {
            wrapper.set(TestDataTask::getFinishTime, LocalDateTime.now());
        }

        taskMapper.update(null, wrapper);
    }

    /**
     * 从 Schema 缓存构建 AI 服务需要的 schema JSON 结构。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSchemaMap(Long datasourceId) {
        if (!schemaCacheService.hasCache(datasourceId)) {
            log.info("Schema 缓存不存在，自动同步: datasourceId={}", datasourceId);
            schemaCacheService.sync(datasourceId);
        }

        var cached = schemaCacheService.getSchema(datasourceId);
        List<Map<String, Object>> tables = new ArrayList<>();
        for (var tableInfo : cached.getTables()) {
            Map<String, Object> tableMap = new LinkedHashMap<>();
            tableMap.put("name", tableInfo.getTableName());
            tableMap.put("comment", tableInfo.getTableComment() != null
                    ? tableInfo.getTableComment() : "");

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
     * 从 Schema 缓存构建表名到列元数据的映射，供隐私脱敏使用。
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
