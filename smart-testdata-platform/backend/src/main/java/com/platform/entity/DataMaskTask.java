package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据库脱敏任务实体 — 映射 data_mask_task 表
 *
 * <p>记录对已有数据库业务数据执行安全脱敏的任务信息。
 * 流程：选择数据源 → 选择表 → 分析敏感字段 → 预览SQL → 确认 → 执行。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("data_mask_task")
public class DataMaskTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 目标脱敏表名 */
    private String tableName;

    /** 状态：PREVIEW / EXECUTING / SUCCESS / FAILED */
    private String status;

    /** 预览生成的 UPDATE SQL 语句（多条用 ;\n 分隔） */
    private String sqlPreview;

    /** 执行结果消息 */
    private String executeResult;

    /** SQL 执行影响行数 */
    private Integer affectedRows;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
