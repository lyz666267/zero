package com.platform.controller;

import com.platform.dto.DatabaseMaskRequest;
import com.platform.dto.DatabaseMaskResponse;
import com.platform.service.DatabaseMaskService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 数据库脱敏控制器 — 对已有数据库业务数据进行安全脱敏
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/privacy/database/preview — 分析敏感字段 + 生成预览 UPDATE SQL</li>
 *   <li>POST /api/privacy/database/execute — 执行确认后的脱敏 SQL</li>
 *   <li>GET  /api/privacy/database/task/{id} — 查询脱敏任务执行结果</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <p><b>禁止直接执行。</b>必须经过：预览 → 确认 → 执行 的标准流程。</p>
 */
@RestController
@RequestMapping("/api/privacy/database")
@RequiredArgsConstructor
public class DatabaseMaskController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMaskController.class);

    private final DatabaseMaskService maskService;

    /**
     * 预览脱敏 SQL
     *
     * <p>连接目标数据库，检测敏感字段，自动分配脱敏策略并生成 UPDATE SQL 预览。
     * 返回敏感字段列表（含示例值和脱敏示例）供用户在确认前审查。</p>
     *
     * @param request 包含 datasourceId 和 tableName
     * @return 预览结果（taskId + 敏感字段列表 + UPDATE SQL）
     */
    @PostMapping("/preview")
    public DatabaseMaskResponse preview(@RequestBody DatabaseMaskRequest request) {
        log.info("收到脱敏预览请求: datasourceId={}, tableName={}",
                request.getDatasourceId(), request.getTableName());
        return maskService.preview(request);
    }

    /**
     * 执行脱敏 SQL
     *
     * <p>需要先调用 /preview 获得 taskId，确认 SQL 无误后再调用本端点执行。
     * 内部进行 SQL 安全检查（禁止 DROP / DELETE / TRUNCATE 等危险操作）。</p>
     *
     * @param request 包含 taskId
     * @return 执行结果（状态 + 影响行数 + 消息）
     */
    @PostMapping("/execute")
    public DatabaseMaskResponse execute(@RequestBody DatabaseMaskRequest request) {
        log.info("收到脱敏执行请求: taskId={}", request.getTaskId());
        DatabaseMaskResponse result = maskService.execute(request);
        log.info("脱敏执行完成: taskId={}, status={}, affectedRows={}",
                result.getTaskId(), result.getStatus(), result.getAffectedRows());
        return result;
    }

    /**
     * 查询脱敏任务结果
     *
     * @param id 任务 ID
     * @return 任务详情（状态 + SQL 预览 + 执行结果）
     */
    @GetMapping("/task/{id}")
    public DatabaseMaskResponse getTask(@PathVariable Long id) {
        log.info("查询脱敏任务: id={}", id);
        return maskService.getTask(id);
    }
}
