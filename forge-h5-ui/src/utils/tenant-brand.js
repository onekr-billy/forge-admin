import { resolveStaticUrl } from './assets.js'

const MANAGED_FILE_ID = /^[\w-]{8,128}$/

/**
 * uni-app H5 的 <image> 会把以 / 开头的路径再拼一层 Vite base。
 * API 路径（/forge-h5-api/...）需转成 origin 绝对地址，避免变成 /forge-h5/forge-h5-api/...
 */
function toUniSafeAbsoluteUrl(path) {
  const value = String(path || '').trim()
  if (!value) {
    return ''
  }
  if (/^(https?:|data:|blob:)/i.test(value)) {
    return value
  }
  if (typeof window !== 'undefined' && window.location?.origin && value.startsWith('/')) {
    return `${window.location.origin}${value}`
  }
  return value
}

export function resolveTenantLogoUrl(config = {}) {
  const value = String(config?.systemLogo || '').trim()
  if (!value)
    return resolveStaticUrl('/static/logo.png')
  if (/^(https?:|data:|blob:)/i.test(value))
    return value
  if (value.startsWith('/') && !value.includes('/api/file/'))
    return resolveStaticUrl(value)

  const tenantId = config?.tenantId || config?.id
  if (!tenantId)
    return resolveStaticUrl('/static/logo.png')

  const fileId = value.includes('/api/file/') ? value.split('/').pop()?.split('?')[0] : value
  if (!MANAGED_FILE_ID.test(String(fileId || '')))
    return resolveStaticUrl('/static/logo.png')

  const prefix = import.meta.env.VITE_REQUEST_PREFIX || ''
  return toUniSafeAbsoluteUrl(
    `${prefix}/auth/tenant/assets/${encodeURIComponent(String(tenantId))}/logo?v=${encodeURIComponent(fileId)}`,
  )
}
