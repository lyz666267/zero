package com.platform.quality;

import com.platform.dto.CachedSchemaResponse.CachedTableInfo;
import com.platform.dto.QualityReportResponse.QualityIssue;
import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(5)
@RequiredArgsConstructor
public class PrivacyMetric implements QualityMetric {

    private final SensitiveFieldDetector sensitiveFieldDetector;

    @Override
    public QualityMetricResult evaluate(List<Map<String, Object>> data, SchemaInfo schemaInfo) {
        List<QualityIssue> issues = new ArrayList<>();
        long totalSensitiveCells = 0;
        long maskedCells = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry
                : schemaInfo.tableData().entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) {
                continue;
            }

            CachedTableInfo tableInfo = schemaInfo.findTable(tableName);
            Set<String> sensitiveCols = new HashSet<>();
            if (tableInfo != null) {
                List<SchemaColumn> columns = tableInfo.getColumns().stream().map(col -> {
                    SchemaColumn sc = new SchemaColumn();
                    sc.setColumnName(col.getName());
                    sc.setDataType(col.getType());
                    sc.setColumnComment(col.getComment());
                    return sc;
                }).collect(Collectors.toList());

                sensitiveCols = sensitiveFieldDetector.detect(columns).stream()
                        .map(com.platform.dto.SensitiveField::getColumnName)
                        .collect(Collectors.toSet());
            }

            for (Map<String, Object> row : rows) {
                for (String colName : sensitiveCols) {
                    Object val = row.get(colName);
                    if (val == null) {
                        continue;
                    }
                    totalSensitiveCells++;
                    if (QualityMetricSupport.isMasked(colName, val.toString())) {
                        maskedCells++;
                    }
                }
            }

            if (!sensitiveCols.isEmpty()) {
                long unmaskedCount = totalSensitiveCells - maskedCells;
                if (unmaskedCount > 0) {
                    issues.add(QualityIssue.builder()
                            .category("privacy")
                            .level("warning")
                            .tableName(tableName)
                            .fieldName(String.join(", ", sensitiveCols))
                            .message(String.format("%s 表有 %d 个敏感字段值未脱敏",
                                    tableName, unmaskedCount))
                            .suggestion("调用 POST /api/privacy/process-auto 对数据执行自动脱敏处理")
                            .build());
                }
            }
        }

        double score = totalSensitiveCells == 0
                ? 100.0
                : Math.round(maskedCells * 10000.0 / totalSensitiveCells) / 100.0;
        return new QualityMetricResult("privacy", score, issues);
    }
}
