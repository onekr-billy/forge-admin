import { resolveStaticUrl } from './assets.js'

const MANAGED_FILE_ID = /^[\w-]{8,128}$/

export function resolveTenantLogoUrl(config = {}) {
  const value = String(config?.systemLogo || '').trim()
  if (!value) return resolveStaticUrl('/static/logo.png')
  if (/^(https?:|data:|blob:)/i.test(value)) return value
  if (value.startsWith('/') && !value.includes('/api/file/')) return value
  const tenantId = config?.tenantId || config?.id
  if (!tenantId) return resolveStaticUrl('/static/logo.png')
  const fileId = value.includes('/api/file/') ? value.split('/').pop()?.split('?')[0] : value
  if (!MANAGED_FILE_ID.test(String(fileId || ''))) return resolveStaticUrl('/static/logo.png')
  const prefix = import.meta.env.VITE_REQUEST_PREFIX || ''
  return `${prefix}/auth/tenant/assets/${encodeURIComponent(String(tenantId))}/logo?v=${encodeURIComponent(fileId)}`
}
