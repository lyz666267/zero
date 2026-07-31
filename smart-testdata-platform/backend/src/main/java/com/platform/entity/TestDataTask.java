package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试数据生成任务实体 — 映射 testdata_task 表
 */
@Data
@TableName("testdata_task")
public class TestDataTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 目标数据源 ID */
    private Long datasourceId;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 目标生成总数 */
    private Integer totalCount;

    /** 成功生成数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failCount;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 完成时间 */
    private LocalDateTime finishTime;
}
