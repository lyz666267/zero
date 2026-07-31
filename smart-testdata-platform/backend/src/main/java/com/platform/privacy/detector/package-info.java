/**
 * 敏感字段检测器包
 *
 * <h3>三层架构</h3>
 * <pre>
 *                    ┌──────────────────────────┐
 *                    │  CompositeSensitiveDetector │
 *                    │     (融合编排 @Primary)     │
 *                    └──────────┬───────────────┘
 *                               │
 *               ┌───────────────┼───────────────┐
 *               │               │               │
 *               ▼               ▼               ▼
 *     RegexSensitiveDetector  KeywordSensitiveDetector  LLMSensitiveDetector
 *       (正则检测 - 最高优先级)    (关键词 - 中优先级)     (LLM - 低优先级)
 *
 *   SensitiveDetector 接口 ── {@link com.platform.privacy.detector.SensitiveDetector}
 *   DetectionContext ── 统一输入上下文
 *   DetectionResult ── 统一输出结果
 *   DetectionSource ── 检测来源标签
 * </pre>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>检测（detect）— 本包负责，识别哪些字段包含敏感信息</li>
 *   <li>脱敏（mask）— {@link com.platform.privacy.executor.MaskExecutor} 负责，对值进行脱敏转换</li>
 *   <li>策略映射 — {@link com.platform.privacy.mask.MaskRuleRegistry} 负责</li>
 * </ul>
 *
 * @see com.platform.privacy.SensitiveFieldDetector 旧版检测器（保留向后兼容）
 */
package com.platform.privacy.detector;
