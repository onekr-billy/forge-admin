import { request } from '@/utils'
import { postEncrypt } from '@/utils/encrypt-request'

const ENCRYPTED = { encrypt: true }

export function dataAuditRecordPage(objectId, recordId, params) {
  return request.get(`/ai/data-audit/record/${objectId}/${recordId}/page`, { params, ...ENCRYPTED })
}

export function dataAuditAdminPage(params) {
  return request.get('/ai/data-audit/page', { params, ...ENCRYPTED })
}

export function dataAuditFilterOptions() {
  return request.get('/ai/data-audit/filter-options', ENCRYPTED)
}

export function dataAuditObjectFieldOptions(objectId) {
  return request.get(`/ai/data-audit/filter-options/objects/${objectId}/fields`, ENCRYPTED)
}

export function dataAuditEventDetail(eventId, params) {
  return request.get(`/ai/data-audit/${eventId}`, { params, ...ENCRYPTED })
}

export function dataAuditFieldPage(eventId, params) {
  return request.get(`/ai/data-audit/${eventId}/fields/page`, { params, ...ENCRYPTED })
}

export function dataAuditReveal(eventId, fieldId, data) {
  return postEncrypt(`/ai/data-audit/${eventId}/fields/${fieldId}/reveal`, data)
}

export function dataAuditPolicy(objectId) {
  return request.get(`/ai/data-audit/policy/${objectId}`, ENCRYPTED)
}

export function updateDataAuditPolicy(objectId, data) {
  return request.put(`/ai/data-audit/policy/${objectId}`, data, ENCRYPTED)
}

export function dataAuditScope(roleId) {
  return request.get(`/ai/data-audit/scope/${roleId}`, ENCRYPTED)
}

export function updateDataAuditScope(roleId, data) {
  return request.put(`/ai/data-audit/scope/${roleId}`, data, ENCRYPTED)
}

export function dataAuditRemove(configKey, data) {
  return postEncrypt(`/ai/crud/${configKey}/remove`, data, {
    globalLoading: false,
  })
}
