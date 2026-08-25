import { postEncrypt, request } from '@/utils'

export function modelPage(params) {
  return request.get('/ai/model/page', { params })
}

export function modelListByProvider(providerId) {
  return request.get('/ai/model/list', { params: { providerId } })
}

export function modelGetById(id) {
  return request.get(`/ai/model/${id}`)
}

export function modelAdd(data) {
  return request.post('/ai/model', data)
}

export function modelUpdate(data) {
  return request.put('/ai/model', data)
}

export function modelDelete(id) {
  return request.delete(`/ai/model/${id}`)
}

export function modelTest(id) {
  return request.post(`/ai/model/${id}/test`)
}

export function routePolicyPage(params) {
  return request.get('/ai/model-routing/policy/page', { params })
}

export function routePolicyGet(id) {
  return request.get(`/ai/model-routing/policy/${id}`)
}

export function routePolicyAdd(data) {
  return postEncrypt('/ai/model-routing/policy', data)
}

export function routePolicyUpdate(data) {
  return request.put('/ai/model-routing/policy', data, { encrypt: true })
}

export function routePolicyDelete(id) {
  return request.delete(`/ai/model-routing/policy/${id}`)
}

export function routePolicyPreview(data) {
  return postEncrypt('/ai/model-routing/policy/preview', data)
}

export function invocationPage(params) {
  return request.get('/ai/model-routing/invocation/page', { params })
}

export function invocationSummary(params) {
  return request.get('/ai/model-routing/invocation/summary', { params })
}
