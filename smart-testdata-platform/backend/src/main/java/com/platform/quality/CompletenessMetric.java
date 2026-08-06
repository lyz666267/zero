package com.platform.quality;

import com.platform.dto.CachedSchemaResponse.CachedColumnInfo;
import com.platform.dto.CachedSchemaResponse.CachedTableInfo;
import com.platform.dto.QualityReportResponse.QualityIssue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class CompletenessMetric implements QualityMetric {

    @Override
    public QualityMetricResult evaluate(List<Map<String, Object>> data, SchemaInfo schemaInfo) {
        List<QualityIssue> issues = new ArrayList<>();
        long totalCells = 0;
        long nonNullCells = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry
                : schemaInfo.tableData().entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) {
                continue;
            }

            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    totalCells++;
                    if (cell.getValue() != null && !cell.getValue().toString().isEmpty()) {
                        nonNullCells++;
                    }
                }
            }

            CachedTableInfo tableInfo = schemaInfo.findTable(tableName);
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
                                    .message(String.format("%s.%s 必填字段有 %d 条空值",
                                            tableName, col.getName(), missing))
                                    .suggestion("检查生成器配置，确保必填字段生成非空值")
                                    .build());
                        }
                    }
                }
            }
        }

        double score = totalCells == 0
                ? 100.0
                : Math.round(nonNullCells * 10000.0 / totalCells) / 100.0;
        return new QualityMetricResult("completeness", score, issues);
    }
}
