package com.platform.quality;

import com.platform.dto.CachedSchemaResponse.CachedColumnInfo;
import com.platform.dto.CachedSchemaResponse.CachedTableInfo;
import com.platform.dto.QualityReportResponse.QualityIssue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(3)
public class ConsistencyMetric implements QualityMetric {

    @Override
    public QualityMetricResult evaluate(List<Map<String, Object>> data, SchemaInfo schemaInfo) {
        List<QualityIssue> issues = new ArrayList<>();
        Map<String, Set<Object>> pkIndex = new HashMap<>();

        for (CachedTableInfo tableInfo : schemaInfo.schema().getTables()) {
            for (CachedColumnInfo col : tableInfo.getColumns()) {
                if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                    List<Map<String, Object>> rows =
                            schemaInfo.tableData().get(tableInfo.getTableName());
                    if (rows != null) {
                        Set<Object> pkValues = rows.stream()
                                .map(row -> row.get(col.getName()))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
                        pkIndex.put(tableInfo.getTableName(), pkValues);
                    }
                }
            }
        }

        long totalFkValues = 0;
        long validFkValues = 0;

        for (CachedTableInfo tableInfo : schemaInfo.schema().getTables()) {
            for (CachedColumnInfo col : tableInfo.getColumns()) {
                if (col.getForeignRefTable() == null || col.getForeignRefTable().isEmpty()) {
                    continue;
                }
                String refTable = col.getForeignRefTable();
                Set<Object> refPkValues = pkIndex.get(refTable);

                List<Map<String, Object>> rows =
                        schemaInfo.tableData().get(tableInfo.getTableName());
                if (rows == null || refPkValues == null || refPkValues.isEmpty()) {
                    continue;
                }

                long invalidCount = 0;
                for (Map<String, Object> row : rows) {
                    Object fkVal = row.get(col.getName());
                    if (fkVal != null) {
                        totalFkValues++;
                        if (refPkValues.contains(fkVal)) {
                            validFkValues++;
                        } else {
                            invalidCount++;
                        }
                    }
                }

                if (invalidCount > 0) {
                    String refColumn =
                            col.getForeignRefColumn() != null ? col.getForeignRefColumn() : "id";
                    issues.add(QualityIssue.builder()
                            .category("consistency")
                            .level("error")
                            .tableName(tableInfo.getTableName())
                            .fieldName(col.getName())
                            .message(String.format("%s.%s 有 %d 个外键值在 %s.%s 中不存在",
                                    tableInfo.getTableName(), col.getName(), invalidCount,
                                    refTable, refColumn))
                            .suggestion(String.format("确保 %s 表数据先于 %s 表生成，且外键引用正确",
                                    refTable, tableInfo.getTableName()))
                            .build());
                }
            }
        }

        double score = totalFkValues == 0
                ? 100.0
                : Math.round(validFkValues * 10000.0 / totalFkValues) / 100.0;
        return new QualityMetricResult("consistency", score, issues);
    }
}
