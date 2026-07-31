package com.platform.util;

import com.platform.entity.Datasource;
import com.platform.exception.BusinessException;

/**
 * JDBC URL 构建器 — 统一的 JDBC 连接 URL 构建入口
 *
 * <h3>职责</h3>
 * <p>从数据源配置构建标准的 MySQL JDBC 连接 URL。
 * 项目中所有需要构建 JDBC URL 的地方都应通过此类统一处理，
 * 避免 URL 格式分散在多处导致不一致。</p>
 *
 * <h3>URL 格式</h3>
 * <pre>
 * jdbc:mysql://{host}:{port}/{dbName}?useUnicode=true&amp;characterEncoding=UTF-8
 *     &amp;serverTimezone=Asia/Shanghai&amp;allowPublicKeyRetrieval=true&amp;useSSL=false
 * </pre>
 *
 * <h3>扩展性</h3>
 * <p>当前仅支持 MySQL。如需支持 PostgreSQL/Oracle 等数据库，
 * 在此处新增 switch 分支即可，所有调用方自动获得新数据库类型的支持。</p>
 */
public final class JdbcUrlBuilder {

    private JdbcUrlBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 根据数据源实体构建 JDBC URL
     *
     * @param ds 数据源实体（含 host/port/dbName/dbType）
     * @return JDBC 连接 URL
     * @throws BusinessException 数据库类型不支持时抛出
     */
    public static String build(Datasource ds) {
        return build(ds.getHost(), ds.getPort(), ds.getDbName(), ds.getDbType());
    }

    /**
     * 根据原始参数构建 JDBC URL
     *
     * @param host   数据库主机地址
     * @param port   数据库端口
     * @param dbName 数据库名称
     * @param dbType 数据库类型（mysql / postgresql / ...）
     * @return JDBC 连接 URL
     * @throws BusinessException 数据库类型不支持时抛出
     */
    public static String build(String host, int port, String dbName, String dbType) {
        String type = dbType != null ? dbType.toLowerCase() : "mysql";
        return switch (type) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8"
                            + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                    host, port, dbName);
            default -> throw new BusinessException("暂不支持的数据库类型: " + dbType);
        };
    }
}
