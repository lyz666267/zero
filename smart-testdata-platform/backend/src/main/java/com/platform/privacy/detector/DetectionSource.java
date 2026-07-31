package com.platform.privacy.detector;

/**
 * 检测来源枚举
 *
 * <p>标识敏感字段是由哪个检测器发现的，用于
 * {@link CompositeSensitiveDetector} 的优先级融合和审计追溯。</p>
 *
 * <p>优先级排序：REGEX &gt; KEYWORD &gt; LLM（正则基于实际数据值，置信度最高）</p>
 */
public enum DetectionSource {

    /** 关键词匹配 — 基于字段名关键词 */
    KEYWORD,

    /** 正则表达式 — 基于实际数据值的格式匹配 */
    REGEX,

    /** LLM 语义判断 — 基于大模型的语义分析 */
    LLM
}
