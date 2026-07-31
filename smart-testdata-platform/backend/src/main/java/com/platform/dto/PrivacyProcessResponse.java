package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 隐私脱敏处理响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyProcessResponse {

    /** 处理是否成功 */
    private boolean success;

    /** 脱敏后的数据行 */
    private List<Map<String, Object>> data;
}
