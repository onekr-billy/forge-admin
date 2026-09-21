import { postEncrypt, request } from '@/utils'

const base = id => `/ai/business/application/${encodeURIComponent(id)}/integrations`
export const getApplicationCollaboration = id => request.get(`${base(id)}/collaboration`)
export const getApplicationConnections = id => request.get(`${base(id)}/connections`)
export const saveApplicationCollaboration = (id, data) => request.put(`${base(id)}/collaboration`, data, { encrypt: true })
export const getApplicationCapabilities = (id, params) => request.get(`${base(id)}/capabilities`, { params })
export const publishApplicationCapability = (id, type, data) => postEncrypt(`${base(id)}/publish/${type}`, data)
export const getApplicationClients = id => request.get(`${base(id)}/clients`)
export const getApplicationGrants = (id, params) => request.get(`${base(id)}/grants`, { params })
export const grantApplicationCapability = (id, data) => postEncrypt(`${base(id)}/grants`, data)
export const revokeApplicationGrant = (id, grantId) => request.post(`${base(id)}/grants/${encodeURIComponent(grantId)}/revoke`)
export const getApplicationInvocations = (id, params) => request.get(`${base(id)}/invocations`, { params })
