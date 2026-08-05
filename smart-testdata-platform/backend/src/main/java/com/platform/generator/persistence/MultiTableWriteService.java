package com.platform.generator.persistence;

import com.platform.dto.DatabaseWriteRequest.TableData;
import com.platform.dto.DatabaseWriteResponse;
import com.platform.dto.WriteResult;
import com.platform.connector.DatasourceConnectionPool;
import com.platform.entity.Datasource;
import com.platform.exception.BusinessException;
import com.platform.schema.SchemaCacheService;
import com.platform.service.DatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多表事务写入服务 — 保证多张表的写入在同一事务中（全部成功或全部回滚）
 *
 * <h3>事务保障</h3>
 * <p>使用 {@link TransactionTemplate} + {@link DataSourceTransactionManager}
 * 管理动态数据源的事务。如果任意一张表写入失败，整个事务回滚，
 * 已写入的数据不会持久化。</p>
 *
 * <h3>示例</h3>
 * <pre>
 * writeAll(datasourceId, [department(3 rows), employee(5 rows)])
 *   → 全部在一个事务中写入，失败则全部回滚
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTableWriteService {

    private final DatasourceService datasourceService;
    private final SchemaCacheService schemaCacheService;
    private final DatasourceConnectionPool connectionPool;
    private final DatabaseWriter databaseWriter;

    /**
     * 在单个事务中写入多张表的数据
     *
     * @param datasourceId 目标数据源 ID
     * @param tables       表数据列表（按依赖顺序排列）
     * @return 写入结果
     * @throws BusinessException 任意表写入失败时抛出，已写入数据自动回滚
     */
    public DatabaseWriteResponse writeAll(Long datasourceId, List<TableData> tables) {
        if (tables == null || tables.isEmpty()) {
            return DatabaseWriteResponse.builder()
                    .success(true)
                    .tables(List.of())
                    .build();
        }

        // 1. 获取数据源配置（内部调用，不需要用户隔离）
        Datasource ds = datasourceService.getEntityById(datasourceId);

        // 1.5. Schema 缓存白名单校验 — 确保表名和列名均来自已知 Schema
        validateAgainstSchema(datasourceId, tables);

        // 2. 创建目标数据源
        DataSource dataSource = createDataSource(ds);

        // 3. 事务管理器
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        // 4. 在事务中执行所有写入
        try {
            return txTemplate.execute(status -> {
                List<WriteResult> results = new ArrayList<>();

                for (TableData table : tables) {
                    log.info("事务写入: table={}, rows={}", table.getTable(),
                            table.getData() != null ? table.getData().size() : 0);

                    try {
                        WriteResult result = databaseWriter.write(
                                dataSource, table.getTable(), table.getData());
                        results.add(result);
                    } catch (Exception e) {
                        log.error("表写入失败，事务将回滚: table={}, error={}",
                                table.getTable(), e.getMessage());
                        status.setRollbackOnly();
                        throw new BusinessException(
                                "写入表 " + table.getTable() + " 失败，事务已回滚: " + e.getMessage());
                    }
                }

                log.info("多表事务写入完成: {} 张表", results.size());
                return DatabaseWriteResponse.builder()
                        .success(true)
                        .tables(results)
                        .build();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("事务执行失败: {}", e.getMessage());
            throw new BusinessException("数据库写入失败: " + e.getMessage());
        }
    }

    /**
     * 根据 Schema 缓存验证表名和字段名是否合法（白名单校验，防 SQL 注入）
     *
     * <p>对于请求中的每个表，校验：</p>
     * <ol>
     *   <li>表名在 Schema 缓存中存在</li>
     *   <li>数据行中的所有列名在 Schema 缓存中存在</li>
     * </ol>
     *
     * @param datasourceId 数据源 ID
     * @param tables       待写入的表数据
     * @throws BusinessException 表名或列名不在白名单中
     */
    private void validateAgainstSchema(Long datasourceId, List<TableData> tables) {
        // 自动同步 Schema 缓存（如果不存在）
        if (!schemaCacheService.hasCache(datasourceId)) {
            log.info("Schema 缓存不存在，自动同步: datasourceId={}", datasourceId);
            schemaCacheService.sync(datasourceId);
        }

        var schema = schemaCacheService.getSchema(datasourceId);

        // 构建表名 → 列名集合的映射
        Map<String, Set<String>> schemaMap = new LinkedHashMap<>();
        for (var tableInfo : schema.getTables()) {
            Set<String> columnNames = tableInfo.getColumns().stream()
                    .map(col -> col.getName().toLowerCase())
                    .collect(Collectors.toSet());
            schemaMap.put(tableInfo.getTableName().toLowerCase(), columnNames);
        }

        for (TableData td : tables) {
            String tableName = td.getTable();
            if (tableName == null || tableName.isBlank()) {
                throw new BusinessException(400, "表名不能为空");
            }

            // 校验表名是否在 Schema 白名单中
            Set<String> validColumns = schemaMap.get(tableName.toLowerCase());
            if (validColumns == null) {
                throw new BusinessException(400,
                        "表 \"" + tableName + "\" 不在数据源 Schema 中，写入被拒绝");
            }

            // 校验数据中的列名是否都在 Schema 白名单中
            if (td.getData() != null) {
                for (Map<String, Object> row : td.getData()) {
                    if (row == null) continue;
                    for (String colName : row.keySet()) {
                        if (!validColumns.contains(colName.toLowerCase())) {
                            throw new BusinessException(400,
                                    "列 \"" + colName + "\" 不在表 \"" + tableName + "\" 的 Schema 中，写入被拒绝");
                        }
                    }
                }
            }
        }

        log.info("Schema 白名单校验通过: datasourceId={}, tables={}",
                datasourceId, tables.stream().map(TableData::getTable).collect(Collectors.toList()));
    }

    /**
     * 根据数据源配置创建 Spring DataSource
     */
    private DataSource createDataSource(Datasource ds) {
        return connectionPool.getDataSource(ds);
    }

}
