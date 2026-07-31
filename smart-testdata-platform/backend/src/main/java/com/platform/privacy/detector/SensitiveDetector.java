package com.platform.privacy.detector;

import java.util.List;

/**
 * 敏感字段检测器接口
 *
 * <h3>职责</h3>
 * <p>定义统一的敏感字段检测契约。所有检测器（关键词、正则、LLM、组合）均实现此接口。</p>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link KeywordSensitiveDetector} — 字段名关键词匹配（第 1 层）</li>
 *   <li>{@link RegexSensitiveDetector} — 数据值正则匹配（第 2 层）</li>
 *   <li>{@link LLMSensitiveDetector} — LLM 语义判断（第 3 层）</li>
 *   <li>{@link CompositeSensitiveDetector} — 三层融合编排</li>
 * </ul>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>未检测到的字段不出现在结果列表中</li>
 *   <li>空输入返回空列表（不抛异常）</li>
 *   <li>实现类必须是线程安全的</li>
 * </ul>
 */
public interface SensitiveDetector {

    /**
     * 检测上下文中的敏感字段
     *
     * @param context 包含字段元数据和样本数据值的检测上下文
     * @return 检测结果列表，未检出时返回空列表
     */
    List<DetectionResult> detect(DetectionContext context);
}
