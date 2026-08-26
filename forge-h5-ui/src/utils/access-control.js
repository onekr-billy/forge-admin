import { DEFAULT_PAGE_ACCESS, PAGE_ACCESS_RULES } from '@/config/page-access'

export function normalizeRoutePath(url = '') {
  const raw = String(url || '').trim()
  if (!raw) {
    return ''
  }
  const path = raw.split('?')[0].split('#')[0]
  return path.startsWith('/') ? path : `/${path}`
}

export function resolvePageAccess(url = '') {
  const path = normalizeRoutePath(url)
  const rule = PAGE_ACCESS_RULES.find(item => item.path === path)
  if (rule) {
    return {
      ...DEFAULT_PAGE_ACCESS,
      ...rule,
      path,
    }
  }

  return {
    ...DEFAULT_PAGE_ACCESS,
    path,
    public: false,
    requiresLogin: true,
  }
}

export function hasPermission(permissions = [], required = '', mode = 'any') {
  const requiredList = Array.isArray(required) ? required : [required]
  const expected = requiredList
    .map(item => String(item || '').trim())
    .filter(Boolean)
  if (!expected.length) {
    return true
  }

  const grantedList = (Array.isArray(permissions) ? permissions : [])
    .map(item => String(item || '').trim())
    .filter(Boolean)
  if (!grantedList.length) {
    return false
  }

  if (mode === 'all') {
    return expected.every(requiredItem => grantedList.some(granted => permissionPatternMatches(granted, requiredItem)))
  }

  return expected.some(requiredItem => grantedList.some(granted => permissionPatternMatches(granted, requiredItem)))
}

function permissionPatternMatches(granted, required) {
  if (granted === '**' || granted === '*:*:*' || granted === required) {
    return true
  }
  if (!granted.includes('*')) {
    return false
  }
  const escaped = granted.replace(/[.+?^${}()|[\]\\]/g, '\\$&')
  const pattern = escaped.replace(/\*\*/g, '.*').replace(/\*/g, '[^:]*')
  return new RegExp(`^${pattern}$`).test(required)
}

