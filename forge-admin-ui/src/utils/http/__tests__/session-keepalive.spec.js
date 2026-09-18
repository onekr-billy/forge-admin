import { afterEach, describe, expect, it, vi } from 'vitest'
import { maybeRefreshSession, resetSessionKeepalive } from '../session-keepalive'

describe('session keepalive', () => {
  afterEach(() => {
    resetSessionKeepalive()
    vi.useRealTimers()
  })

  it('does not refresh on the first successful request', () => {
    const post = vi.fn(() => Promise.resolve())
    maybeRefreshSession({ post }, 'token', { url: '/system/user/page' })
    expect(post).not.toHaveBeenCalled()
  })

  it('refreshes after ten minutes of continued activity', () => {
    vi.useFakeTimers()
    vi.setSystemTime(0)
    const post = vi.fn(() => Promise.resolve())
    const axiosInstance = { post }

    maybeRefreshSession(axiosInstance, 'token', { url: '/system/user/page' })
    vi.setSystemTime(10 * 60 * 1000)
    maybeRefreshSession(axiosInstance, 'token', { url: '/system/user/page' })

    expect(post).toHaveBeenCalledTimes(1)
    expect(post).toHaveBeenCalledWith('/auth/refreshToken', {}, {
      needTip: false,
      skipAuthRefresh: true,
    })
  })

  it('does not refresh login or refreshToken requests', () => {
    vi.useFakeTimers()
    vi.setSystemTime(0)
    const post = vi.fn(() => Promise.resolve())
    maybeRefreshSession({ post }, 'token', { url: '/system/user/page' })
    vi.setSystemTime(10 * 60 * 1000)
    maybeRefreshSession({ post }, 'token', { url: '/auth/refreshToken' })
    maybeRefreshSession({ post }, 'token', { url: '/auth/login', skipAuthRefresh: true })
    expect(post).not.toHaveBeenCalled()
  })
})
