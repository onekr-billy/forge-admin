import { request } from '@/utils'

export interface ExternalSystem {
  id?: number
  systemName: string
  systemCode?: string
  baseUrl: string
  authType: string
  authConfig?: string
  description?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export function getExternalSystemList() {
  return request.get('/external/system/list')
}

export function getExternalSystemPage(params: { pageNum: number; pageSize: number; systemName?: string }) {
  return request.get('/external/system/page', { params })
}

export function getExternalSystemById(id: number) {
  return request.get(`/external/system/${id}`)
}

export function createExternalSystem(data: ExternalSystem) {
  return request.post('/external/system', data)
}

export function updateExternalSystem(data: ExternalSystem) {
  return request.put('/external/system', data)
}

export function deleteExternalSystem(id: number) {
  return request.delete(`/external/system/${id}`)
}