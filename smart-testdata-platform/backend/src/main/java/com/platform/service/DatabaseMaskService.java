package com.platform.service;

import com.platform.dto.DatabaseMaskRequest;
import com.platform.dto.DatabaseMaskResponse;
import com.platform.dto.DatabaseMaskResponse.MaskFieldInfo;
import com.platform.connector.ColumnCollector;
import com.platform.connector.DatasourceConnectionPool;
import com.platform.entity.Datasource;
import com.platform.entity.DataMaskTask;
import com.platform.entity.schema.SchemaColumn;
import com.platform.exception.BusinessException;
import com.platform.mapper.DataMaskTaskMapper;
import com.platform.mapper.DatasourceMapper;
import com.platform.privacy.SensitiveFieldType;
import com.platform.privacy.detector.DetectionContext;
import com.platform.privacy.detector.DetectionResult;
import com.platform.privacy.detector.SensitiveDetector;
import com.platform.privacy.executor.MaskExecutor;
import com.platform.privacy.mask.MaskRule;
import com.platform.privacy.mask.MaskRuleRegistry;
import com.platform.privacy.mask.MaskStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 数据库脱敏服务 — 对已有数据库业务数据执行安全脱敏
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>选择数据源 → 选择表</li>
 *   <li>分析敏感字段（{@link SensitiveFieldDetector} + {@link MaskRuleRegistry}）</li>
 *   <li>自动分配脱敏策略 + 生成预览 UPDATE SQL</li>
 *   <li>创建脱敏任务（状态 PREVIEW）</li>
 *   <li>用户确认 → 执行 SQL（状态 SUCCESS / FAILED）</li>
 * </ol>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li><b>必须</b>经过预览→确认→执行流程，禁止直接执行</li>
 *   <li>SQL 安全检查：禁止 DROP / DELETE / TRUNCATE / ALTER / INSERT</li>
 *   <li>仅生成单列 UPDATE 语句，每列一条 SQL</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseMaskService {

    private final DatasourceMapper datasourceMapper;
    private final DataMaskTaskMapper taskMapper;
    private final DatasourceConnectionPool connectionPool;
    private final SensitiveDetector sensitiveDetector;
    private final MaskRuleRegistry ruleRegistry;
    private final MaskExecutor maskExecutor;
    private final ColumnCollector columnCollector;

    /**
     * 表名安全格式校验正则：
     * <ul>
     *   <li>必须以字母或下划线开头</li>
     *   <li>后续字符只能是字母、数字或下划线</li>
     *   <li>长度 1~64（MySQL 标识符上限）</li>
     * </ul>
     */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    /** 敏感类型 → 中文标签 */
    private static final Map<SensitiveFieldType, String> TYPE_LABELS = Map.of(
            SensitiveFieldType.PHONE, "手机号",
            SensitiveFieldType.EMAIL, "邮箱",
            SensitiveFieldType.ID_CARD, "身份证号",
            SensitiveFieldType.NAME, "姓名",
            SensitiveFieldType.ADDRESS, "地址",
            SensitiveFieldType.BANK_CARD, "银行卡号"
    );

    // ==================== 公共 API ====================

    /**
     * 预览脱敏 SQL — 连接目标数据库，检测敏感字段，生成 UPDATE 语句并保存任务。
     *
     * @param request 包含 datasourceId 和 tableName
     * @return 预览结果（敏感字段列表 + SQL + 示例值）
     */
    public DatabaseMaskResponse preview(DatabaseMaskRequest request) {
        Long datasourceId = request.getDatasourceId();
        String tableName = request.getTableName();

        if (datasourceId == null || tableName == null || tableName.isBlank()) {
            throw new BusinessException(400, "数据源 ID 和表名不能为空");
        }

        // 表名格式安全校验（防 SQL 注入）
        validateTableName(tableName);

        // 1. 加载数据源
        Datasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }


        // 2. 读取表结构 + 检测敏感字段
        List<SchemaColumn> allColumns;
        List<Map<String, Object>> sampleRows;
        try (Connection conn = connectionPool.getConnection(ds)) {
            allColumns = columnCollector.readColumns(conn, ds.getDbName(), tableName);
            sampleRows = readSampleRows(conn, ds.getDbName(), tableName, 3);
        } catch (SQLException e) {
            log.error("连接目标数据库失败: {}", e.getMessage());
            throw new BusinessException("数据库连接失败: " + e.getMessage());
        }

        if (allColumns.isEmpty()) {
            throw new BusinessException(404, "表 " + tableName + " 不存在或没有列");
        }

        // 3. 检测敏感字段
        DetectionContext ctx = new DetectionContext(allColumns, sampleRows);
        var detectedFields = sensitiveDetector.detect(ctx);
        if (detectedFields.isEmpty()) {
            log.info("未检测到敏感字段，无需脱敏: table={}", tableName);
            return DatabaseMaskResponse.builder()
                    .tableName(tableName)
                    .status("NO_SENSITIVE")
                    .sensitiveFields(List.of())
                    .sqlPreview("")
                    .build();
        }

        // 4. 构建字段信息 + 生成 SQL
        List<MaskFieldInfo> fields = new ArrayList<>();
        List<String> updateSqls = new ArrayList<>();

        for (var sf : detectedFields) {
            SensitiveFieldType type = sf.getType();
            Optional<MaskRule> ruleOpt = ruleRegistry.lookup(type);
            if (ruleOpt.isEmpty()) {
                continue;
            }

            MaskRule rule = ruleOpt.get();
            MaskStrategy strategy = rule.strategy();

            // 获取示例值
            String exampleValue = findSampleValue(sampleRows, sf.getColumnName());

            // 示例脱敏
            String maskedExample = "";
            if (exampleValue != null && !exampleValue.isEmpty()) {
                maskedExample = maskExecutor.mask(exampleValue, strategy);
            }

            // 生成 UPDATE SQL
            String updateSql = buildUpdateSql(tableName, sf.getColumnName(), strategy);
            updateSqls.add(updateSql);

            fields.add(MaskFieldInfo.builder()
                    .columnName(sf.getColumnName())
                    .sensitiveType(type.name())
                    .typeLabel(TYPE_LABELS.getOrDefault(type, type.name()))
                    .strategy(strategy.name())
                    .strategyDescription(rule.description())
                    .exampleValue(exampleValue != null ? exampleValue : "")
                    .maskedExample(maskedExample)
                    .build());
        }

        if (fields.isEmpty()) {
            throw new BusinessException(400, "检测到的敏感字段无对应脱敏策略");
        }

        String sqlPreview = String.join(";\n", updateSqls) + ";";

        // 5. 保存任务
        DataMaskTask task = DataMaskTask.builder()
                .datasourceId(datasourceId)
                .tableName(tableName)
                .status("PREVIEW")
                .sqlPreview(sqlPreview)
                .createdAt(null)  // 由 MyBatis-Plus 自动填充
                .build();
        taskMapper.insert(task);

        log.info("脱敏预览生成成功: taskId={}, table={}, fields={}", task.getId(), tableName, fields.size());

        return DatabaseMaskResponse.builder()
                .taskId(task.getId())
                .tableName(tableName)
                .status("PREVIEW")
                .sensitiveFields(fields)
                .sqlPreview(sqlPreview)
                .build();
    }

    /**
     * 执行脱敏 SQL — 连接目标数据库执行之前预览过的 UPDATE 语句。
     *
     * @param request 包含 taskId
     * @return 执行结果
     */
    public DatabaseMaskResponse execute(DatabaseMaskRequest request) {
        Long taskId = request.getTaskId();
        if (taskId == null) {
            throw new BusinessException(400, "任务 ID 不能为空");
        }

        // 1. 加载任务
        DataMaskTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "脱敏任务不存在");
        }

        if (!"PREVIEW".equals(task.getStatus())) {
            throw new BusinessException(400, "任务状态不是 PREVIEW，无法执行。当前状态: " + task.getStatus());
        }

        if (task.getSqlPreview() == null || task.getSqlPreview().isBlank()) {
            throw new BusinessException(400, "没有可执行的 SQL 预览");
        }

        // 2. SQL 安全检查
        String sql = task.getSqlPreview();
        validateSqlSafety(sql, task.getTableName());

        // 3. 加载数据源
        Datasource ds = datasourceMapper.selectById(task.getDatasourceId());
        if (ds == null) {
            throw new BusinessException(404, "数据源不存在");
        }


        // 4. 更新状态为执行中
        task.setStatus("EXECUTING");
        taskMapper.updateById(task);

        // 5. 执行 SQL
        int totalAffected = 0;
        try (Connection conn = connectionPool.getConnection(ds)) {
            validateTableName(conn, ds.getDbName(), task.getTableName());
            conn.setAutoCommit(false);

            // 按 ; 拆分多条 UPDATE 语句
            String[] statements = sql.split(";\\s*");
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try (Statement jdbcStmt = conn.createStatement()) {
                    int affected = jdbcStmt.executeUpdate(trimmed);
                    totalAffected += affected;
                    log.info("SQL 执行成功: affected={}, sql={}", affected,
                            trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed);
                }
            }

            conn.commit();
            log.info("脱敏执行完成: taskId={}, totalAffected={}", taskId, totalAffected);

            // 6. 更新任务状态
            task.setStatus("SUCCESS");
            task.setExecuteResult("脱敏执行成功，共影响 " + totalAffected + " 行数据");
            task.setAffectedRows(totalAffected);
            taskMapper.updateById(task);

            return DatabaseMaskResponse.builder()
                    .taskId(taskId)
                    .tableName(task.getTableName())
                    .status("SUCCESS")
                    .sqlPreview(sql)
                    .executeResult(task.getExecuteResult())
                    .affectedRows(totalAffected)
                    .build();

        } catch (SQLException e) {
            log.error("脱敏执行失败: taskId={}, error={}", taskId, e.getMessage());

            // 回滚状态
            task.setStatus("FAILED");
            task.setExecuteResult("执行失败: " + e.getMessage());
            task.setAffectedRows(totalAffected);
            taskMapper.updateById(task);

            return DatabaseMaskResponse.builder()
                    .taskId(taskId)
                    .tableName(task.getTableName())
                    .status("FAILED")
                    .sqlPreview(sql)
                    .executeResult(task.getExecuteResult())
                    .affectedRows(totalAffected)
                    .build();
        }
    }

    /**
     * 查询脱敏任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    public DatabaseMaskResponse getTask(Long id) {
        DataMaskTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "脱敏任务不存在");
        }

        return DatabaseMaskResponse.builder()
                .taskId(task.getId())
                .tableName(task.getTableName())
                .status(task.getStatus())
                .sqlPreview(task.getSqlPreview())
                .executeResult(task.getExecuteResult())
                .affectedRows(task.getAffectedRows())
                .build();
    }

    // ==================== SQL 生成 ====================

    /**
     * 根据脱敏策略为指定列生成 MySQL UPDATE 语句
     *
     * <p>使用 MySQL 内置字符串函数实现脱敏，无需应用层处理每一行数据。</p>
     */
    String buildUpdateSql(String tableName, String columnName, MaskStrategy strategy) {
        validateTableName(tableName);

        String col = "`" + columnName + "`";
        String tbl = "`" + tableName + "`";

        String setExpr = switch (strategy) {
            case PHONE_MASK -> String.format(
                    "CONCAT(LEFT(%s, 3), '****', RIGHT(%s, 4))", col, col);
            case NAME_MASK -> String.format(
                    "CONCAT(LEFT(%s, 1), REPEAT('*', CHAR_LENGTH(%s) - 1))", col, col);
            case ID_CARD_MASK -> String.format(
                    "CONCAT(LEFT(%s, 6), REPEAT('*', CHAR_LENGTH(%s) - 10), RIGHT(%s, 4))", col, col, col);
            case EMAIL_MASK -> String.format(
                    "CONCAT(LEFT(%s, 3), '***', SUBSTRING(%s, INSTR(%s, '@')))", col, col, col);
            case ADDRESS_MASK -> String.format(
                    "CONCAT(LEFT(%s, 6), REPEAT('*', GREATEST(0, CHAR_LENGTH(%s) - 6)))", col, col);
            case BANK_CARD_MASK -> String.format(
                    "CONCAT('****', RIGHT(%s, 4))", col, col);
        };

        // WHERE 条件：不更新 NULL 和空字符串
        return String.format("UPDATE %s SET %s = %s WHERE %s IS NOT NULL AND %s != ''", tbl, col, setExpr, col, col);
    }

    // ==================== SQL 安全检查 ====================

    /**
     * 验证 SQL 安全性，确保只包含安全的 UPDATE 操作。
     *
     * <h3>禁止项</h3>
     * <ul>
     *   <li>DROP / DELETE / TRUNCATE / ALTER / CREATE / INSERT 关键字</li>
     *   <li>操作的表名与预期不符</li>
     *   <li>多语句注入（除分号分隔的多个 UPDATE 外）</li>
     * </ul>
     */
    void validateSqlSafety(String sql, String expectedTableName) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(400, "SQL 为空，无法执行");
        }

        String upperSql = sql.toUpperCase().trim();

        // 禁止的关键字
        List<String> forbidden = List.of("DROP ", "DELETE ", "TRUNCATE ", "ALTER ", "CREATE ", "INSERT ",
                "DROP\n", "DELETE\n", "TRUNCATE\n", "ALTER\n", "CREATE\n", "INSERT\n");

        for (String keyword : forbidden) {
            if (upperSql.contains(keyword)) {
                throw new BusinessException(400, "SQL 安全检查失败：包含禁止的关键字 " + keyword.trim());
            }
        }

        // 验证以 UPDATE 开头（忽略前导空白）
        if (!upperSql.startsWith("UPDATE ")) {
            throw new BusinessException(400, "SQL 安全检查失败：仅允许 UPDATE 语句");
        }

        // 验证表名匹配
        if (!upperSql.contains("UPDATE `" + expectedTableName.toUpperCase() + "`")
                && !upperSql.contains("UPDATE " + expectedTableName.toUpperCase())) {
            throw new BusinessException(400,
                    "SQL 安全检查失败：UPDATE 的目标表与预期不符，预期: " + expectedTableName);
        }

        log.info("SQL 安全检查通过: table={}, length={}", expectedTableName, sql.length());
    }

    // ==================== 数据库连接 ====================

    /**
     * 校验表名格式安全性，防止 SQL 注入。
     *
     * <p>仅允许符合标准标识符格式的表名：字母/下划线开头，字母/数字/下划线构成，长度 1~64。
     * 同时作为 {@link #tableExists(Connection, String, String)} 的前置防线。</p>
     *
     * @param tableName 待校验的表名
     * @throws BusinessException 表名格式不合法
     */
    private void validateTableName(String tableName) {
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new BusinessException(400,
                    "表名格式不合法: " + tableName + "（仅允许字母、数字和下划线，且必须以字母或下划线开头）");
        }
    }

    /**
     * 读取表中的样本行（先通过 information_schema 校验表名安全性）
     */
    private List<Map<String, Object>> readSampleRows(Connection conn, String dbName, String tableName, int limit) throws SQLException {
        validateTableName(conn, dbName, tableName);

        // 表名已通过 information_schema 校验，拼接 SQL 安全
        String sql = String.format("SELECT * FROM `%s` LIMIT %d", tableName, limit);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 统一表名校验：先做格式白名单校验，再通过 information_schema 确认表存在。
     */
    private void validateTableName(Connection conn, String dbName, String tableName) throws SQLException {
        validateTableName(tableName);
        if (!tableExists(conn, dbName, tableName)) {
            throw new BusinessException(404, "表不存在: " + tableName);
        }
    }

    /**
     * 通过 information_schema 校验表是否存在于目标数据库（参数化查询）
     */
    private boolean tableExists(Connection conn, String dbName, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 从样本行中查找指定列的值
     */
    private String findSampleValue(List<Map<String, Object>> rows, String columnName) {
        for (Map<String, Object> row : rows) {
            Object val = row.get(columnName);
            if (val != null) {
                return val.toString();
            }
        }
        return "";
    }
}
