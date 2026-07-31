import request from './request'

/**
 * 列出可导出任务（仅 SUCCESS 状态）
 * @returns {Promise} response.data = [TaskResponse, ...]
 */
export function listExportableTasks() {
  return request.get('/export/tasks')
}

/**
 * 导出任务数据（返回 blob 文件流）
 * @param {Number} taskId - 任务 ID
 * @param {String} format - 导出格式: CSV / SQL / JSON
 * @returns {Promise} response 为 Blob（用于文件下载）
 */
export function exportTaskData(taskId, format) {
  return request.get(`/export/task/${taskId}`, {
    params: { format },
    responseType: 'blob'
  })
}

/**
 * 预览导出内容（JSON 格式文本，用于前端预览展示）
 * @param {Number} taskId - 任务 ID
 * @param {String} format - 导出格式: CSV / SQL / JSON
 * @returns {Promise} response 为文本字符串
 */
export function previewExportData(taskId, format) {
  return request.get(`/export/task/${taskId}`, {
    params: { format },
    responseType: 'text',
    // 覆盖默认的 JSON 解析，因为 CSV/SQL 不是 JSON
    transformResponse: [data => data]
  })
}
