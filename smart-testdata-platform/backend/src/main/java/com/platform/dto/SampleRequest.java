package com.platform.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 数据采样请求
 */
@Data
public class SampleRequest {

    /** 数据源 ID */
    @NotNull(message = "数据源 ID 不能为空")
    private Long datasourceId;

    /** 要采样的表名列表 */
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tables;
}
