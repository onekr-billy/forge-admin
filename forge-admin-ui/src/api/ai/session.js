import { request } from '@/utils'

export function sessionPage(params) {
  return request.get('/ai/admin/session/page', { params })
}

export function sessionMessages(sessionId) {
  return request.get(`/ai/admin/session/${sessionId}/messages`)
}

export function sessionDelete(sessionId) {
  return request.delete(`/ai/admin/session/${sessionId}`)
}

export function sessionStatistics() {
  return request.get('/ai/admin/session/statistics')
}

export function sessionExperienceMetrics() {
  return request.get('/ai/admin/session/experience-metrics')
}

export function sessionList() {
  return request.get('/ai/session/list')
}

export function sessionPageByUser(params) {
  return request.get('/ai/session/page', { params })
}

export function sessionCreate(data) {
  return request.post('/ai/session', data)
}

export function sessionRename(sessionId, sessionName) {
  return request.put(`/ai/session/${sessionId}`, { sessionName })
}

export function sessionPin(sessionId, pinned) {
  return request.put(`/ai/session/${sessionId}/pin`, null, { params: { pinned } })
}

export function sessionMessagesByUser(sessionId) {
  return request.get(`/ai/session/${sessionId}/messages`)
}

// 变更B#7：会话消息游标分页。params: { beforeId?, size? }；beforeId 为空取最近一页，上滑传当前最早消息 id 取更早一页
export function sessionMessagesPageByUser(sessionId, params) {
  return request.get(`/ai/session/${sessionId}/messages/page`, { params })
}

export function sessionDeleteByUser(sessionId) {
  return request.delete(`/ai/session/${sessionId}`)
}

export function messageDelete(recordId) {
  return request.delete(`/ai/message/${recordId}`)
}
