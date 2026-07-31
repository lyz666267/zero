package com.platform.privacy.detector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 组合敏感字段检测器 — 三层融合编排
 *
 * <h3>融合策略（优先级从高到低）</h3>
 * <ol>
 *   <li><b>Regex</b> — 基于实际数据值的正则匹配，置信度最高</li>
 *   <li><b>Keyword</b> — 基于字段名关键词匹配，中等置信度</li>
 *   <li><b>LLM</b> — 基于大模型语义判断，最低优先级</li>
 * </ol>
 *
 * <h3>融合规则</h3>
 * <p>对每个字段，取优先级最高的检测器结果。使用
 * {@code LinkedHashMap&lt;columnName, DetectionResult&gt;} 实现：
 * LLM 先写入 → Keyword 覆盖 → Regex 最终覆盖。</p>
 *
 * <h3>容错</h3>
 * <p>任一层失败不影响其他层。LLM 不可用时自动降级为空列表，
 * 仅依赖 Keyword + Regex 完成检测。</p>
 *
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │  CompositeSensitiveDetector              │
 * │                                          │
 * │  1. RegexSensitiveDetector  ─── 最高优先级 │
 * │  2. KeywordSensitiveDetector ─── 中间优先级 │
 * │  3. LLMSensitiveDetector    ─── 最低优先级 │
 * │                                          │
 * │  同一字段只保留最高优先级的检测结果          │
 * └──────────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class CompositeSensitiveDetector implements SensitiveDetector {

    private final RegexSensitiveDetector regexDetector;
    private final KeywordSensitiveDetector keywordDetector;
    private final LLMSensitiveDetector llmDetector;

    @Override
    public List<DetectionResult> detect(DetectionContext context) {
        if (context == null || context.getColumns() == null || context.getColumns().isEmpty()) {
            log.debug("CompositeSensitiveDetector: 无列信息，返回空结果");
            return Collections.emptyList();
        }

        int totalColumns = context.columnCount();

        // 使用 LinkedHashMap 维护插入顺序，后写入的同名列覆盖前一个
        Map<String, DetectionResult> fused = new LinkedHashMap<>();

        // ── 第 3 层：LLM（最低优先级，先写入）──
        try {
            List<DetectionResult> llmResults = llmDetector.detect(context);
            for (DetectionResult r : llmResults) {
                fused.put(r.getColumnName(), r);
            }
            log.debug("Composite: LLM 检测到 {} 个敏感字段", llmResults.size());
        } catch (Exception e) {
            log.warn("Composite: LLM 检测失败，跳过: {}", e.getMessage());
        }

        // ── 第 1 层（排序第二）：Keyword（中优先级，覆盖 LLM）──
        try {
            List<DetectionResult> keywordResults = keywordDetector.detect(context);
            for (DetectionResult r : keywordResults) {
                fused.put(r.getColumnName(), r); // 覆盖 LLM 同名列
            }
            log.debug("Composite: Keyword 检测到 {} 个敏感字段", keywordResults.size());
        } catch (Exception e) {
            log.warn("Composite: Keyword 检测失败，跳过: {}", e.getMessage());
        }

        // ── 第 1 层（排序第三）：Regex（最高优先级，覆盖 Keyword + LLM）──
        try {
            List<DetectionResult> regexResults = regexDetector.detect(context);
            for (DetectionResult r : regexResults) {
                fused.put(r.getColumnName(), r); // 覆盖 Keyword + LLM 同名列
            }
            log.debug("Composite: Regex 检测到 {} 个敏感字段", regexResults.size());
        } catch (Exception e) {
            log.warn("Composite: Regex 检测失败，跳过: {}", e.getMessage());
        }

        List<DetectionResult> results = new ArrayList<>(fused.values());

        log.info("CompositeSensitiveDetector: {} 列 → 融合结果 {} 个敏感字段",
                totalColumns, results.size());

        return results;
    }
}
