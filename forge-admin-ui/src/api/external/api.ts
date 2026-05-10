import { request } from '@/utils'

export interface ExternalApi {
  id?: number
  systemId: number
  apiName: string
  apiCode?: string
  apiPath: string
  method: string
  description?: string
  adapterType?: string
  adapterConfig?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface ExternalApiVO extends ExternalApi {
  systemName?: string
}

export function getExternalApiList() {
  return request.get('/external/api/list')
}

export function getExternalApiPage(params: { pageNum: number; pageSize: number; systemId?: number; apiName?: string }) {
  return request.get('/external/api/page', { params })
}

export function getExternalApiById(id: number) {
  return request.get(`/external/api/${id}`)
}

export function createExternalApi(data: ExternalApi) {
  return request.post('/external/api', data)
}

export function updateExternalApi(data: ExternalApi) {
  return request.put('/external/api', data)
}

export function deleteExternalApi(id: number) {
  return request.delete(`/external/api/${id}`)
}