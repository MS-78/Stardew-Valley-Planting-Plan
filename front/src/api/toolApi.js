import request from './request'

export function getTools(params) {
  return request.get('/tools', { params })
}

export function getToolById(id) {
  return request.get(`/tools/${id}`)
}

export function createTool(data) {
  return request.post('/tools', data)
}

export function updateTool(id, data) {
  return request.put(`/tools/${id}`, data)
}

export function deleteTool(id) {
  return request.delete(`/tools/${id}`)
}
