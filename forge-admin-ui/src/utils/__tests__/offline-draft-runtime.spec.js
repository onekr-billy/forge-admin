import { describe, expect, it } from 'vitest'
import {
  buildOfflineDraftNamespace,
  createOfflineDraftStore,
  detectDraftConflict,
  OFFLINE_DRAFT_STATUS,
  OFFLINE_REPLAY_STATUS,
} from '../offline-draft-runtime'

function createMemoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) || null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
  }
}

function createDraft(store, overrides = {}) {
  return store.saveDraft({
    draftId: 'draft-1',
    applicationCode: 'presale',
    objectCode: 'order',
    formCode: 'create',
    publishedVersion: 'v1',
    schemaHash: 'sha-1',
    recordId: 'record-1',
    baseRecordVersion: 'r1',
    data: { mobile: '13800000000', token: 'must-not-persist', nested: { userId: 'drop' } },
    ...overrides,
  })
}

describe('offline draft storage and sanitization', () => {
  it('builds an isolated namespace from tenant user application object and form', () => {
    expect(buildOfflineDraftNamespace({
      tenantId: 1,
      userId: 9,
      applicationCode: 'presale',
      objectCode: 'order',
      formCode: 'create',
    })).toBe('forge-lowcode:1:9:presale:order:create')
    expect(() => buildOfflineDraftNamespace({ tenantId: 1 })).toThrowError('草稿作用域缺少userId')
  })

  it('persists a bounded draft while removing credentials and browser identity fields', () => {
    const store = createOfflineDraftStore({ storage: createMemoryStorage(), now: () => 100 })
    const draft = createDraft(store)

    expect(draft).toMatchObject({
      draftId: 'draft-1',
      publishedVersion: 'v1',
      schemaHash: 'sha-1',
      status: OFFLINE_DRAFT_STATUS.DRAFT,
    })
    expect(draft.data).toEqual({ mobile: '13800000000', nested: {} })
    expect(store.getDraft('draft-1')).toEqual(draft)
  })

  it('fails closed for circular or oversized data and enforces draft count', () => {
    const storage = createMemoryStorage()
    const store = createOfflineDraftStore({ storage, maxDrafts: 1, maxBytes: 4096 })
    const circular = {}
    circular.self = circular
    expect(() => store.saveDraft({ draftId: 'bad', data: circular })).toThrowError('草稿数据不可保存')
    expect(() => store.saveDraft({ draftId: 'large', data: { value: 'x'.repeat(5000) } })).toThrowError('草稿超过本地存储上限')

    let timestamp = 1
    const boundedStore = createOfflineDraftStore({ storage, maxDrafts: 1, now: () => timestamp++ })
    boundedStore.saveDraft({ draftId: 'first', data: {} })
    boundedStore.saveDraft({ draftId: 'second', data: {} })
    expect(boundedStore.listDrafts().map(item => item.draftId)).toEqual(['second'])
  })
})

describe('offline replay governance', () => {
  it('deduplicates idempotency keys and replays each pending intent once', async () => {
    const store = createOfflineDraftStore({ storage: createMemoryStorage() })
    createDraft(store)
    const first = store.appendReplayIntent('draft-1', {
      actionCode: 'save_order',
      objectCode: 'order',
      recordId: 'record-1',
      formData: { amount: 100, tenantId: 'spoofed' },
      idempotencyKey: 'ui:retry-1',
    })
    const duplicate = store.appendReplayIntent('draft-1', {
      actionCode: 'save_order',
      objectCode: 'order',
      idempotencyKey: 'ui:retry-1',
      formData: { amount: 999 },
    })
    expect(duplicate).toEqual(first)

    const execute = []
    const result = await store.replayDraft('draft-1', {
      confirmed: true,
      loadCurrent: async () => ({ available: true, publishedVersion: 'v1', recordVersion: 'r1' }),
      execute: async intent => execute.push(intent),
    })
    expect(result.status).toBe('completed')
    expect(execute).toHaveLength(1)
    expect(execute[0].formData).toEqual({ amount: 100 })
    expect(store.getDraft('draft-1').replayLog[0].status).toBe(OFFLINE_REPLAY_STATUS.COMPLETED)
  })

  it('stops on the first execution failure and never skips the remaining intent', async () => {
    const store = createOfflineDraftStore({ storage: createMemoryStorage() })
    createDraft(store)
    store.appendReplayIntent('draft-1', { actionCode: 'one', objectCode: 'order', idempotencyKey: 'ui:retry-1' })
    store.appendReplayIntent('draft-1', { actionCode: 'two', objectCode: 'order', idempotencyKey: 'ui:retry-2' })
    const execute = []
    const result = await store.replayDraft('draft-1', {
      confirmed: true,
      loadCurrent: async () => ({ available: true, publishedVersion: 'v1', recordVersion: 'r1' }),
      execute: async (intent) => {
        execute.push(intent.actionCode)
        throw new Error('network')
      },
    })
    expect(result).toMatchObject({ status: 'failed', code: 'DRAFT_REPLAY_FAILED' })
    expect(execute).toEqual(['one'])
    expect(store.getDraft('draft-1').status).toBe(OFFLINE_DRAFT_STATUS.REPLAY_FAILED)
    expect(store.getDraft('draft-1').replayLog[1].status).toBe(OFFLINE_REPLAY_STATUS.PENDING)
  })

  it('fails before side effects when release or record versions conflict', async () => {
    const store = createOfflineDraftStore({ storage: createMemoryStorage() })
    createDraft(store)
    store.appendReplayIntent('draft-1', { actionCode: 'save_order', objectCode: 'order', idempotencyKey: 'ui:retry-1' })
    const execute = []
    const result = await store.replayDraft('draft-1', {
      confirmed: true,
      loadCurrent: async () => ({ available: true, publishedVersion: 'v2', recordVersion: 'r2' }),
      execute: async intent => execute.push(intent),
    })
    expect(result).toMatchObject({ status: 'conflict', code: 'PUBLISHED_VERSION_CONFLICT' })
    expect(execute).toEqual([])
    expect(detectDraftConflict(createDraft(store), { available: true, publishedVersion: 'v1', recordVersion: 'r2' }))
      .toMatchObject({ code: 'RECORD_VERSION_CONFLICT' })
  })

  it('requires explicit confirmation and checks schema changes before side effects', async () => {
    const store = createOfflineDraftStore({ storage: createMemoryStorage() })
    createDraft(store)
    store.appendReplayIntent('draft-1', { actionCode: 'save_order', objectCode: 'order', idempotencyKey: 'ui:retry-1' })
    const execute = []
    const confirmation = await store.replayDraft('draft-1', {
      loadCurrent: async () => ({ available: true }),
      execute: async intent => execute.push(intent),
    })
    expect(confirmation).toMatchObject({
      status: 'confirmation_required',
      code: 'DRAFT_REPLAY_CONFIRMATION_REQUIRED',
    })
    const conflict = await store.replayDraft('draft-1', {
      confirmed: true,
      loadCurrent: async () => ({
        available: true,
        publishedVersion: 'v1',
        schemaHash: 'sha-2',
        recordVersion: 'r1',
      }),
      execute: async intent => execute.push(intent),
    })
    expect(conflict).toMatchObject({ status: 'conflict', code: 'SCHEMA_CONFLICT' })
    expect(execute).toEqual([])
  })
})
