package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Schema 缓存同步响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaCacheSyncResponse {

    /** 是否成功 */
    private boolean success;

    /** 同步的表数量 */
    private int tableCount;

    /** 同步的列总数 */
    private int columnCount;
}
