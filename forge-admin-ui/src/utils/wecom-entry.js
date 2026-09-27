const ENTRY_KEY = 'forge.wecom.application-entry'

export function readWeComConnection(location, storage) {
  const query = new URLSearchParams(location.search || '')
  const hash = new URLSearchParams((location.hash || '').split('?')[1] || '')
  const explicit = query.get('collaborationConnection') || hash.get('collaborationConnection')
  if (explicit)
    return explicit
  if (query.get('code') && query.get('state')) {
    try {
      return JSON.parse(storage.getItem(ENTRY_KEY) || '{}').connectionCode || ''
    }
    catch { return '' }
  }
  return ''
}

export function rememberWeComEntry(location, storage, connectionCode) {
  storage.setItem(ENTRY_KEY, JSON.stringify({ path: `${location.pathname}${location.search || ''}${location.hash || ''}`, connectionCode }))
}

export function consumeWeComEntry(storage) {
  try {
    const saved = JSON.parse(storage.getItem(ENTRY_KEY) || '{}')
    storage.removeItem(ENTRY_KEY)
    const path = saved.path || ''
    return path.startsWith('/') && !path.startsWith('//') && !path.includes('\\') ? path : ''
  }
  catch { return '' }
}
