package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 隐私脱敏处理请求
 *
 * <p>包含待脱敏的数据行和已识别的敏感字段列表。
 * 敏感字段通常由 {@link com.platform.privacy.SensitiveFieldDetector} 前置识别。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyProcessRequest {

    /** 待处理的数据行，每行为 Map&lt;列名, 值&gt; */
    private List<Map<String, Object>> data;

    /** 已识别的敏感字段列表 */
    private List<SensitiveFieldInfo> sensitiveFields;

    /**
     * 敏感字段简要信息（仅 columnName + type）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensitiveFieldInfo {
        /** 列名 */
        private String columnName;
        /** 敏感类型枚举名（如 PHONE / EMAIL） */
        private String type;
    }
}
