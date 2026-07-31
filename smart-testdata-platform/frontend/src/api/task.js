import request from './request'

/**
 * 创建测试数据生成任务
 * @param {Object} data - { taskName: String, datasourceId: Number, totalCount: Number }
 * @returns {Promise} response.data = { id, taskName, status, ... }
 */
export function createTask(data) {
  return request.post('/testdata/task', data)
}

/**
 * 查询任务状态
 * @param {Number} id - 任务 ID
 * @returns {Promise} response.data = { id, taskName, status, totalCount, successCount, failCount, ... }
 */
export function getTask(id) {
  return request.get(`/testdata/task/${id}`)
}

/**
 * 查询任务生成结果
 * @param {Number} taskId - 任务 ID
 * @returns {Promise} response.data = { success: Boolean, tables: [{ tableName, rows: [...] }] }
 */
export function getTaskResult(taskId) {
  return request.get(`/testdata/task/${taskId}/result`)
}

/**
 * 查询任务的 AI 生成计划
 * @param {Number} taskId - 任务 ID
 * @returns {Promise} response.data = { success: Boolean, data: { taskName, tables: [...] } }
 */
export function getTaskPlan(taskId) {
  return request.get(`/testdata/task/${taskId}/plan`)
}
