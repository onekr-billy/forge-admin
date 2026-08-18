export const DEFAULT_PORTAL_CONFIG = Object.freeze({
  themeColor: '#3370ff',
  navigation: {
    style: 'side',
    showLogo: true,
    showName: true,
    collapsible: true,
    collapsed: false,
  },
  watermark: {
    enabled: false,
    text: '',
    showUsername: true,
    showTime: false,
    scope: 'content',
  },
  permission: {
    visibility: 'all',
    administrators: [],
    roleIds: [],
    departmentIds: [],
    userIds: [],
  },
  globalization: {
    enabled: false,
    defaultLanguage: 'zh-CN',
    timezone: 'Asia/Shanghai',
    dateFormat: 'YYYY-MM-DD',
  },
  advanced: {
    codePrefix: '',
    cachePolicy: 'version',
    versionRetention: 20,
  },
  distribution: {
    workbench: false,
    roleIds: [],
    h5Enabled: true,
  },
})

export function parseJsonObject(value, fallback = {}) {
  if (value && typeof value === 'object' && !Array.isArray(value))
    return clone(value)
  if (typeof value !== 'string' || !value.trim())
    return clone(fallback)
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : clone(fallback)
  }
  catch {
    return clone(fallback)
  }
}

export function normalizePortalConfig(value) {
  const source = parseJsonObject(value)
  return {
    ...clone(DEFAULT_PORTAL_CONFIG),
    ...source,
    navigation: { ...DEFAULT_PORTAL_CONFIG.navigation, ...(source.navigation || {}) },
    watermark: { ...DEFAULT_PORTAL_CONFIG.watermark, ...(source.watermark || {}) },
    permission: { ...DEFAULT_PORTAL_CONFIG.permission, ...(source.permission || {}) },
    globalization: { ...DEFAULT_PORTAL_CONFIG.globalization, ...(source.globalization || {}) },
    advanced: { ...DEFAULT_PORTAL_CONFIG.advanced, ...(source.advanced || {}) },
    distribution: { ...DEFAULT_PORTAL_CONFIG.distribution, ...(source.distribution || {}) },
  }
}

export function buildPortalWatermarkText(config, userName, now = new Date()) {
  const normalized = normalizePortalConfig(config)
  const watermark = normalized.watermark
  if (!watermark.enabled)
    return ''
  return [
    String(watermark.text || '').trim(),
    watermark.showUsername ? String(userName || '').trim() : '',
    watermark.showTime ? formatWatermarkTime(now, normalized.globalization) : '',
  ].filter(Boolean).join(' · ').slice(0, 50)
}

export function buildPortalWatermarkStyle(text, color = '#64748b') {
  if (!text)
    return {}
  const safeText = escapeXml(text)
  const safeColor = /^#[0-9a-f]{6}$/i.test(color) ? color : '#64748b'
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="260" height="180"><text x="20" y="100" fill="${safeColor}" fill-opacity="0.13" font-family="Arial,sans-serif" font-size="14" transform="rotate(-24 130 90)">${safeText}</text></svg>`
  return { backgroundImage: `url("data:image/svg+xml,${encodeURIComponent(svg)}")` }
}

function formatWatermarkTime(value, globalization = {}) {
  const timezone = String(globalization.timezone || 'Asia/Shanghai')
  const dateFormat = String(globalization.dateFormat || 'YYYY-MM-DD')
  let parts
  try {
    parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(value).map(part => [part.type, part.value]))
  }
  catch {
    parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
      timeZone: 'UTC',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(value).map(part => [part.type, part.value]))
  }
  const date = dateFormat === 'DD/MM/YYYY'
    ? `${parts.day}/${parts.month}/${parts.year}`
    : dateFormat === 'YYYY/MM/DD'
      ? `${parts.year}/${parts.month}/${parts.day}`
      : `${parts.year}-${parts.month}-${parts.day}`
  return `${date} ${parts.hour}:${parts.minute}`
}

function escapeXml(value) {
  return String(value).replace(/[&<>'"]/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    [String.fromCharCode(39)]: '&apos;',
    [String.fromCharCode(34)]: '&quot;',
  })[character])
}

function clone(value) {
  return JSON.parse(JSON.stringify(value || {}))
}
