package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.dto.TaskResponse;
import com.platform.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 测试数据导出接口
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/export/task/{taskId}?format=CSV|SQL|JSON — 导出任务数据</li>
 *   <li>GET  /api/export/task/{taskId}?format=CSV|SQL|JSON — 同上（浏览器友好）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 * // 前端调用
 * POST /api/export/task/1?format=CSV
 * → Content-Disposition: attachment; filename="task_1_20260801_120000.csv"
 * → Content-Type: text/csv
 * </pre>
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * 列出可导出任务（仅 SUCCESS 状态）
     *
     * <p>GET /api/export/tasks</p>
     */
    @GetMapping("/tasks")
    public ApiResponse<List<TaskResponse>> listTasks() {
        return ApiResponse.success(exportService.listExportableTasks());
    }

    /**
     * 导出任务数据（GET — 浏览器友好）
     *
     * <p>支持 GET 和 POST 两种方式，GET 便于浏览器直接下载。</p>
     *
     * @param taskId 任务 ID
     * @param format 导出格式：CSV / SQL / JSON（默认 JSON）
     * @return 文件流响应，Content-Disposition: attachment
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<byte[]> exportByGet(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "JSON") String format) {
        return doExport(taskId, format);
    }

    @PostMapping("/task/{taskId}")
    public ResponseEntity<byte[]> exportByPost(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "JSON") String format) {
        return doExport(taskId, format);
    }

    /**
     * 执行导出逻辑，返回文件下载响应
     */
    private ResponseEntity<byte[]> doExport(Long taskId, String format) {
        String content = exportService.exportTaskData(taskId, format);
        String fileName = exportService.generateFileName(taskId, format);
        String contentType = resolveContentType(format);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(bytes.length)
                .body(bytes);
    }

    /**
     * 根据导出格式确定 Content-Type
     */
    private String resolveContentType(String format) {
        String fmt = format != null ? format.toUpperCase().trim() : "JSON";
        return switch (fmt) {
            case "CSV" -> "text/csv; charset=UTF-8";
            case "SQL" -> "text/plain; charset=UTF-8";
            default -> "application/json; charset=UTF-8";
        };
    }
}
