import { request } from '@/utils'

export function storeInstancePage(params) {
  return request.get('/ai/store/page', { params })
}

export function storeInstanceGetById(id) {
  return request.get(`/ai/store/${id}`)
}

export function storeInstanceCreate(data) {
  return request.post('/ai/store', data)
}

export function storeInstanceUpdate(data) {
  return request.put('/ai/store', data)
}

export function storeInstanceDelete(id) {
  return request.delete(`/ai/store/${id}`)
}

export function storeInstanceTest(id) {
  return request.post(`/ai/store/${id}/test`)
}
