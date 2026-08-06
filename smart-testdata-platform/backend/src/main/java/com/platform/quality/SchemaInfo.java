package com.platform.quality;

import com.platform.dto.CachedSchemaResponse;
import com.platform.dto.CachedSchemaResponse.CachedTableInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SchemaInfo(
        CachedSchemaResponse schema,
        LinkedHashMap<String, List<Map<String, Object>>> tableData
) {

    public List<Map<String, Object>> allRows() {
        return tableData.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    public CachedTableInfo findTable(String tableName) {
        if (schema == null || schema.getTables() == null) {
            return null;
        }
        return schema.getTables().stream()
                .filter(t -> t.getTableName().equals(tableName))
                .findFirst()
                .orElse(null);
    }
}
