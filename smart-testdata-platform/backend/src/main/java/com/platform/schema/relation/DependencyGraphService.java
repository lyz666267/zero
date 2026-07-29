package com.platform.schema.relation;

import com.platform.dto.RelationAnalysisResponse.DependencyGraph;
import com.platform.dto.RelationAnalysisResponse.GraphEdge;
import com.platform.dto.RelationAnalysisResponse.RelationItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 依赖图服务 — 将外键关系转换为有向图
 *
 * <h3>图语义</h3>
 * <ul>
 *   <li>节点：涉及的所有表名</li>
 *   <li>边 from → to：from 依赖 to（from 持有指向 to 的外键）</li>
 *   <li>含义：生成数据时必须先有 to 表的记录，才能生成 from 表的记录</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>
 *   user.department_id → department.id
 *
 *   nodes: ["department", "user"]
 *   edges: [{ from: "user", to: "department" }]
 *
 *   解读: user 依赖 department，生成时先 department 后 user
 * </pre>
 */
@Slf4j
@Service
public class DependencyGraphService {

    /**
     * 根据外键关系构建依赖图
     *
     * @param relations 外键关系列表
     * @return 依赖图（节点 + 边）
     */
    public DependencyGraph build(List<RelationItem> relations) {
        Set<String> nodeSet = new LinkedHashSet<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (RelationItem r : relations) {
            nodeSet.add(r.getTable());
            nodeSet.add(r.getReferencedTable());
            edges.add(GraphEdge.builder()
                    .from(r.getTable())
                    .to(r.getReferencedTable())
                    .build());
        }

        DependencyGraph graph = DependencyGraph.builder()
                .nodes(new ArrayList<>(nodeSet))
                .edges(edges)
                .build();

        log.info("依赖图构建完成: nodes={}, edges={}", graph.getNodes().size(), graph.getEdges().size());
        return graph;
    }
}
