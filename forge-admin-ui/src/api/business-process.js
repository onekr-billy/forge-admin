import { request } from '@/utils'

const ENCRYPTED_REQUEST = { encrypt: true }

function encryptedParams(params) {
  return { params, encrypt: true }
}

export function businessProcessPage(params) {
  return request.get('/ai/business/process/page', encryptedParams(params))
}

export function businessObjectProcesses(objectCode) {
  return request.get(`/ai/business/object/${encodeURIComponent(objectCode)}/processes`, ENCRYPTED_REQUEST)
}

export function businessProcessDetail(id) {
  return request.get(`/ai/business/process/${id}`, ENCRYPTED_REQUEST)
}

export function createBusinessProcess(data) {
  return request.post('/ai/business/process', data, ENCRYPTED_REQUEST)
}

export function copyBusinessProcess(id, data = {}) {
  return request.post(`/ai/business/process/${id}/copy`, data, ENCRYPTED_REQUEST)
}

export function updateBusinessProcess(data) {
  return request.put('/ai/business/process', data, ENCRYPTED_REQUEST)
}

export function businessProcessDesigner(id) {
  return request.get(`/ai/business/process/${id}/designer`, ENCRYPTED_REQUEST)
}

export function businessProcessFlowModels(id) {
  return request.get(`/ai/business/process/${id}/flow-models`, ENCRYPTED_REQUEST)
}

export function saveBusinessProcessSchema(id, data) {
  return request.put(`/ai/business/process/${id}/schema`, data, ENCRYPTED_REQUEST)
}

export function validateBusinessProcess(id) {
  return request.post(`/ai/business/process/${id}/validate`, null, ENCRYPTED_REQUEST)
}

// 独立发布：同步生成不可变流程版本并切换运行投影，不触发应用发布。
export function publishBusinessProcess(id) {
  return request.post(`/ai/business/process/${id}/publish`, null, ENCRYPTED_REQUEST)
}

export function updateBusinessProcessStatus(id, status) {
  return request.put(`/ai/business/process/${id}/status`, null, encryptedParams({ status }))
}

export function deleteBusinessProcess(id) {
  return request.delete(`/ai/business/process/${id}`, ENCRYPTED_REQUEST)
}
