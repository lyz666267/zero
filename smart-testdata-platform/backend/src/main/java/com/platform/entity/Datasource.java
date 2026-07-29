package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源配置实体 — 映射 datasource 表
 */
@Data
@TableName("datasource")
public class Datasource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 数据源名称 */
    private String name;

    /** 数据库类型：MySQL / PostgreSQL */
    private String dbType;

    /** 主机地址 */
    private String host;

    /** 端口号 */
    private Integer port;

    /** 数据库用户名 */
    private String username;

    /** 密码（AES 加密存储） */
    private String passwordEncrypted;

    /** 数据库名 */
    private String dbName;

    /** 连接状态：UNCONNECTED / CONNECTED / ERROR */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
