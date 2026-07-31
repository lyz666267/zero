package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据库脱敏预览 / 执行 响应
 *
 * <p>包含敏感字段分析结果、生成的 UPDATE SQL 预览，以及执行结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseMaskResponse {

    /** 脱敏任务 ID */
    private Long taskId;

    /** 目标表名 */
    private String tableName;

    /** 状态：PREVIEW / EXECUTING / SUCCESS / FAILED */
    private String status;

    /** 检测到的敏感字段列表 */
    private List<MaskFieldInfo> sensitiveFields;

    /** 预览的 UPDATE SQL 语句 */
    private String sqlPreview;

    /** 执行结果消息 */
    private String executeResult;

    /** 影响行数 */
    private Integer affectedRows;

    // ==================== 内嵌类 ====================

    /**
     * 脱敏字段信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaskFieldInfo {
        /** 列名 */
        private String columnName;
        /** 敏感类型（如 PHONE / NAME / EMAIL） */
        private String sensitiveType;
        /** 敏感类型中文标签 */
        private String typeLabel;
        /** 脱敏策略（如 PHONE_MASK / NAME_MASK） */
        private String strategy;
        /** 策略描述 */
        private String strategyDescription;
        /** 示例原始值 */
        private String exampleValue;
        /** 示例脱敏后值 */
        private String maskedExample;
    }
}
