package com.platform.schema.relation;

import com.platform.dto.RelationAnalysisResponse.RelationItem;
import com.platform.entity.Datasource;
import com.platform.exception.BusinessException;
import com.platform.mapper.DatasourceMapper;
import com.platform.util.AesUtil;
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
    private final AesUtil aesUtil;

    /**
     * 分析指定数据源的外键关系
     *
     * @param datasourceId 数据源 ID
     * @return 外键关系列表
     */
    public List<RelationItem> analyze(Long datasourceId) {
        Datasource ds = getDatasource(datasourceId);
        String url = buildJdbcUrl(ds);
        String password = aesUtil.decrypt(ds.getPasswordEncrypted());

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
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), password);
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

    private String buildJdbcUrl(Datasource ds) {
        String dbType = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "mysql";
        return switch (dbType) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8"
                            + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                    ds.getHost(), ds.getPort(), ds.getDbName());
            default -> throw new BusinessException("暂不支持的数据库类型: " + ds.getDbType());
        };
    }
}
