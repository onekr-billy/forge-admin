import { webcrypto } from 'node:crypto'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { createFlowActionCredentials } from '../flow-action-idempotency'

beforeEach(() => {
  vi.stubGlobal('crypto', webcrypto)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('flow action idempotency credentials', () => {
  it('reuses the same command identity for an identical retry', async () => {
    const payload = {
      comment: '同意',
      variables: { approved: true },
      data: { amount: 1200 },
    }

    const first = await createFlowActionCredentials('approve', 'task-42', payload)
    const retry = await createFlowActionCredentials('approve', 'task-42', payload)

    expect(retry).toEqual(first)
    expect(first.idempotencyKey).toBe(`flow:${first.requestDigest}`)
    expect(first.requestDigest).toMatch(/^[0-9a-f]{64}$/)
  })

  it('canonicalizes object key order without changing array order', async () => {
    const first = await createFlowActionCredentials('approve', 'task-42', {
      variables: { b: 2, a: 1 },
      approvalPointResults: [{ code: 'risk', checked: true }],
    })
    const reordered = await createFlowActionCredentials('approve', 'task-42', {
      approvalPointResults: [{ checked: true, code: 'risk' }],
      variables: { a: 1, b: 2 },
    })
    const changedArray = await createFlowActionCredentials('approve', 'task-42', {
      approvalPointResults: [{ checked: true, code: 'risk' }, { checked: true, code: 'amount' }],
      variables: { a: 1, b: 2 },
    })

    expect(reordered).toEqual(first)
    expect(changedArray.idempotencyKey).not.toBe(first.idempotencyKey)
  })

  it('changes identity when the action or payload changes', async () => {
    const approved = await createFlowActionCredentials('approve', 'task-42', { comment: '同意' })
    const rejected = await createFlowActionCredentials('reject', 'task-42', { comment: '同意' })
    const changedComment = await createFlowActionCredentials('approve', 'task-42', { comment: '复核同意' })

    expect(rejected.idempotencyKey).not.toBe(approved.idempotencyKey)
    expect(changedComment.idempotencyKey).not.toBe(approved.idempotencyKey)
  })

  it('fails closed when Web Crypto is unavailable', async () => {
    vi.stubGlobal('crypto', undefined)

    await expect(createFlowActionCredentials('approve', 'task-42', { comment: '同意' }))
      .rejects
      .toThrow('SHA-256')
  })
})
