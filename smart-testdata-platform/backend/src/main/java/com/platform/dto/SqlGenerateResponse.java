package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL INSERT 生成响应
 *
 * <pre>
 * {
 *   "success": true,
 *   "sql": "INSERT INTO user (name, age) VALUES ('test', 20);"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlGenerateResponse {

    /** 是否成功 */
    private boolean success;

    /** 生成的 INSERT SQL 语句 */
    private String sql;
}
