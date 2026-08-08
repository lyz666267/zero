import request from './request'

/**
 * 查询缓存的 Schema 结构
 * @param {Number} datasourceId - 数据源 ID
 * @returns {Promise} response.data = { tables: [{ tableName, tableComment, columns: [...] }] }
 */
export function getSchemaCache(datasourceId) {
  return request.get(`/schema/cache/${datasourceId}`, { silent: true })
}

/**
 * 同步数据源 Schema 到本地缓存
 * @param {Number} datasourceId - 数据源 ID
 */
export function syncSchemaCache(datasourceId) {
  return request.post('/schema/cache/sync', { datasourceId })
}

/**
 * 查询数据源的外键关系和生成顺序
 * @param {Number} datasourceId - 数据源 ID
 * @returns {Promise} response.data = { relations, graph: { nodes, edges }, generationOrder }
 */
export function getSchemaRelation(datasourceId) {
  return request.get(`/schema/relation/${datasourceId}`)
}

/**
 * AI 语义分析 Schema（调用 AI Agent 进行深度分析）
 * @param {Object} data - { database: String, dbType: String, tables: Array<{tableName, comment, columns}> }
 * @returns {Promise} response.data = { success, result: { tables, summary }, mock }
 */
export function analyzeSchema(data) {
  return request.post('/schema/analyze', data)
}
