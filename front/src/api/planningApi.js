import request from './request'

export function initPlanning(data) {
  return request.post('/planning/init', data)
}

export function autoGenerate(planningId, data) {
  return request.post(`/planning/${planningId}/auto-generate`, data)
}

export function getStats(planningId) {
  return request.get(`/planning/${planningId}/stats`)
}
