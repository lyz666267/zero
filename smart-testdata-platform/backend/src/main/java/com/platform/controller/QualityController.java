package com.platform.controller;

import com.platform.dto.QualityReportResponse;
import com.platform.exception.BusinessException;
import com.platform.service.DataQualityEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 数据质量评估接口
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/quality/evaluate/{taskId} — 执行质量评估并生成报告</li>
 *   <li>GET /api/quality/report/{taskId} — 查询已保存的质量报告</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final DataQualityEvaluator evaluator;

    /**
     * 执行质量评估
     *
     * <p>对已完成生成任务的数据进行五项指标评估（完整性/唯一性/一致性/合法性/隐私），
     * 计算综合评分和等级，保存报告并返回完整结果。</p>
     *
     * @param taskId       任务 ID
     * @param datasourceId 数据源 ID（可选，用于外键一致性校验）
     * @return 完整的质量评估报告
     */
    @PostMapping("/evaluate/{taskId}")
    public QualityReportResponse evaluate(
            @PathVariable Long taskId,
            @RequestParam(required = false) Long datasourceId) {
        log.info("收到质量评估请求: taskId={}, datasourceId={}", taskId, datasourceId);

        if (datasourceId == null) {
            throw new BusinessException(400, "缺少 datasourceId 参数，请提供数据源 ID");
        }

        return evaluator.evaluate(taskId, datasourceId);
    }

    /**
     * 查询质量报告
     *
     * <p>返回之前执行评估时保存的质量报告。如果任务尚未评估，返回 404。</p>
     *
     * @param taskId 任务 ID
     * @return 质量评估报告
     */
    @GetMapping("/report/{taskId}")
    public QualityReportResponse getReport(@PathVariable Long taskId) {
        log.info("查询质量报告: taskId={}", taskId);

        QualityReportResponse report = evaluator.getReport(taskId);
        if (report == null) {
            throw new BusinessException(404, "该任务尚未执行质量评估，请先调用 POST /api/quality/evaluate/" + taskId);
        }

        return report;
    }
}
