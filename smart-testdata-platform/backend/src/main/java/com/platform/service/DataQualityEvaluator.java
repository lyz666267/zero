package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.CachedSchemaResponse;
import com.platform.dto.QualityReportResponse;
import com.platform.dto.QualityReportResponse.QualityIssue;
import com.platform.entity.DataQualityReport;
import com.platform.mapper.DataQualityReportMapper;
import com.platform.quality.QualityMetric;
import com.platform.quality.QualityMetricResult;
import com.platform.quality.SchemaInfo;
import com.platform.schema.SchemaCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量评估编排器，将五项指标计算委托给独立的 {@link QualityMetric} 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQualityEvaluator {

    private static final double W_COMPLETENESS = 0.25;
    private static final double W_UNIQUENESS = 0.20;
    private static final double W_CONSISTENCY = 0.25;
    private static final double W_VALIDITY = 0.15;
    private static final double W_PRIVACY = 0.15;

    private final TestDataResultService resultService;
    private final SchemaCacheService schemaCacheService;
    private final DataQualityReportMapper reportMapper;
    private final ObjectMapper objectMapper;
    private final List<QualityMetric> metrics;

    @Transactional(rollbackFor = Exception.class)
    public QualityReportResponse evaluate(Long taskId, Long datasourceId) {
        log.info("开始质量评估: taskId={}, datasourceId={}", taskId, datasourceId);

        LinkedHashMap<String, List<Map<String, Object>>> tableData =
                resultService.findDataByTaskId(taskId);
        CachedSchemaResponse schema = loadSchema(datasourceId);

        if (tableData.isEmpty()) {
            log.warn("任务 {} 无生成数据，跳过质量评估", taskId);
            return buildEmptyReport(taskId);
        }

        SchemaInfo schemaInfo = new SchemaInfo(schema, tableData);
        List<QualityIssue> allIssues = new ArrayList<>();
        Map<String, Double> scores = new LinkedHashMap<>();

        for (QualityMetric metric : metrics) {
            QualityMetricResult result = metric.evaluate(schemaInfo.allRows(), schemaInfo);
            scores.put(result.key(), result.score());
            allIssues.addAll(result.issues());
        }

        double completeness = scores.getOrDefault("completeness", 0.0);
        double uniqueness = scores.getOrDefault("uniqueness", 0.0);
        double consistency = scores.getOrDefault("consistency", 0.0);
        double validity = scores.getOrDefault("validity", 0.0);
        double privacy = scores.getOrDefault("privacy", 0.0);

        double totalScore = completeness * W_COMPLETENESS
                + uniqueness * W_UNIQUENESS
                + consistency * W_CONSISTENCY
                + validity * W_VALIDITY
                + privacy * W_PRIVACY;
        totalScore = Math.round(totalScore * 100.0) / 100.0;

        String grade = calculateGrade(totalScore);
        saveReport(taskId, totalScore, grade, completeness, uniqueness,
                consistency, validity, privacy, allIssues);

        Map<String, Double> metricsResult = new LinkedHashMap<>();
        metricsResult.put("completeness", completeness);
        metricsResult.put("uniqueness", uniqueness);
        metricsResult.put("consistency", consistency);
        metricsResult.put("validity", validity);
        metricsResult.put("privacy", privacy);

        log.info("质量评估完成: taskId={}, totalScore={}, grade={}", taskId, totalScore, grade);

        return QualityReportResponse.builder()
                .taskId(taskId)
                .totalScore(totalScore)
                .grade(grade)
                .metrics(metricsResult)
                .details(allIssues)
                .build();
    }

    public QualityReportResponse getReport(Long taskId) {
        DataQualityReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<DataQualityReport>()
                        .eq(DataQualityReport::getTaskId, taskId));

        if (report == null) {
            return null;
        }

        Map<String, Double> metricsResult = new LinkedHashMap<>();
        metricsResult.put("completeness", report.getCompletenessScore());
        metricsResult.put("uniqueness", report.getUniquenessScore());
        metricsResult.put("consistency", report.getConsistencyScore());
        metricsResult.put("validity", report.getValidityScore());
        metricsResult.put("privacy", report.getPrivacyScore());

        List<QualityIssue> details = parseDetails(report.getDetailJson());

        return QualityReportResponse.builder()
                .taskId(report.getTaskId())
                .totalScore(report.getTotalScore())
                .grade(report.getGrade())
                .metrics(metricsResult)
                .details(details)
                .build();
    }

    private CachedSchemaResponse loadSchema(Long datasourceId) {
        try {
            if (schemaCacheService.hasCache(datasourceId)) {
                return schemaCacheService.getSchema(datasourceId);
            }
        } catch (Exception e) {
            log.warn("Schema 缓存加载失败: datasourceId={}, error={}",
                    datasourceId, e.getMessage());
        }
        return CachedSchemaResponse.builder()
                .tables(Collections.emptyList())
                .build();
    }

    private String calculateGrade(double score) {
        if (score >= 90) {
            return "优秀";
        }
        if (score >= 80) {
            return "良好";
        }
        if (score >= 60) {
            return "合格";
        }
        return "不合格";
    }

    private void saveReport(Long taskId, double totalScore, String grade,
                            double completeness, double uniqueness, double consistency,
                            double validity, double privacy, List<QualityIssue> issues) {
        reportMapper.delete(new LambdaQueryWrapper<DataQualityReport>()
                .eq(DataQualityReport::getTaskId, taskId));

        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(issues);
        } catch (JsonProcessingException e) {
            log.error("问题列表 JSON 序列化失败: taskId={}", taskId, e);
            detailJson = "[]";
        }

        DataQualityReport report = DataQualityReport.builder()
                .taskId(taskId)
                .totalScore(totalScore)
                .grade(grade)
                .completenessScore(completeness)
                .uniquenessScore(uniqueness)
                .consistencyScore(consistency)
                .validityScore(validity)
                .privacyScore(privacy)
                .detailJson(detailJson)
                .build();

        reportMapper.insert(report);
        log.info("质量报告已保存: taskId={}, totalScore={}", taskId, totalScore);
    }

    @SuppressWarnings("unchecked")
    private List<QualityIssue> parseDetails(String detailJson) {
        if (detailJson == null || detailJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(detailJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            QualityIssue.class));
        } catch (JsonProcessingException e) {
            log.error("问题列表 JSON 解析失败", e);
            return Collections.emptyList();
        }
    }

    private QualityReportResponse buildEmptyReport(Long taskId) {
        Map<String, Double> metricsResult = new LinkedHashMap<>();
        metricsResult.put("completeness", 0.0);
        metricsResult.put("uniqueness", 0.0);
        metricsResult.put("consistency", 0.0);
        metricsResult.put("validity", 0.0);
        metricsResult.put("privacy", 0.0);

        return QualityReportResponse.builder()
                .taskId(taskId)
                .totalScore(0.0)
                .grade("不合格")
                .metrics(metricsResult)
                .details(Collections.emptyList())
                .build();
    }
}
