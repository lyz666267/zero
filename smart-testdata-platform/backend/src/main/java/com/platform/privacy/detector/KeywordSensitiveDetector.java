package com.platform.privacy.detector;

import com.platform.privacy.SensitiveFieldDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 关键词敏感字段检测器（第 1 层）
 *
 * <h3>职责</h3>
 * <p>基于字段名关键词匹配识别敏感字段。内部委托给现有的
 * {@link SensitiveFieldDetector}，将其输出适配为 {@link DetectionResult}。</p>
 *
 * <h3>检测依据</h3>
 * <p>仅使用字段名进行匹配，不依赖样本数据值。
 * 识别规则与关键词列表由 {@link SensitiveFieldDetector} 维护。</p>
 *
 * <h3>置信度</h3>
 * <ul>
 *   <li>0.95 — 字段名与关键词完全相同（如 "phone" → PHONE）</li>
 *   <li>0.80 — 字段名包含关键词（如 "user_phone" → PHONE）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordSensitiveDetector implements SensitiveDetector {

    private final SensitiveFieldDetector delegate;

    @Override
    public List<DetectionResult> detect(DetectionContext context) {
        if (context == null || context.getColumns() == null || context.getColumns().isEmpty()) {
            log.debug("KeywordSensitiveDetector: 无列信息，返回空结果");
            return Collections.emptyList();
        }

        List<DetectionResult> results = delegate.detect(context.getColumns())
                .stream()
                .map(DetectionResult::fromKeyword)
                .collect(Collectors.toList());

        log.debug("KeywordSensitiveDetector: 检测完成, {} 列 → {} 个敏感字段",
                context.columnCount(), results.size());
        return results;
    }
}
