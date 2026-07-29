package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 关系分析综合响应 — 包含外键关系、依赖图、生成顺序
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationAnalysisResponse {

    /** 外键关系列表 */
    private List<RelationItem> relations;

    /** 依赖图（节点 + 边） */
    private DependencyGraph graph;

    /** 拓扑排序后的表生成顺序（被依赖的表在前） */
    private List<String> generationOrder;

    // ==================== 内嵌类型 ====================

    /** 单个外键关系 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationItem {

        /** 源表（持有外键的表） */
        private String table;

        /** 源列（外键列） */
        private String column;

        /** 引用的目标表 */
        private String referencedTable;

        /** 引用的目标列 */
        private String referencedColumn;
    }

    /** 依赖图 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencyGraph {

        /** 图中所有表名 */
        private List<String> nodes;

        /** 有向边：from 依赖 to（即 from 表有外键指向 to 表） */
        private List<GraphEdge> edges;
    }

    /** 有向边 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge {

        /** 依赖方（持有外键的表） */
        private String from;

        /** 被依赖方（被引用的表） */
        private String to;
    }
}
