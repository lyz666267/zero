package com.platform.schema.relation;

import com.platform.dto.RelationAnalysisResponse.RelationItem;
import com.platform.connector.DatasourceConnectionPool;
import com.platform.entity.Datasource;
import com.platform.exception.BusinessException;
import com.platform.mapper.DatasourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * 关系分析器 — 读取 information_schema.KEY_COLUMN_USAGE 获取外键依赖关系
 *
 * <h3>数据来源</h3>
 * <ul>
 *   <li>MySQL: {@code information_schema.KEY_COLUMN_USAGE} 表</li>
 *   <li>过滤条件: {@code REFERENCED_TABLE_NAME IS NOT NULL}，只取真实外键</li>
 * </ul>
 *
 * <h3>安全</h3>
 * <ul>
 *   <li>只读查询，不修改目标库任何数据</li>
 *   <li>表名/列名来自 information_schema 自身，不拼接用户输入</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationAnalyzerService {

    private final DatasourceMapper datasourceMapper;
    private final DatasourceConnectionPool connectionPool;

    /**
     * 分析指定数据源的外键关系
     *
     * @param datasourceId 数据源 ID
     * @return 外键关系列表
     */
    public List<RelationItem> analyze(Long datasourceId) {
        Datasource ds = getDatasource(datasourceId);

        String sql = """
                SELECT
                    TABLE_NAME,
                    COLUMN_NAME,
                    REFERENCED_TABLE_NAME,
                    REFERENCED_COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = ?
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                ORDER BY TABLE_NAME, COLUMN_NAME
                """;

        List<RelationItem> relations = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection(ds);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ds.getDbName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(RelationItem.builder()
                            .table(rs.getString("TABLE_NAME"))
                            .column(rs.getString("COLUMN_NAME"))
                            .referencedTable(rs.getString("REFERENCED_TABLE_NAME"))
                            .referencedColumn(rs.getString("REFERENCED_COLUMN_NAME"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("读取外键关系失败: datasourceId={}, error={}", datasourceId, e.getMessage());
            throw new BusinessException("读取外键关系失败: " + e.getMessage());
        }

        log.info("外键关系分析完成: datasourceId={}, relations={}", datasourceId, relations.size());
        return relations;
    }

    // ==================== 工具方法 ====================

    private Datasource getDatasource(Long datasourceId) {
        Datasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        return ds;
    }
}
