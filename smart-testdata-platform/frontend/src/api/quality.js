import request from './request'

/**
 * 执行质量评估
 * @param {number} taskId - 任务 ID
 * @param {number} datasourceId - 数据源 ID
 */
export function evaluateQuality(taskId, datasourceId) {
  return request.post(`/quality/evaluate/${taskId}`, null, {
    params: { datasourceId }
  })
}

/**
 * 查询质量报告
 * @param {number} taskId - 任务 ID
 */
export function getQualityReport(taskId) {
  return request.get(`/quality/report/${taskId}`)
}
