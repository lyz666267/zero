package com.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称最多 128 个字符")
    private String name;

    @Size(max = 512, message = "项目描述最多 512 个字符")
    private String description;
}
