import request from './request'

/** 创建数据源配置 */
export function createDatasource(data) {
  return request.post('/datasource', data)
}

/** 查询项目下的数据源列表 */
export function getDatasourceList(projectId) {
  return request.get('/datasource', { params: { projectId } })
}

/** 查询数据源详情 */
export function getDatasourceById(id) {
  return request.get(`/datasource/${id}`)
}

/** 更新数据源配置 */
export function updateDatasource(id, data) {
  return request.put(`/datasource/${id}`, data)
}

/** 删除数据源 */
export function deleteDatasource(id) {
  return request.delete(`/datasource/${id}`)
}

/** 测试数据库连接（不需要先保存） */
export function testConnection(data) {
  return request.post('/datasource/test', data)
}

/** 获取数据库 Schema 信息 */
export function getSchema(id) {
  return request.get(`/datasource/${id}/schema`)
}
