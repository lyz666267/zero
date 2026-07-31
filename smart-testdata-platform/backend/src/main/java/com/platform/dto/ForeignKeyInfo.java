package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外键信息 — 字段级外键关联描述
 *
 * <pre>
 * {
 *   "table": "department",
 *   "column": "id"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKeyInfo {

    /** 关联的目标表名 */
    private String table;

    /** 关联的目标列名 */
    private String column;
}
