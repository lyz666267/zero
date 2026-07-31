package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试数据生成结果实体 — 映射 testdata_result 表
 *
 * <p>一个任务可对应多条结果记录，每张生成过数据的表各一条。
 * {@link #dataJson} 以 JSON 数组格式存储该表生成的所有数据行。</p>
 */
@Data
@TableName("testdata_result")
public class TestDataResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务 ID */
    private Long taskId;

    /** 来源表名 */
    private String tableName;

    /** 生成的数据行（JSON 数组） */
    private String dataJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
