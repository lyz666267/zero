import request from './request'

/**
 * 预览脱敏 SQL — 分析敏感字段并生成 UPDATE 语句
 * @param {Object} params - { datasourceId: number, tableName: string }
 * @returns {Promise} response.data = { taskId, tableName, status, sensitiveFields, sqlPreview }
 */
export function previewMask(params) {
  return request.post('/privacy/database/preview', params)
}

/**
 * 执行脱敏 SQL — 需要先调用 previewMask 获得 taskId
 * @param {Object} params - { taskId: number }
 * @returns {Promise} response.data = { taskId, tableName, status, executeResult, affectedRows }
 */
export function executeMask(params) {
  return request.post('/privacy/database/execute', params)
}

/**
 * 查询脱敏任务详情
 * @param {number} id - 任务 ID
 * @returns {Promise} response.data = { taskId, tableName, status, sqlPreview, executeResult, affectedRows }
 */
export function getMaskTask(id) {
  return request.get(`/privacy/database/task/${id}`)
}
