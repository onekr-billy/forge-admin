import { request } from '@/utils'

export function skillPage(params) {
  return request.get('/ai/skill/page', { params })
}

export function skillGetById(id) {
  return request.get(`/ai/skill/${id}`)
}

export function skillGetFiles(id) {
  return request.get(`/ai/skill/${id}/files`)
}

export function skillGetAgentSkills(agentId) {
  return request.get(`/ai/skill/agent/${agentId}`)
}

export function skillAddAgentSkill(data) {
  return request.post(`/ai/skill/agent/${data.agentId}`, { skillId: data.skillId })
}

export function skillDeleteAgentSkill(agentId, skillId) {
  return request.delete(`/ai/skill/agent/${agentId}/${skillId}`)
}

export function skillAdd(data) {
  return request.post('/ai/skill', data)
}

export function skillUpdate(data) {
  return request.put('/ai/skill', data)
}

export function skillDelete(id) {
  return request.delete(`/ai/skill/${id}`)
}

export function skillUploadZip(formData) {
  return request.post('/ai/skill/upload-zip', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function skillAiGenerate(description) {
  return request.post('/ai/skill/ai-generate', null, { params: { description } })
}

export function skillAiOptimize(id, instruction) {
  return request.post(`/ai/skill/${id}/ai-optimize`, null, { params: { instruction } })
}
