package com.platform.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据库脱敏请求 — 预览 / 执行脱敏 SQL
 *
 * <p>preview 模式：传入 datasourceId + tableName，后端自动检测敏感字段并生成 SQL。
 * execute 模式：传入 taskId，执行之前预览过的 SQL。</p>
 */
@Data
public class DatabaseMaskRequest {

    /** 数据源 ID */
    private Long datasourceId;

    /** 目标表名 */
    private String tableName;

    /** 执行时需要传入之前预览生成的 taskId */
    private Long taskId;

    /**
     * 用户选择的敏感字段（可选，为空则自动检测全部敏感字段）
     * 每个字段包含列名和期望的脱敏策略名
     */
    private List<FieldStrategy> selectedFields;

    @Data
    public static class FieldStrategy {
        /** 列名 */
        private String columnName;
        /** 脱敏策略名（如 PHONE_MASK），为空则使用默认策略 */
        private String strategy;
    }
}
