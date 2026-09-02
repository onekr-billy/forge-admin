import { describe, expect, it, vi } from 'vitest'
import { stripNullRequestBody } from '../empty-body'

describe('stripNullRequestBody', () => {
  it('converts null data to undefined and clears content-type', () => {
    const config = stripNullRequestBody({
      data: null,
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8' },
    })
    expect(config.data).toBeUndefined()
    expect(config.headers['Content-Type']).toBeUndefined()
  })

  it('uses AxiosHeaders.setContentType(false) when available', () => {
    const headers = { setContentType: vi.fn() }
    stripNullRequestBody({ data: null, headers })
    expect(headers.setContentType).toHaveBeenCalledWith(false, false)
  })

  it('keeps object and undefined bodies', () => {
    const payload = { id: 1 }
    expect(stripNullRequestBody({ data: payload }).data).toBe(payload)
    expect(stripNullRequestBody({ data: undefined }).data).toBeUndefined()
  })
})
