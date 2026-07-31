import request from './request'

/**
 * 获取所有脱敏规则
 * @returns {Promise} response.data = [ { sensitiveType, typeLabel, strategy, description, exampleInput, exampleOutput } ]
 */
export function getMaskRules() {
  return request.get('/privacy/rules')
}

/**
 * 测试脱敏效果
 * @param {Object} params - { strategy: string, value: string, sensitiveType?: string }
 * @returns {Promise} response.data = { strategy, originalValue, maskedValue }
 */
export function testMask(params) {
  return request.post('/privacy/test', params)
}

/**
 * 对数据行执行脱敏（手动指定敏感字段）
 * @param {Object} params - { data: Array<Object>, sensitiveFields: Array<{columnName, type}> }
 * @returns {Promise} response.data = { success, data: Array<Object> }
 */
export function processPrivacy(params) {
  return request.post('/privacy/process', params)
}

/**
 * 自动检测并脱敏（三层融合检测）
 * @param {Object} params - { data: Array<Object>, columns: Array<{columnName, columnType, columnComment, dataType}> }
 * @returns {Promise} response.data = { success, data: Array<Object> }
 */
export function processPrivacyAuto(params) {
  return request.post('/privacy/process-auto', params)
}
