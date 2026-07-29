package com.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源创建/更新/测试请求
 * 创建时必须提供 projectId；测试连接时 projectId 可选
 */
@Data
public class DatasourceRequest {

    /** 所属项目（创建时需要，测试连接时可选） */
    private Long projectId;

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /** 数据库类型，默认 MySQL */
    private String dbType = "MySQL";

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @Min(value = 1, message = "端口号范围 1-65535")
    @Max(value = 65535, message = "端口号范围 1-65535")
    private Integer port = 3306;

    @NotBlank(message = "数据库用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "数据库名不能为空")
    private String databaseName;
}
