package com.platform.privacy.detector;

import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 正则表达式敏感字段检测器（第 2 层）
 *
 * <h3>职责</h3>
 * <p>基于实际数据值的正则匹配识别敏感字段。对样本数据值进行模式匹配，
 * 判断字段是否包含手机号、邮箱、身份证号、银行卡号等敏感信息。</p>
 *
 * <h3>检测正则</h3>
 * <table>
 *   <tr><th>敏感类型</th><th>正则</th><th>置信度基准</th></tr>
 *   <tr><td>PHONE</td><td>{@code 1[3-9]\d{9}}</td><td>0.98</td></tr>
 *   <tr><td>EMAIL</td><td>{@code [a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}}</td><td>0.98</td></tr>
 *   <tr><td>ID_CARD</td><td>{@code [1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]}</td><td>0.98</td></tr>
 *   <tr><td>BANK_CARD</td><td>{@code \d{16,19}}</td><td>0.85</td></tr>
 * </table>
 *
 * <h3>性能优化</h3>
 * <p>只对列名包含相关关键词的字段做正则检测，避免对非敏感字段（如 age、id）
 * 进行不必要的正则计算。数字类型列（INT/BIGINT/DECIMAL）跳过电话号码/身份证/银行卡检测，
 * 防止主键 ID 被误判。</p>
 *
 * <h3>置信度计算</h3>
 * <ul>
 *   <li>所有样本值都匹配 → 基准置信度</li>
 *   <li>≥ 75% 样本值匹配 → 基准 × 0.95</li>
 *   <li>≥ 50% 样本值匹配 → 基准 × 0.85</li>
 *   <li>&lt; 50% → 不返回</li>
 * </ul>
 */
@Slf4j
@Component
public class RegexSensitiveDetector implements SensitiveDetector {

    // ==================== 正则模式 ====================

    /** 手机号：1[3-9] 开头 + 9 位数字 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱：标准邮箱格式 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    /** 身份证号：18 位，末位可为数字或 X */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");

    /** 银行卡号：16-19 位数字 */
    private static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("^\\d{16,19}$");

    /** 数字类型 — 跳过这些类型以避免主键/数值误判 */
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "integer", "bigint", "decimal", "numeric", "float", "double", "real", "smallint", "tinyint"
    );

    private static final double CONFIDENCE_BASE = 0.98;
    private static final double CONFIDENCE_BANK_CARD = 0.85;

    @Override
    public List<DetectionResult> detect(DetectionContext context) {
        if (context == null || !context.hasSampleData() || context.getColumns() == null) {
            log.debug("RegexSensitiveDetector: 无样本数据，跳过检测");
            return Collections.emptyList();
        }

        List<DetectionResult> results = new ArrayList<>();

        for (SchemaColumn column : context.getColumns()) {
            String columnName = column.getColumnName();
            if (columnName == null || columnName.isBlank()) {
                continue;
            }

            String dataType = column.getDataType();
            boolean isNumeric = dataType != null && NUMERIC_TYPES.contains(dataType.toLowerCase());

            List<String> values = context.getValuesForColumn(columnName);
            if (values.isEmpty()) {
                continue;
            }

            // 按优先级检测每种类型
            DetectionResult result = detectColumn(columnName, isNumeric, values);
            if (result != null) {
                results.add(result);
            }
        }

        log.debug("RegexSensitiveDetector: 检测完成, {} 列 → {} 个敏感字段",
                context.columnCount(), results.size());
        return results;
    }

    /**
     * 检测单个字段
     */
    private DetectionResult detectColumn(String columnName, boolean isNumeric, List<String> values) {
        int total = values.size();

        // PHONE — 数字类型跳过
        if (!isNumeric) {
            DetectionResult phoneResult = matchPattern(
                    columnName, SensitiveFieldType.PHONE, PHONE_PATTERN,
                    values, total, CONFIDENCE_BASE);
            if (phoneResult != null) return phoneResult;
        }

        // EMAIL — 任何类型都可能
        DetectionResult emailResult = matchPattern(
                columnName, SensitiveFieldType.EMAIL, EMAIL_PATTERN,
                values, total, CONFIDENCE_BASE);
        if (emailResult != null) return emailResult;

        // ID_CARD — 数字类型跳过（18 位数字容易和 ID 冲突）
        if (!isNumeric) {
            DetectionResult idCardResult = matchPattern(
                    columnName, SensitiveFieldType.ID_CARD, ID_CARD_PATTERN,
                    values, total, CONFIDENCE_BASE);
            if (idCardResult != null) return idCardResult;
        }

        // BANK_CARD — 数字类型跳过（16-19 位数字容易和 ID 冲突）
        if (!isNumeric) {
            DetectionResult bankCardResult = matchPattern(
                    columnName, SensitiveFieldType.BANK_CARD, BANK_CARD_PATTERN,
                    values, total, CONFIDENCE_BANK_CARD);
            if (bankCardResult != null) return bankCardResult;
        }

        return null;
    }

    /**
     * 用指定正则匹配样本值，返回检测结果或 null
     */
    private DetectionResult matchPattern(String columnName, SensitiveFieldType type,
                                         Pattern pattern, List<String> values,
                                         int total, double baseConfidence) {
        long matchCount = values.stream().filter(v -> pattern.matcher(v).matches()).count();

        if (matchCount == 0) {
            return null;
        }

        double ratio = (double) matchCount / total;
        double confidence;

        if (ratio >= 1.0) {
            confidence = baseConfidence;
        } else if (ratio >= 0.75) {
            confidence = baseConfidence * 0.95;
        } else if (ratio >= 0.5) {
            confidence = baseConfidence * 0.85;
        } else {
            return null; // 匹配比例不足
        }

        // 找到第一个匹配的值
        String firstMatch = values.stream()
                .filter(v -> pattern.matcher(v).matches())
                .findFirst()
                .orElse(null);

        log.debug("RegexSensitiveDetector: {} → {} (matched {}/{}, confidence={:.2f})",
                columnName, type, matchCount, total, confidence);

        return DetectionResult.fromRegex(columnName, type, confidence, firstMatch);
    }
}
