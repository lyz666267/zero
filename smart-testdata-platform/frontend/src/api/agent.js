import request from './request'

/**
 * 查询 Agent 执行日志（时间线展示）
 * @param {Number} taskId - 任务 ID
 * @returns {Promise} response.data = { taskId, steps: [{ stepNumber, action, stepType, toolName, status, executionTime, inputData, outputData }] }
 */
export function getAgentLogs(taskId) {
  return request.get(`/agent/log/${taskId}`)
}
