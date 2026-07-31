package com.platform.privacy;

import com.platform.dto.SensitiveField;
import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.detector.DetectionContext;
import com.platform.privacy.detector.DetectionResult;
import com.platform.privacy.detector.DetectionSource;
import com.platform.privacy.detector.SensitiveDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 敏感字段识别器 — 统一的敏感字段检测入口
 *
 * <h3>职责</h3>
 * <p>作为项目中所有敏感字段检测的<b>唯一入口</b>。基于数据库字段名关键词匹配
 * 识别可能包含个人隐私数据的字段。实现 {@link SensitiveDetector} 接口，
 * 支持 {@link DetectionContext} 和传统的 {@code List<SchemaColumn>} 两种输入。</p>
 *
 * <h3>识别规则</h3>
 * <table>
 *   <tr><th>关键词</th><th>敏感类型</th></tr>
 *   <tr><td>phone / mobile / tel</td><td>{@link SensitiveFieldType#PHONE}</td></tr>
 *   <tr><td>email / mail</td><td>{@link SensitiveFieldType#EMAIL}</td></tr>
 *   <tr><td>idcard / id_card / card_no</td><td>{@link SensitiveFieldType#ID_CARD}</td></tr>
 *   <tr><td>name / username</td><td>{@link SensitiveFieldType#NAME}</td></tr>
 *   <tr><td>address / addr / location</td><td>{@link SensitiveFieldType#ADDRESS}</td></tr>
 *   <tr><td>bank / card</td><td>{@link SensitiveFieldType#BANK_CARD}</td></tr>
 * </table>
 *
 * <h3>置信度</h3>
 * <ul>
 *   <li><b>0.95</b> — 精确匹配：字段名与关键词完全相同（如 "phone" → PHONE）</li>
 *   <li><b>0.80</b> — 包含匹配：字段名包含关键词（如 "user_phone" → PHONE）</li>
 *   <li>未匹配的字段不返回</li>
 * </ul>
 */
@Slf4j
@Component
public class SensitiveFieldDetector implements SensitiveDetector {

    /**
     * 识别规则列表，按优先级排序。
     * 当多个类型以相同置信度匹配同一字段时，靠前的规则优先。
     */
    private static final LinkedHashMap<SensitiveFieldType, List<String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put(SensitiveFieldType.PHONE,    List.of("phone", "mobile", "tel"));
        RULES.put(SensitiveFieldType.EMAIL,    List.of("email", "mail"));
        RULES.put(SensitiveFieldType.ID_CARD,  List.of("idcard", "id_card", "card_no"));
        RULES.put(SensitiveFieldType.NAME,     List.of("name", "username"));
        RULES.put(SensitiveFieldType.ADDRESS,  List.of("address", "addr", "location"));
        RULES.put(SensitiveFieldType.BANK_CARD, List.of("bank", "card"));
    }

    private static final double CONFIDENCE_EXACT = 0.95;
    private static final double CONFIDENCE_CONTAINS = 0.80;

    /**
     * 识别字段列表中的敏感字段
     *
     * @param columns 数据库字段列表
     * @return 识别出的敏感字段列表（不包含未匹配的字段）
     */
    public List<SensitiveField> detect(List<SchemaColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return Collections.emptyList();
        }

        List<SensitiveField> results = new ArrayList<>();
        for (SchemaColumn column : columns) {
            String columnName = column.getColumnName();
            if (columnName == null || columnName.isBlank()) {
                continue;
            }

            SensitiveField detected = detectSingle(columnName);
            if (detected != null) {
                results.add(detected);
                log.debug("敏感字段识别: {} → {} (confidence={})",
                        columnName, detected.getType(), detected.getConfidence());
            }
        }

        log.info("敏感字段识别完成: 输入 {} 列, 识别出 {} 个敏感字段",
                columns.size(), results.size());
        return results;
    }

    // ==================== SensitiveDetector 接口实现 ====================

    /**
     * 统一检测入口 — 从 {@link DetectionContext} 中提取列信息并执行关键词识别。
     *
     * <p>仅使用列名进行关键词匹配（不依赖样本数据）。返回
     * {@link DetectionResult} 列表，标注来源为 {@link DetectionSource#KEYWORD}。</p>
     *
     * @param context 包含列元数据和样本数据的检测上下文
     * @return 检测结果列表，未检出时返回空列表
     */
    @Override
    public List<DetectionResult> detect(DetectionContext context) {
        if (context == null || context.getColumns() == null || context.getColumns().isEmpty()) {
            return Collections.emptyList();
        }
        return detect(context.getColumns()).stream()
                .map(DetectionResult::fromKeyword)
                .collect(Collectors.toList());
    }

    /**
     * 识别单个字段名
     *
     * @param columnName 原始字段名
     * @return 识别结果，未匹配返回 {@code null}
     */
    SensitiveField detectSingle(String columnName) {
        String normalized = columnName.toLowerCase();

        SensitiveFieldType bestType = null;
        double bestConfidence = 0;

        for (Map.Entry<SensitiveFieldType, List<String>> entry : RULES.entrySet()) {
            SensitiveFieldType type = entry.getKey();
            for (String keyword : entry.getValue()) {
                double confidence = matchConfidence(normalized, keyword);
                if (confidence > bestConfidence) {
                    bestConfidence = confidence;
                    bestType = type;
                }
            }
        }

        if (bestType == null || bestConfidence == 0) {
            return null; // 未知字段不返回
        }

        return new SensitiveField(columnName, bestType, bestConfidence);
    }

    /**
     * 计算字段名与关键词的匹配置信度
     *
     * @param normalizedColumn 已转小写的字段名
     * @param keyword          已转小写的关键词
     * @return 0.95（精确匹配）、0.80（包含匹配）或 0（不匹配）
     */
    private double matchConfidence(String normalizedColumn, String keyword) {
        if (normalizedColumn.equals(keyword)) {
            return CONFIDENCE_EXACT;
        }
        if (normalizedColumn.contains(keyword)) {
            return CONFIDENCE_CONTAINS;
        }
        return 0;
    }
}
