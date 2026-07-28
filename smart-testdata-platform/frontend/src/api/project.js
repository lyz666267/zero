import request from './request'

export function getDashboardStats() {
  return request.get('/projects/dashboard/stats')
}

export function getProjectList(page = 1, size = 10) {
  return request.get('/projects', { params: { page, size } })
}

export function getProjectById(id) {
  return request.get(`/projects/${id}`)
}

export function createProject(data) {
  return request.post('/projects', data)
}

export function updateProject(id, data) {
  return request.put(`/projects/${id}`, data)
}

export function deleteProject(id) {
  return request.delete(`/projects/${id}`)
}
