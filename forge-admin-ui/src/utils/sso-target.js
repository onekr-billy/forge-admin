import { isExternal } from '@/utils'

export const SSO_BRIDGE_ROUTE = '/report/design'
export const DEFAULT_SSO_TARGET_CLIENT = 'forge_report'
export const DEFAULT_REPORT_BASE_URL = 'http://localhost:3021/forge-report'

const DEFAULT_SSO_ENTRY_MAP = {
  [DEFAULT_SSO_TARGET_CLIENT]: '/project/items',
}

function trimString(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function parseEnvJsonMap(rawValue) {
  const value = trimString(rawValue)
  if (!value) {
    return {}
  }

  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  }
  catch (error) {
    console.warn('[SSO] VITE_SSO_TARGET_BASE_URLS 解析失败:', error)
    return {}
  }
}

export function normalizeSsoBaseUrl(value) {
  return trimString(value).replace(/\/+$/, '')
}

export function getDefaultSsoRedirectPath(targetClient = DEFAULT_SSO_TARGET_CLIENT) {
  return DEFAULT_SSO_ENTRY_MAP[targetClient] || '/'
}

export function normalizeSsoRedirectPath(value, targetClient = DEFAULT_SSO_TARGET_CLIENT) {
  const raw = trimString(value)
  if (!raw || raw.includes('://') || raw.startsWith('//')) {
    return getDefaultSsoRedirectPath(targetClient)
  }
  return raw.startsWith('/') ? raw : `/${raw}`
}

export function parseExternalSsoTarget(rawTarget, targetClient = DEFAULT_SSO_TARGET_CLIENT) {
  const target = trimString(rawTarget)
  if (!target || !isExternal(target)) {
    return null
  }

  try {
    const targetUrl = new URL(target)
    const baseUrl = normalizeSsoBaseUrl(`${targetUrl.origin}${targetUrl.pathname}`)
    let redirectPath = ''

    if (targetUrl.hash) {
      const hashValue = targetUrl.hash.replace(/^#/, '')
      if (hashValue.startsWith('/')) {
        const [hashPath] = hashValue.split('?')
        redirectPath = hashPath
      }
    }

    return {
      baseUrl,
      redirectPath: normalizeSsoRedirectPath(redirectPath, targetClient),
    }
  }
  catch {
    return null
  }
}

export function getConfiguredSsoTargetBaseUrls() {
  const envMap = parseEnvJsonMap(import.meta.env.VITE_SSO_TARGET_BASE_URLS)
  const normalizedMap = Object.entries(envMap).reduce((acc, [key, value]) => {
    const normalizedValue = normalizeSsoBaseUrl(value)
    if (normalizedValue) {
      acc[key] = normalizedValue
    }
    return acc
  }, {})

  const legacyReportBaseUrl = normalizeSsoBaseUrl(
    import.meta.env.VITE_REPORT_UI_BASE_URL || DEFAULT_REPORT_BASE_URL,
  )

  if (legacyReportBaseUrl && !normalizedMap[DEFAULT_SSO_TARGET_CLIENT]) {
    normalizedMap[DEFAULT_SSO_TARGET_CLIENT] = legacyReportBaseUrl
  }

  return normalizedMap
}

export function resolveSsoTargetBaseUrl({ targetClient = DEFAULT_SSO_TARGET_CLIENT, preferredBaseUrl = '' } = {}) {
  const normalizedPreferredBaseUrl = normalizeSsoBaseUrl(preferredBaseUrl)
  if (normalizedPreferredBaseUrl) {
    return normalizedPreferredBaseUrl
  }

  const configuredBaseUrls = getConfiguredSsoTargetBaseUrls()
  return configuredBaseUrls[targetClient] || ''
}
