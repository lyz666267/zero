package com.platform.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Schema 缓存同步请求
 */
@Data
public class SchemaCacheSyncRequest {

    @NotNull(message = "数据源 ID 不能为空")
    private Long datasourceId;
}
