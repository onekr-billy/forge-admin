import { postEncrypt, request } from '@/utils'

export function providerPage(params) {
  return request.get('/ai/provider/page', { params })
}

export function providerGetById(id) {
  return request.get(`/ai/provider/${id}`)
}

export function providerAdd(data) {
  return postEncrypt('/ai/provider', data)
}

export function providerUpdate(data) {
  return request.put('/ai/provider', data, { encrypt: true })
}

export function providerDelete(id) {
  return request.delete(`/ai/provider/${id}`)
}

export function providerTest(data) {
  return postEncrypt('/ai/provider/test', data)
}

export function providerSetDefault(id) {
  return request.put(`/ai/provider/${id}/default`)
}

export function providerTemplates() {
  return request.get('/ai/provider/templates')
}

export function providerFetchModels(id) {
  return request.post(`/ai/provider/${id}/fetch-models`)
}

export function providerBatchImportModels(id, items) {
  return request.post(`/ai/provider/${id}/models/batch`, items)
}
