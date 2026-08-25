import { request } from '@/utils'

export function agentToolPage(params) {
  return request.get('/ai/agent-tool/page', { params })
}

export function agentToolGetById(id) {
  return request.get(`/ai/agent-tool/${id}`)
}

export function agentToolAdd(data) {
  return request.post('/ai/agent-tool', data)
}

export function agentToolUpdate(data) {
  return request.put('/ai/agent-tool', data)
}

export function agentToolDelete(id) {
  return request.delete(`/ai/agent-tool/${id}`)
}

export function agentToolPermissions(agentId, toolKey) {
  return request.get(`/ai/agent-tool/permission/${agentId}`, { params: { toolKey } })
}

export function agentToolSavePermissions(agentId, toolKey, data) {
  return request.post(`/ai/agent-tool/permission/${agentId}`, data, { params: { toolKey } })
}

export function agentToolDeletePermissions(agentId, toolKey) {
  return request.delete(`/ai/agent-tool/permission/${agentId}`, { params: { toolKey } })
}
