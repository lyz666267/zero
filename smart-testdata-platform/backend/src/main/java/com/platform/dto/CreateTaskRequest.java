package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建测试数据生成任务请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {

    /** 任务名称 */
    private String taskName;

    /** 目标数据源 ID */
    private Long datasourceId;

    /** 目标生成总数 */
    private Integer totalCount;
}
