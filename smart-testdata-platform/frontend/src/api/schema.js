import request from './request'

/**
 * 查询缓存的 Schema 结构
 * @param {Number} datasourceId - 数据源 ID
 * @returns {Promise} response.data = { tables: [{ tableName, tableComment, columns: [...] }] }
 */
export function getSchemaCache(datasourceId) {
  return request.get(`/schema/cache/${datasourceId}`)
}

/**
 * 查询数据源的外键关系和生成顺序
 * @param {Number} datasourceId - 数据源 ID
 * @returns {Promise} response.data = { relations, graph: { nodes, edges }, generationOrder }
 */
export function getSchemaRelation(datasourceId) {
  return request.get(`/schema/relation/${datasourceId}`)
}
