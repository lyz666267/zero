package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.CachedSchemaResponse;
import com.platform.dto.CachedSchemaResponse.CachedColumnInfo;
import com.platform.dto.CachedSchemaResponse.CachedTableInfo;
import com.platform.dto.QualityReportResponse;
import com.platform.dto.QualityReportResponse.QualityIssue;
import com.platform.entity.DataQualityReport;
import com.platform.mapper.DataQualityReportMapper;
import com.platform.privacy.SensitiveFieldDetector;
import com.platform.privacy.SensitiveFieldType;
import com.platform.schema.SchemaCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据质量评估器 — 对生成的测试数据执行五项指标质量评估
 *
 * <h3>五项评估指标</h3>
 * <ol>
 *   <li><b>数据完整性 (25%)</b> — 非空字段比例 + 必填字段缺失检测</li>
 *   <li><b>数据唯一性 (20%)</b> — 主键/唯一字段重复检测</li>
 *   <li><b>关联一致性 (25%)</b> — 外键引用有效性验证</li>
 *   <li><b>格式合法性 (15%)</b> — 邮箱/手机号/日期/枚举值格式校验</li>
 *   <li><b>隐私安全 (15%)</b> — 敏感字段是否已完成脱敏</li>
 * </ol>
 *
 * <h3>评分算法</h3>
 * <pre>
 * totalScore = completeness×0.25 + uniqueness×0.20 + consistency×0.25
 *            + validity×0.15 + privacy×0.15
 * </pre>
 *
 * <h3>等级划分</h3>
 * <ul>
 *   <li>90~100 — 优秀</li>
 *   <li>80~89  — 良好</li>
 *   <li>60~79  — 合格</li>
 *   <li>&lt;60  — 不合格</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQualityEvaluator {

    private final TestDataResultService resultService;
    private final SchemaCacheService schemaCacheService;
    private final SensitiveFieldDetector sensitiveFieldDetector;
    private final DataQualityReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    // ==================== 正则模式（用于格式校验） ====================

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("^\\d{17}[\\dXx]$");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("^\\d{16,19}$");

    // 评分权重
    private static final double W_COMPLETENESS = 0.25;
    private static final double W_UNIQUENESS   = 0.20;
    private static final double W_CONSISTENCY  = 0.25;
    private static final double W_VALIDITY     = 0.15;
    private static final double W_PRIVACY      = 0.15;

    private List<String> resolvePrimaryKeyColumns(CachedTableInfo tableInfo,
                                                   List<Map<String, Object>> rows) {
        if (tableInfo != null && tableInfo.getColumns() != null) {
            List<String> pks = tableInfo.getColumns().stream()
                    .filter(col -> Boolean.TRUE.equals(col.getPrimaryKey()))
                    .map(CachedColumnInfo::getName)
                    .collect(Collectors.toList());
            if (!pks.isEmpty()) {
                return pks;
            }
        }

        return Collections.emptyList();
    }

    /**
     * 对已完成的任务执行质量评估
     *
     * @param taskId       任务 ID
     * @param datasourceId 数据源 ID
     * @return 质量评估响应（含五项指标 + 问题明细 + 等级）
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityReportResponse evaluate(Long taskId, Long datasourceId) {
        log.info("开始质量评估: taskId={}, datasourceId={}", taskId, datasourceId);

        // 1. 加载数据
        LinkedHashMap<String, List<Map<String, Object>>> tableData = resultService.findDataByTaskId(taskId);
        CachedSchemaResponse schema = loadSchema(datasourceId);
        List<QualityIssue> allIssues = new ArrayList<>();

        if (tableData.isEmpty()) {
            log.warn("任务 {} 无生成数据，跳过质量评估", taskId);
            return buildEmptyReport(taskId);
        }

        // 2. 执行五项评估
        double completeness = evaluateCompleteness(tableData, schema, allIssues);
        double uniqueness   = evaluateUniqueness(tableData, schema, allIssues);
        double consistency  = evaluateConsistency(tableData, schema, allIssues);
        double validity     = evaluateValidity(tableData, schema, allIssues);
        double privacy      = evaluatePrivacy(tableData, schema, allIssues);

        // 3. 综合评分
        double totalScore = completeness * W_COMPLETENESS
                          + uniqueness   * W_UNIQUENESS
                          + consistency  * W_CONSISTENCY
                          + validity     * W_VALIDITY
                          + privacy      * W_PRIVACY;

        // Round to 2 decimal places
        totalScore = Math.round(totalScore * 100.0) / 100.0;

        String grade = calculateGrade(totalScore);

        // 4. 保存报告
        saveReport(taskId, totalScore, grade, completeness, uniqueness,
                consistency, validity, privacy, allIssues);

        // 5. 构建响应
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("completeness", completeness);
        metrics.put("uniqueness", uniqueness);
        metrics.put("consistency", consistency);
        metrics.put("validity", validity);
        metrics.put("privacy", privacy);

        log.info("质量评估完成: taskId={}, totalScore={}, grade={}", taskId, totalScore, grade);

        return QualityReportResponse.builder()
                .taskId(taskId)
                .totalScore(totalScore)
                .grade(grade)
                .metrics(metrics)
                .details(allIssues)
                .build();
    }

    /**
     * 按任务 ID 查询已保存的质量报告
     *
     * @param taskId 任务 ID
     * @return 质量报告响应，不存在返回 null
     */
    public QualityReportResponse getReport(Long taskId) {
        DataQualityReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<DataQualityReport>()
                        .eq(DataQualityReport::getTaskId, taskId));

        if (report == null) {
            return null;
        }

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("completeness", report.getCompletenessScore());
        metrics.put("uniqueness", report.getUniquenessScore());
        metrics.put("consistency", report.getConsistencyScore());
        metrics.put("validity", report.getValidityScore());
        metrics.put("privacy", report.getPrivacyScore());

        List<QualityIssue> details = parseDetails(report.getDetailJson());

        return QualityReportResponse.builder()
                .taskId(report.getTaskId())
                .totalScore(report.getTotalScore())
                .grade(report.getGrade())
                .metrics(metrics)
                .details(details)
                .build();
    }

    // ==================== 五项指标评估 ====================

    /**
     * 1. 数据完整性 — 非空字段比例
     */
    private double evaluateCompleteness(
            LinkedHashMap<String, List<Map<String, Object>>> tableData,
            CachedSchemaResponse schema,
            List<QualityIssue> issues) {

        long totalCells = 0;
        long nonNullCells = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry : tableData.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();

            if (rows.isEmpty()) continue;

            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    totalCells++;
                    if (cell.getValue() != null && !cell.getValue().toString().isEmpty()) {
                        nonNullCells++;
                    }
                }
            }

            // 检查必填字段（nullable=false）是否有缺失
            CachedTableInfo tableInfo = findTable(schema, tableName);
            if (tableInfo != null) {
                for (CachedColumnInfo col : tableInfo.getColumns()) {
                    if (Boolean.FALSE.equals(col.getNullable())) {
                        long missing = rows.stream()
                                .filter(row -> {
                                    Object val = row.get(col.getName());
                                    return val == null || val.toString().isEmpty();
                                })
                                .count();
                        if (missing > 0) {
                            issues.add(QualityIssue.builder()
                                    .category("completeness")
                                    .level("error")
                                    .tableName(tableName)
                                    .fieldName(col.getName())
                                    .message(String.format("%s.%s 必填字段有 %d 条空值", tableName, col.getName(), missing))
                                    .suggestion("检查生成器配置，确保必填字段生成非空值")
                                    .build());
                        }
                    }
                }
            }
        }

        return totalCells == 0 ? 100.0 : Math.round(nonNullCells * 10000.0 / totalCells) / 100.0;
    }

    /**
     * 2. 数据唯一性 — 检查主键和唯一字段重复
     */
    private double evaluateUniqueness(
            LinkedHashMap<String, List<Map<String, Object>>> tableData,
            CachedSchemaResponse schema,
            List<QualityIssue> issues) {

        long totalPkValues = 0;
        long uniquePkValues = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry : tableData.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();

            if (rows.isEmpty()) continue;

            CachedTableInfo tableInfo = findTable(schema, tableName);
            List<String> pkColumns = resolvePrimaryKeyColumns(tableInfo, rows);
            for (String pkColumn : pkColumns) {
                Set<Object> seen = new HashSet<>();
                int count = 0;
                for (Map<String, Object> row : rows) {
                    Object val = row.get(pkColumn);
                    if (val != null) {
                        count++;
                        seen.add(val);
                    }
                }
                totalPkValues += count;
                uniquePkValues += seen.size();

                long duplicates = count - seen.size();
                if (duplicates > 0) {
                    issues.add(QualityIssue.builder()
                            .category("uniqueness")
                            .level("error")
                            .tableName(tableName)
                            .fieldName(pkColumn)
                            .message(String.format("%s.%s 主键有 %d 个重复值", tableName, pkColumn, duplicates))
                            .suggestion("检查主键生成策略，使用 UUID 或自增序列避免重复")
                            .build());
                }
            }

            // 检查整行是否重复
            Set<String> rowSignatures = new HashSet<>();
            for (Map<String, Object> row : rows) {
                rowSignatures.add(row.toString());
            }
            long rowDupes = rows.size() - rowSignatures.size();
            if (rowDupes > 0) {
                issues.add(QualityIssue.builder()
                        .category("uniqueness")
                        .level("warning")
                        .tableName(tableName)
                        .fieldName("*")
                        .message(String.format("%s 表有 %d 行完全重复", tableName, rowDupes))
                        .suggestion("增加随机种子变化或增加数据多样性参数")
                        .build());
            }
        }

        return totalPkValues == 0 ? 100.0 : Math.round(uniquePkValues * 10000.0 / totalPkValues) / 100.0;
    }

    /**
     * 3. 关联一致性 — 检查外键引用是否有效
     */
    private double evaluateConsistency(
            LinkedHashMap<String, List<Map<String, Object>>> tableData,
            CachedSchemaResponse schema,
            List<QualityIssue> issues) {

        // 构建所有主键值索引：tableName → Set of PK values
        Map<String, Set<Object>> pkIndex = new HashMap<>();
        for (CachedTableInfo tableInfo : schema.getTables()) {
            for (CachedColumnInfo col : tableInfo.getColumns()) {
                if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                    List<Map<String, Object>> rows = tableData.get(tableInfo.getTableName());
                    if (rows != null) {
                        Set<Object> pkValues = rows.stream()
                                .map(row -> row.get(col.getName()))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
                        pkIndex.put(tableInfo.getTableName(), pkValues);
                    }
                }
            }
        }

        long totalFkValues = 0;
        long validFkValues = 0;

        for (CachedTableInfo tableInfo : schema.getTables()) {
            for (CachedColumnInfo col : tableInfo.getColumns()) {
                // 检查外键引用
                if (col.getForeignRefTable() != null && !col.getForeignRefTable().isEmpty()) {
                    String refTable = col.getForeignRefTable();
                    Set<Object> refPkValues = pkIndex.get(refTable);

                    List<Map<String, Object>> rows = tableData.get(tableInfo.getTableName());
                    if (rows == null || refPkValues == null || refPkValues.isEmpty()) continue;

                    long invalidCount = 0;
                    for (Map<String, Object> row : rows) {
                        Object fkVal = row.get(col.getName());
                        if (fkVal != null) {
                            totalFkValues++;
                            if (refPkValues.contains(fkVal)) {
                                validFkValues++;
                            } else {
                                invalidCount++;
                            }
                        }
                    }

                    if (invalidCount > 0) {
                        issues.add(QualityIssue.builder()
                                .category("consistency")
                                .level("error")
                                .tableName(tableInfo.getTableName())
                                .fieldName(col.getName())
                                .message(String.format("%s.%s 有 %d 个外键值在 %s.%s 中不存在",
                                        tableInfo.getTableName(), col.getName(),
                                        invalidCount, refTable,
                                        col.getForeignRefColumn() != null ? col.getForeignRefColumn() : "id"))
                                .suggestion(String.format("确保 %s 表数据先于 %s 表生成，且外键引用正确",
                                        refTable, tableInfo.getTableName()))
                                .build());
                    }
                }
            }
        }

        return totalFkValues == 0 ? 100.0 : Math.round(validFkValues * 10000.0 / totalFkValues) / 100.0;
    }

    /**
     * 4. 格式合法性 — 检查邮箱、手机号、日期等格式
     */
    private double evaluateValidity(
            LinkedHashMap<String, List<Map<String, Object>>> tableData,
            CachedSchemaResponse schema,
            List<QualityIssue> issues) {

        long totalChecked = 0;
        long validCount = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry : tableData.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();

            if (rows.isEmpty()) continue;

            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    String colName = cell.getKey();
                    Object val = cell.getValue();
                    if (val == null) continue;

                    String strVal = val.toString();
                    Pattern pattern = inferPattern(colName);
                    if (pattern == null) continue; // 不需要格式校验的字段，跳过

                    totalChecked++;
                    if (pattern.matcher(strVal).matches()) {
                        validCount++;
                    }
                }
            }

            // 汇总错误报告
            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    String colName = cell.getKey();
                    Object val = cell.getValue();
                    if (val == null) continue;

                    Pattern pattern = inferPattern(colName);
                    if (pattern == null) continue;

                    if (!pattern.matcher(val.toString()).matches()) {
                        issues.add(QualityIssue.builder()
                                .category("validity")
                                .level("warning")
                                .tableName(tableName)
                                .fieldName(colName)
                                .message(String.format("%s.%s 值 '%s' 格式不合法", tableName, colName, val))
                                .suggestion("检查生成器配置，使用正确的格式生成器")
                                .build());
                        break; // 每个字段只报一次
                    }
                }
            }
        }

        return totalChecked == 0 ? 100.0 : Math.round(validCount * 10000.0 / totalChecked) / 100.0;
    }

    /**
     * 5. 隐私安全 — 检测敏感字段是否已完成脱敏
     */
    private double evaluatePrivacy(
            LinkedHashMap<String, List<Map<String, Object>>> tableData,
            CachedSchemaResponse schema,
            List<QualityIssue> issues) {

        long totalSensitiveCells = 0;
        long maskedCells = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry : tableData.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();

            if (rows.isEmpty()) continue;

            // 用 SensitiveFieldDetector 识别敏感字段（统一入口）
            CachedTableInfo tableInfo = findTable(schema, tableName);
            Set<String> sensitiveCols = new HashSet<>();
            if (tableInfo != null) {
                // 将 CachedColumnInfo 转为 SchemaColumn 以复用统一检测器
                List<com.platform.entity.schema.SchemaColumn> columns =
                        tableInfo.getColumns().stream().map(col -> {
                            com.platform.entity.schema.SchemaColumn sc =
                                    new com.platform.entity.schema.SchemaColumn();
                            sc.setColumnName(col.getName());
                            sc.setDataType(col.getType());
                            sc.setColumnComment(col.getComment());
                            return sc;
                        }).collect(Collectors.toList());

                sensitiveCols = sensitiveFieldDetector.detect(columns).stream()
                        .map(com.platform.dto.SensitiveField::getColumnName)
                        .collect(Collectors.toSet());
            }

            // 检查敏感字段的值是否已完成脱敏
            for (Map<String, Object> row : rows) {
                for (String colName : sensitiveCols) {
                    Object val = row.get(colName);
                    if (val == null) continue;

                    totalSensitiveCells++;
                    if (isMasked(colName, val.toString())) {
                        maskedCells++;
                    }
                }
            }

            // 报告未脱敏的敏感字段
            if (!sensitiveCols.isEmpty()) {
                long unmaskedCount = totalSensitiveCells - maskedCells;
                if (unmaskedCount > 0) {
                    issues.add(QualityIssue.builder()
                            .category("privacy")
                            .level("warning")
                            .tableName(tableName)
                            .fieldName(String.join(", ", sensitiveCols))
                            .message(String.format("%s 表有 %d 个敏感字段值未脱敏", tableName, unmaskedCount))
                            .suggestion("调用 POST /api/privacy/process-auto 对数据执行自动脱敏处理")
                            .build());
                }
            }
        }

        return totalSensitiveCells == 0 ? 100.0 : Math.round(maskedCells * 10000.0 / totalSensitiveCells) / 100.0;
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据字段名推断应使用的格式校验正则
     */
    private Pattern inferPattern(String columnName) {
        if (columnName == null) return null;
        String lower = columnName.toLowerCase();

        if (lower.contains("email") || lower.contains("mail")) {
            return EMAIL_PATTERN;
        }
        if (lower.contains("phone") || lower.contains("mobile") || lower.contains("tel")) {
            return PHONE_PATTERN;
        }
        if (lower.contains("idcard") || lower.contains("id_card") || lower.contains("card_no")
                || lower.equals("id_number")) {
            return ID_CARD_PATTERN;
        }
        if (lower.contains("date") || lower.contains("time") || lower.contains("birth")
                || lower.contains("created") || lower.contains("updated")) {
            return DATE_PATTERN;
        }
        if (lower.contains("bank") || lower.equals("card")) {
            return BANK_CARD_PATTERN;
        }
        return null;
    }

    /**
     * 判断值是否已被脱敏（含掩码字符 *** 或已 hash）
     */
    private boolean isMasked(String columnName, String value) {
        if (value == null || value.isEmpty()) return true;

        String lower = columnName.toLowerCase();

        // 检查是否含掩码星号
        if (value.contains("***") || value.contains("****")) return true;

        // 检查 SHA-256 hash 格式
        if (value.matches("^[a-f0-9]{64}$")) return true;

        // 检查邮箱脱敏：user***@domain.com
        if (lower.contains("email") && value.contains("***@")) return true;

        // 检查手机号脱敏：138****5678
        if ((lower.contains("phone") || lower.contains("mobile")) && value.matches("^1[3-9]\\d\\*{3,4}\\d{4}$")) return true;

        // 检查身份证脱敏
        if ((lower.contains("idcard") || lower.contains("id_card")) && value.contains("*")) return true;

        // 检查银行卡脱敏
        if (lower.contains("bank") && value.contains("*")) return true;

        // 检查姓名脱敏：张* / 张**
        if ((lower.contains("name") || lower.equals("username")) && value.matches("^[\\u4e00-\\u9fa5][\\*]+$")) {
            return true;
        }

        // 检查地址脱敏
        if (lower.contains("address") && value.contains("***")) return true;

        return false;
    }

    /**
     * 计算等级
     */
    private String calculateGrade(double score) {
        if (score >= 90) return "优秀";
        if (score >= 80) return "良好";
        if (score >= 60) return "合格";
        return "不合格";
    }

    /**
     * 查找 Schema 中的表信息
     */
    private CachedTableInfo findTable(CachedSchemaResponse schema, String tableName) {
        if (schema == null || schema.getTables() == null) return null;
        return schema.getTables().stream()
                .filter(t -> t.getTableName().equals(tableName))
                .findFirst().orElse(null);
    }

    /**
     * 加载 Schema 缓存（不存在时跳过）
     */
    private CachedSchemaResponse loadSchema(Long datasourceId) {
        try {
            if (schemaCacheService.hasCache(datasourceId)) {
                return schemaCacheService.getSchema(datasourceId);
            }
        } catch (Exception e) {
            log.warn("Schema 缓存加载失败: datasourceId={}, error={}", datasourceId, e.getMessage());
        }
        // 返回空 Schema（部分指标降级处理）
        return CachedSchemaResponse.builder().tables(Collections.emptyList()).build();
    }

    /**
     * 保存质量报告到数据库
     */
    private void saveReport(Long taskId, double totalScore, String grade,
                            double completeness, double uniqueness, double consistency,
                            double validity, double privacy, List<QualityIssue> issues) {
        // 删除旧报告（如果存在）
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

    /**
     * 解析 detailJson → QualityIssue 列表
     */
    @SuppressWarnings("unchecked")
    private List<QualityIssue> parseDetails(String detailJson) {
        if (detailJson == null || detailJson.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(detailJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, QualityIssue.class));
        } catch (JsonProcessingException e) {
            log.error("问题列表 JSON 解析失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建空报告（无数据时）
     */
    private QualityReportResponse buildEmptyReport(Long taskId) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("completeness", 0.0);
        metrics.put("uniqueness", 0.0);
        metrics.put("consistency", 0.0);
        metrics.put("validity", 0.0);
        metrics.put("privacy", 0.0);

        return QualityReportResponse.builder()
                .taskId(taskId)
                .totalScore(0.0)
                .grade("不合格")
                .metrics(metrics)
                .details(Collections.emptyList())
                .build();
    }
}
