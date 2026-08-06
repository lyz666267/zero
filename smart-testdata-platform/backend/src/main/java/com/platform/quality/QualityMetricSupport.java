package com.platform.quality;

import com.platform.dto.CachedSchemaResponse.CachedColumnInfo;
import com.platform.dto.CachedSchemaResponse.CachedTableInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class QualityMetricSupport {

    static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    static final Pattern PHONE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");
    static final Pattern ID_CARD_PATTERN =
            Pattern.compile("^\\d{17}[\\dXx]$");
    static final Pattern DATE_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}");
    static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("^\\d{16,19}$");

    private QualityMetricSupport() {
    }

    static List<String> resolvePrimaryKeyColumns(CachedTableInfo tableInfo,
                                                 List<Map<String, Object>> rows) {
        if (tableInfo != null && tableInfo.getColumns() != null) {
            List<String> pks = tableInfo.getColumns().stream()
                    .filter(col -> Boolean.TRUE.equals(col.getPrimaryKey()))
                    .map(CachedColumnInfo::getName)
                    .collect(Collectors.toList());
            if (!pks.isEmpty()) {
                return pks;
            }
        }
        return Collections.emptyList();
    }

    static Pattern inferPattern(String columnName) {
        if (columnName == null) {
            return null;
        }
        String lower = columnName.toLowerCase();
        if (lower.contains("email") || lower.contains("mail")) {
            return EMAIL_PATTERN;
        }
        if (lower.contains("phone") || lower.contains("mobile") || lower.contains("tel")) {
            return PHONE_PATTERN;
        }
        if (lower.contains("idcard") || lower.contains("id_card") || lower.contains("card_no")
                || lower.equals("id_number")) {
            return ID_CARD_PATTERN;
        }
        if (lower.contains("date") || lower.contains("time") || lower.contains("birth")
                || lower.contains("created") || lower.contains("updated")) {
            return DATE_PATTERN;
        }
        if (lower.contains("bank") || lower.equals("card")) {
            return BANK_CARD_PATTERN;
        }
        return null;
    }

    static boolean isMasked(String columnName, String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        String lower = columnName.toLowerCase();
        if (value.contains("***") || value.contains("****")) {
            return true;
        }
        if (value.matches("^[a-f0-9]{64}$")) {
            return true;
        }
        if (lower.contains("email") && value.contains("***@")) {
            return true;
        }
        if ((lower.contains("phone") || lower.contains("mobile"))
                && value.matches("^1[3-9]\\d\\*{3,4}\\d{4}$")) {
            return true;
        }
        if ((lower.contains("idcard") || lower.contains("id_card")) && value.contains("*")) {
            return true;
        }
        if (lower.contains("bank") && value.contains("*")) {
            return true;
        }
        if ((lower.contains("name") || lower.equals("username"))
                && value.matches("^[\\u4e00-\\u9fa5][\\*]+$")) {
            return true;
        }
        return lower.contains("address") && value.contains("***");
    }
}
