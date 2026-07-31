package com.platform.dto;

import com.platform.entity.TestDataTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 测试数据生成任务响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private Long id;
    private String taskName;
    private Long datasourceId;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;

    /**
     * 从实体转换
     */
    public static TaskResponse fromEntity(TestDataTask task) {
        return TaskResponse.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .datasourceId(task.getDatasourceId())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .successCount(task.getSuccessCount())
                .failCount(task.getFailCount())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getCreateTime())
                .finishTime(task.getFinishTime())
                .build();
    }
}
