import { request } from '@/utils'

export function contextConfigList(agentCode) {
  return request.get('/ai/context/list', { params: { agentCode } })
}

export function contextConfigAdd(data) {
  return request.post('/ai/context/add', data)
}

export function contextConfigUpdate(data) {
  return request.put('/ai/context/update', data)
}

export function contextConfigDelete(id) {
  return request.delete(`/ai/context/${id}`)
}
