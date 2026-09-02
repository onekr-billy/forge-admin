import { defaultThemeConfig } from '@/config/theme.config'
import { useTenantStore } from '@/store'
import { resolveRenderableFileUrl } from '@/utils/file'
import { normalizePageTitle, setDocumentTitle } from '@/utils/page-title'

const MANAGED_FILE_ID_PATTERN = /^[\w-]{8,128}$/

function getRequestPrefix() {
  return import.meta.env.VITE_REQUEST_PREFIX || ''
}

function normalizeTenantId(value) {
  if (Array.isArray(value)) {
    const first = value.find(item => item !== null && item !== undefined && item !== '')
    return normalizeTenantId(first)
  }
  if (value === null || value === undefined || value === '')
    return null
  const parsed = Number(value)
  return Number.isNaN(parsed) ? null : parsed
}

function isDirectRenderableReference(value = '') {
  const text = String(value || '').trim().toLowerCase()
  return text.startsWith('http://')
    || text.startsWith('https://')
    || text.startsWith('data:')
    || text.startsWith('blob:')
}

function isManagedFileReference(value = '') {
  const text = String(value || '').trim()
  if (!text)
    return false
  if (text.includes('/api/file/download/') || text.includes('/api/file/url/'))
    return true
  return !text.startsWith('/') && !text.includes('/') && !text.includes('\\') && MANAGED_FILE_ID_PATTERN.test(text)
}

function extractManagedFileId(value = '') {
  const text = String(value || '').trim()
  if (!text)
    return ''

  const normalizedText = text.toLowerCase()
  let fileId = ''

  if (normalizedText.includes('/api/file/download/')) {
    fileId = text.split('/api/file/download/').pop() || ''
  }
  else if (normalizedText.includes('/api/file/url/')) {
    fileId = text.split('/api/file/url/').pop() || ''
  }
  else if (!text.startsWith('/') && !text.includes('/') && !text.includes('\\')) {
    fileId = text
  }

  if (!fileId)
    return ''

  const queryIndex = fileId.indexOf('?')
  if (queryIndex >= 0)
    fileId = fileId.slice(0, queryIndex)

  const hashIndex = fileId.indexOf('#')
  if (hashIndex >= 0)
    fileId = fileId.slice(0, hashIndex)

  return MANAGED_FILE_ID_PATTERN.test(fileId) ? fileId : ''
}

function resolveAssetReference(tenantConfig, assetType) {
  if (!tenantConfig)
    return ''
  if (assetType === 'logo')
    return tenantConfig.systemLogo || ''
  return tenantConfig.browserIcon || ''
}

function buildAssetCacheBuster(assetReference) {
  const fileId = extractManagedFileId(assetReference)
  return fileId ? `?v=${encodeURIComponent(fileId)}` : ''
}

export function resolveTenantPublicAssetUrl(tenantConfig, assetType = 'logo') {
  const assetReference = String(resolveAssetReference(tenantConfig, assetType) || '').trim()
  if (!assetReference)
    return ''
  if (isDirectRenderableReference(assetReference))
    return assetReference
  if (assetReference.startsWith('/') && !isManagedFileReference(assetReference))
    return assetReference

  const tenantId = normalizeTenantId(tenantConfig?.tenantId ?? tenantConfig?.id)
  if (!tenantId || !isManagedFileReference(assetReference))
    return ''

  const normalizedAssetType = assetType === 'favicon' ? 'icon' : assetType
  return `${getRequestPrefix()}/auth/tenant/assets/${tenantId}/${normalizedAssetType}${buildAssetCacheBuster(assetReference)}`
}

export function setDocumentFavicon(iconUrl = '/favicon.ico') {
  const head = document.head || document.getElementsByTagName('head')[0]
  if (!head)
    return

  document.querySelectorAll("link[rel*='icon']").forEach(link => link.remove())

  const link = document.createElement('link')
  link.type = 'image/x-icon'
  link.rel = 'shortcut icon'
  link.href = iconUrl || '/favicon.ico'
  head.appendChild(link)
}

function parseThemeConfig(tenantConfig, tenantStore) {
  const rawThemeConfig = tenantConfig?.themeConfig
  if (rawThemeConfig) {
    try {
      return typeof rawThemeConfig === 'string' ? JSON.parse(rawThemeConfig) : rawThemeConfig
    }
    catch (error) {
      console.error('解析主题配置失败:', error)
    }
  }
  return tenantStore.themeConfig
}

export async function applyTenantConfig(tenantConfig, appStore) {
  const tenantStore = useTenantStore()

  appStore.resetAccountState()
  if (!tenantConfig)
    return

  if (tenantConfig.systemLayout) {
    appStore.setLayout(tenantConfig.systemLayout)
  }

  const themeConfigObj = parseThemeConfig(tenantConfig, tenantStore)
  if (themeConfigObj) {
    const primaryColor = tenantConfig.systemTheme || themeConfigObj.primaryColor || defaultThemeConfig.primaryColor
    appStore.setThemeConfig({
      primaryColor,
      header: {
        ...defaultThemeConfig.header,
        ...themeConfigObj.header,
      },
      headerDark: {
        ...defaultThemeConfig.headerDark,
        ...themeConfigObj.headerDark,
      },
      topMenu: {
        ...defaultThemeConfig.topMenu,
        ...themeConfigObj.topMenu,
      },
      topMenuDark: {
        ...defaultThemeConfig.topMenuDark,
        ...themeConfigObj.topMenuDark,
      },
      sideMenu: {
        ...defaultThemeConfig.sideMenu,
        ...themeConfigObj.sideMenu,
      },
      sideMenuDark: {
        ...defaultThemeConfig.sideMenuDark,
        ...themeConfigObj.sideMenuDark,
      },
    })
  }
  else if (tenantConfig.systemTheme) {
    appStore.setPrimaryColor(tenantConfig.systemTheme)
    appStore.setThemeColor(tenantConfig.systemTheme)
  }

  const pageBaseTitle = normalizePageTitle(tenantConfig.browserTitle) || normalizePageTitle(tenantConfig.systemName)
  if (pageBaseTitle) {
    setDocumentTitle(pageBaseTitle)
  }

  let iconUrl = ''
  if (tenantConfig.browserIcon) {
    try {
      iconUrl = resolveTenantPublicAssetUrl(tenantConfig, 'icon')
      if (!iconUrl)
        iconUrl = await resolveRenderableFileUrl(tenantConfig.browserIcon)
    }
    catch {
      iconUrl = resolveTenantPublicAssetUrl(tenantConfig, 'icon') || tenantConfig.browserIcon
    }
  }
  setDocumentFavicon(iconUrl || tenantConfig.browserIcon)
}
