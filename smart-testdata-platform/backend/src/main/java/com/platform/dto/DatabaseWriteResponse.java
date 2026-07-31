package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据库写入响应
 *
 * <pre>
 * {
 *   "success": true,
 *   "tables": [
 *     { "table": "department", "success": true, "insertCount": 3 },
 *     { "table": "employee",   "success": true, "insertCount": 5 }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseWriteResponse {

    /** 整体是否成功 */
    private boolean success;

    /** 各表写入结果 */
    private List<WriteResult> tables;
}
