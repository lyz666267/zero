package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据源响应 DTO — 用于 API 返回，不包含加密密码字段
 *
 * <p>与 {@link com.platform.entity.Datasource} 实体分离，
 * 确保 {@code passwordEncrypted} 永远不会通过 JSON 序列化泄露到前端。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasourceResponse {

    private Long id;
    private Long projectId;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String username;
    private String dbName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从实体转换（不含密码字段）
     */
    public static DatasourceResponse fromEntity(com.platform.entity.Datasource ds) {
        return DatasourceResponse.builder()
                .id(ds.getId())
                .projectId(ds.getProjectId())
                .name(ds.getName())
                .dbType(ds.getDbType())
                .host(ds.getHost())
                .port(ds.getPort())
                .username(ds.getUsername())
                .dbName(ds.getDbName())
                .status(ds.getStatus())
                .createdAt(ds.getCreatedAt())
                .updatedAt(ds.getUpdatedAt())
                .build();
    }
}
