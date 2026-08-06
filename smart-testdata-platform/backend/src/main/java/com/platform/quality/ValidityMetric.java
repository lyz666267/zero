package com.platform.quality;

import com.platform.dto.QualityReportResponse.QualityIssue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Order(4)
public class ValidityMetric implements QualityMetric {

    @Override
    public QualityMetricResult evaluate(List<Map<String, Object>> data, SchemaInfo schemaInfo) {
        List<QualityIssue> issues = new ArrayList<>();
        long totalChecked = 0;
        long validCount = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry
                : schemaInfo.tableData().entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) {
                continue;
            }

            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    String colName = cell.getKey();
                    Object val = cell.getValue();
                    if (val == null) {
                        continue;
                    }
                    Pattern pattern = QualityMetricSupport.inferPattern(colName);
                    if (pattern == null) {
                        continue;
                    }
                    totalChecked++;
                    if (pattern.matcher(val.toString()).matches()) {
                        validCount++;
                    }
                }
            }

            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    String colName = cell.getKey();
                    Object val = cell.getValue();
                    if (val == null) {
                        continue;
                    }
                    Pattern pattern = QualityMetricSupport.inferPattern(colName);
                    if (pattern == null) {
                        continue;
                    }
                    if (!pattern.matcher(val.toString()).matches()) {
                        issues.add(QualityIssue.builder()
                                .category("validity")
                                .level("warning")
                                .tableName(tableName)
                                .fieldName(colName)
                                .message(String.format("%s.%s 值 '%s' 格式不合法",
                                        tableName, colName, val))
                                .suggestion("检查生成器配置，使用正确的格式生成器")
                                .build());
                        break;
                    }
                }
            }
        }

        double score = totalChecked == 0
                ? 100.0
                : Math.round(validCount * 10000.0 / totalChecked) / 100.0;
        return new QualityMetricResult("validity", score, issues);
    }
}
