package com.platform.quality;

import com.platform.dto.CachedSchemaResponse.CachedTableInfo;
import com.platform.dto.QualityReportResponse.QualityIssue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(2)
public class UniquenessMetric implements QualityMetric {

    @Override
    public QualityMetricResult evaluate(List<Map<String, Object>> data, SchemaInfo schemaInfo) {
        List<QualityIssue> issues = new ArrayList<>();
        long totalPkValues = 0;
        long uniquePkValues = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry
                : schemaInfo.tableData().entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) {
                continue;
            }

            CachedTableInfo tableInfo = schemaInfo.findTable(tableName);
            List<String> pkColumns = QualityMetricSupport.resolvePrimaryKeyColumns(tableInfo, rows);
            for (String pkColumn : pkColumns) {
                Set<Object> seen = new HashSet<>();
                int count = 0;
                for (Map<String, Object> row : rows) {
                    Object val = row.get(pkColumn);
                    if (val != null) {
                        count++;
                        seen.add(val);
                    }
                }
                totalPkValues += count;
                uniquePkValues += seen.size();

                long duplicates = count - seen.size();
                if (duplicates > 0) {
                    issues.add(QualityIssue.builder()
                            .category("uniqueness")
                            .level("error")
                            .tableName(tableName)
                            .fieldName(pkColumn)
                            .message(String.format("%s.%s 主键有 %d 个重复值",
                                    tableName, pkColumn, duplicates))
                            .suggestion("检查主键生成策略，使用 UUID 或自增序列避免重复")
                            .build());
                }
            }

            Set<String> rowSignatures = new HashSet<>();
            for (Map<String, Object> row : rows) {
                rowSignatures.add(row.toString());
            }
            long rowDupes = rows.size() - rowSignatures.size();
            if (rowDupes > 0) {
                issues.add(QualityIssue.builder()
                        .category("uniqueness")
                        .level("warning")
                        .tableName(tableName)
                        .fieldName("*")
                        .message(String.format("%s 表有 %d 行完全重复", tableName, rowDupes))
                        .suggestion("增加随机种子变化或增加数据多样性参数")
                        .build());
            }
        }

        double score = totalPkValues == 0
                ? 100.0
                : Math.round(uniquePkValues * 10000.0 / totalPkValues) / 100.0;
        return new QualityMetricResult("uniqueness", score, issues);
    }
}
