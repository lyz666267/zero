package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单表写入结果
 *
 * <pre>
 * {
 *   "success": true,
 *   "table": "user",
 *   "insertCount": 10
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WriteResult {

    /** 是否成功 */
    private boolean success;

    /** 表名 */
    private String table;

    /** 实际写入行数 */
    private int insertCount;
}
