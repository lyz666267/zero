package com.platform.privacy.service;

import com.platform.dto.PrivacyProcessRequest.SensitiveFieldInfo;
import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldType;
import com.platform.privacy.detector.CompositeSensitiveDetector;
import com.platform.privacy.detector.DetectionContext;
import com.platform.privacy.detector.DetectionResult;
import com.platform.privacy.executor.MaskExecutor;
import com.platform.privacy.mask.MaskRuleRegistry;
import com.platform.privacy.mask.MaskStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 隐私感知数据处理器
 *
 * <h3>职责</h3>
 * <p>将脱敏能力融入测试数据生成流程：
 * 接收已生成的数据行和已识别的敏感字段列表，对敏感字段值执行脱敏转换。</p>
 *
 * <h3>两种使用方式</h3>
 * <ul>
 *   <li><b>手动指定</b> — {@link #process(List, List)}，调用方已通过外部检测器识别敏感字段</li>
 *   <li><b>自动检测</b> — {@link #process(List, List)}（SchemaColumn 参数），内部使用
 *   {@link CompositeSensitiveDetector} 三层融合自动识别</li>
 * </ul>
 *
 * <h3>复用关系</h3>
 * <ul>
 *   <li>{@link MaskRuleRegistry} — 根据 SensitiveFieldType 查询脱敏策略</li>
 *   <li>{@link MaskExecutor} — 根据策略执行值转换</li>
 *   <li>{@link CompositeSensitiveDetector} — 三层融合自动检测（新增）</li>
 * </ul>
 */
@Slf4j
@Component
public class PrivacyAwareDataProcessor {

    private final MaskRuleRegistry maskRuleRegistry;
    private final MaskExecutor maskExecutor;
    private final CompositeSensitiveDetector sensitiveDetector;

    public PrivacyAwareDataProcessor(MaskRuleRegistry maskRuleRegistry,
                                     MaskExecutor maskExecutor,
                                     CompositeSensitiveDetector sensitiveDetector) {
        this.maskRuleRegistry = maskRuleRegistry;
        this.maskExecutor = maskExecutor;
        this.sensitiveDetector = sensitiveDetector;
    }

    /**
     * 对数据行执行脱敏处理
     *
     * @param data            待处理的数据行
     * @param sensitiveFields 已识别的敏感字段列表
     * @return 脱敏后的数据行（新列表，不修改入参）
     */
    public List<Map<String, Object>> process(
            List<Map<String, Object>> data,
            List<SensitiveFieldInfo> sensitiveFields) {

        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建 columnName → MaskStrategy 的快速查找映射
        Map<String, MaskStrategy> strategyMap = buildStrategyMap(sensitiveFields);

        if (strategyMap.isEmpty()) {
            log.debug("无敏感字段需要脱敏，返回原始数据");
            return data;
        }

        List<Map<String, Object>> result = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            result.add(maskRow(row, strategyMap));
        }

        log.info("脱敏处理完成: {} 行数据, {} 个敏感字段", data.size(), strategyMap.size());
        return result;
    }

    /**
     * 对单行数据执行脱敏
     */
    private Map<String, Object> maskRow(Map<String, Object> row, Map<String, MaskStrategy> strategyMap) {
        Map<String, Object> maskedRow = new LinkedHashMap<>(row);
        for (Map.Entry<String, MaskStrategy> entry : strategyMap.entrySet()) {
            String columnName = entry.getKey();
            MaskStrategy strategy = entry.getValue();

            Object rawValue = maskedRow.get(columnName);
            if (rawValue != null) {
                String masked = maskExecutor.mask(rawValue.toString(), strategy);
                maskedRow.put(columnName, masked);
            }
            // null 值保持 null 不变
        }
        return maskedRow;
    }

    /**
     * 自动检测并脱敏处理
     *
     * <p>使用 {@link CompositeSensitiveDetector} 对列进行三层融合检测，
     * 自动识别敏感字段并执行脱敏。无需调用方预先做敏感字段识别。</p>
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>columns + data → DetectionContext</li>
     *   <li>CompositeSensitiveDetector.detect() → List&lt;DetectionResult&gt;</li>
     *   <li>DetectionResult → SensitiveFieldInfo 转换</li>
     *   <li>委托 {@link #process(List, List)} 执行脱敏</li>
     * </ol>
     *
     * @param data    待处理的数据行
     * @param columns 字段元数据列表
     * @return 脱敏后的数据行（新列表，不修改入参）
     */
    public List<Map<String, Object>> processAuto(List<Map<String, Object>> data,
                                                  List<SchemaColumn> columns) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        if (columns == null || columns.isEmpty()) {
            log.debug("无列信息，返回原始数据（不执行脱敏）");
            return data;
        }

        // 构建检测上下文
        DetectionContext ctx = new DetectionContext(columns, data);

        // 三层融合检测
        List<DetectionResult> results = sensitiveDetector.detect(ctx);

        if (results.isEmpty()) {
            log.debug("未检测到敏感字段，返回原始数据");
            return data;
        }

        // 转换为 SensitiveFieldInfo
        List<SensitiveFieldInfo> fieldInfos = results.stream()
                .filter(r -> r.getType() != SensitiveFieldType.UNKNOWN)
                .map(r -> new SensitiveFieldInfo(r.getColumnName(), r.getType().name()))
                .collect(Collectors.toList());

        log.info("自动检测完成: {} 列 → {} 个敏感字段, 即将执行脱敏",
                columns.size(), fieldInfos.size());

        // 委托给已有方法执行脱敏
        return process(data, fieldInfos);
    }

    /**
     * 构建 columnName → MaskStrategy 的映射
     *
     * <p>跳过 UNKNOWN 类型和无法解析的类型名。</p>
     */
    private Map<String, MaskStrategy> buildStrategyMap(List<SensitiveFieldInfo> sensitiveFields) {
        if (sensitiveFields == null || sensitiveFields.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, MaskStrategy> map = new LinkedHashMap<>();
        for (SensitiveFieldInfo info : sensitiveFields) {
            if (info.getColumnName() == null || info.getType() == null) {
                continue;
            }

            // 解析 SensitiveFieldType 枚举
            SensitiveFieldType fieldType;
            try {
                fieldType = SensitiveFieldType.valueOf(info.getType());
            } catch (IllegalArgumentException e) {
                log.warn("无法解析的敏感类型: {} (字段: {}), 跳过", info.getType(), info.getColumnName());
                continue;
            }

            // UNKNOWN 不处理
            if (fieldType == SensitiveFieldType.UNKNOWN) {
                continue;
            }

            // 从注册表查询策略
            Optional<MaskStrategy> strategyOpt = maskRuleRegistry.getStrategy(fieldType);
            if (strategyOpt.isPresent()) {
                map.put(info.getColumnName(), strategyOpt.get());
            } else {
                log.debug("敏感类型 {} 无对应脱敏策略, 跳过字段 {}", fieldType, info.getColumnName());
            }
        }

        return map;
    }
}
