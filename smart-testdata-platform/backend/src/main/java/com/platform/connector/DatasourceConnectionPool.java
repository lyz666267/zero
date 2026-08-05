package com.platform.connector;

import com.platform.entity.Datasource;
import com.platform.util.AesUtil;
import com.platform.util.JdbcUrlBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches HikariCP pools by datasource id so dynamic datasource operations reuse connections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceConnectionPool {

    private static final int MAX_POOL_SIZE = 10;
    private static final int MIN_IDLE = 2;
    private static final long CONNECTION_TIMEOUT_MS = 10_000L;

    private final AesUtil aesUtil;
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public Connection getConnection(Datasource ds) throws SQLException {
        return getDataSource(ds).getConnection();
    }

    public DataSource getDataSource(Datasource ds) {
        if (ds == null || ds.getId() == null) {
            throw new IllegalArgumentException("datasource id is required");
        }
        return pools.computeIfAbsent(ds.getId(), id -> createPool(ds));
    }

    public boolean testConnection(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);

        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            log.warn("datasource connection test failed: {}", e.getMessage());
            return false;
        }
    }

    public void evict(Long datasourceId) {
        HikariDataSource pool = pools.remove(datasourceId);
        if (pool != null) {
            pool.close();
            log.info("evicted datasource pool: id={}", datasourceId);
        }
    }

    @PreDestroy
    public void closeAll() {
        int count = pools.size();
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
        log.info("closed all dynamic datasource pools: count={}", count);
    }

    private HikariDataSource createPool(Datasource ds) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JdbcUrlBuilder.build(ds));
        config.setUsername(ds.getUsername());
        config.setPassword(aesUtil.decrypt(ds.getPasswordEncrypted()));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setPoolName("datasource-" + ds.getId());
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        config.addDataSourceProperty("serverTimezone", "Asia/Shanghai");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        config.addDataSourceProperty("useSSL", "false");
        return new HikariDataSource(config);
    }
}
