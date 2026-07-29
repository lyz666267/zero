import request from './request'

/**
 * 调用 AI 服务生成测试数据计划
 * @param {Object} data - { schema: Object, requirement: String }
 * @returns {Promise} response.data = { success, mock, plan, error }
 */
export function generatePlan(data) {
  return request.post('/testdata/generate-plan', data)
}
