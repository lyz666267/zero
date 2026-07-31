package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 自动脱敏处理请求（Phase 8.1 新增）
 *
 * <p>包含待脱敏的数据行和字段元数据。服务端使用
 * {@link com.platform.privacy.detector.CompositeSensitiveDetector}
 * 自动完成三层融合检测（Regex → Keyword → LLM）。</p>
 *
 * <p>与 {@link PrivacyProcessRequest} 的区别：</p>
 * <ul>
 *   <li>PrivacyProcessRequest: 调用方已识别敏感字段，直接传入 sensitiveFields</li>
 *   <li>PrivacyProcessAutoRequest: 调用方只传 column 元数据，服务端自动检测</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyProcessAutoRequest {

    /** 待处理的数据行 */
    private List<Map<String, Object>> data;

    /** 字段元数据列表（仅需 columnName, columnType, columnComment, dataType） */
    private List<ColumnInfo> columns;

    /**
     * 字段元数据简要信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        /** 列名 */
        private String columnName;
        /** 完整列类型（如 varchar(64)） */
        private String columnType;
        /** 列注释 */
        private String columnComment;
        /** 数据类型（如 varchar, int） */
        private String dataType;
    }
}
