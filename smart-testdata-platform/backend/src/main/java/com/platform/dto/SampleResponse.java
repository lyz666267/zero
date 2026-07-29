package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据采样响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleResponse {

    /** 是否成功 */
    private boolean success;

    /** 各表的采样数据 */
    private List<TableSample> data;

    /** 单表采样数据 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableSample {

        /** 表名 */
        private String table;

        /** 列名 → 采样值列表 */
        private Map<String, List<Object>> columns;
    }
}
