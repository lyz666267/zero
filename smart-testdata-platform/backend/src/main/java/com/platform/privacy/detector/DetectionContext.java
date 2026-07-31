package com.platform.privacy.detector;

import com.platform.entity.schema.SchemaColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检测上下文 — 统一封装所有检测器需要的输入信息
 *
 * <p>包含字段元数据（列名、类型、注释）和样本数据值（用于正则 + LLM 检测）。
 * 不同的检测器按需使用其中的信息：</p>
 * <ul>
 *   <li>KeywordDetector — 只使用 columnName</li>
 *   <li>RegexDetector — 使用 columnName + dataType + sampleValues</li>
 *   <li>LLMDetector — 使用 columnName + columnComment + sampleValues</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectionContext {

    /** 字段元数据列表 */
    private List<SchemaColumn> columns;

    /** 样本数据行（每行为 Map&lt;列名, 值&gt;），可为空 */
    private List<Map<String, Object>> sampleData;

    /**
     * 从样本数据中提取指定列的所有非空值
     *
     * @param columnName 列名
     * @return 该列的所有字符串值（非 null），无样本数据时返回空列表
     */
    public List<String> getValuesForColumn(String columnName) {
        if (sampleData == null || sampleData.isEmpty()) {
            return Collections.emptyList();
        }
        return sampleData.stream()
                .map(row -> row.get(columnName))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 是否有样本数据
     */
    public boolean hasSampleData() {
        return sampleData != null && !sampleData.isEmpty();
    }

    /**
     * 列数量
     */
    public int columnCount() {
        return columns != null ? columns.size() : 0;
    }
}
