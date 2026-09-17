import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  isDynamicImportError,
  recoverFromDynamicImportError,
  setupDynamicImportRecovery,
} from '../dynamic-import-recovery'

function createStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  }
}

describe('dynamic import recovery', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('recognizes browser dynamic import errors', () => {
    expect(isDynamicImportError(new TypeError('Failed to fetch dynamically imported module: /forge/assets/page-old.js'))).toBe(true)
    expect(isDynamicImportError(new Error('ordinary request failed'))).toBe(false)
  })

  it('reloads only once during the cooldown window', () => {
    const storage = createStorage()
    const reload = vi.fn()
    const error = new TypeError('Failed to fetch dynamically imported module: /forge/assets/page-old.js')

    expect(recoverFromDynamicImportError(error, { storage, reload, now: () => 100_000 })).toBe(true)
    expect(recoverFromDynamicImportError(error, { storage, reload, now: () => 100_001 })).toBe(false)
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('does not reload when the cooldown marker cannot be persisted', () => {
    const reload = vi.fn()
    const error = new TypeError('Failed to fetch dynamically imported module: /forge/assets/page-old.js')

    expect(recoverFromDynamicImportError(error, { storage: null, reload })).toBe(false)
    expect(reload).not.toHaveBeenCalled()
  })

  it('prevents Vite from rethrowing when recovery starts', () => {
    const reload = vi.fn()
    const eventTarget = new EventTarget()
    vi.stubGlobal('sessionStorage', createStorage())
    vi.stubGlobal('location', { reload })
    setupDynamicImportRecovery(eventTarget)

    const event = new Event('vite:preloadError', { cancelable: true })
    event.payload = new TypeError('Failed to fetch dynamically imported module: /forge/assets/page-old.js')
    eventTarget.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
