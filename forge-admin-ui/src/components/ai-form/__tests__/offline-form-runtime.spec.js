import { describe, expect, it } from 'vitest'
import {
  createOfflineFormRuntime,
  createOfflinePublishedSnapshot,
  createOfflineSchemaHash,
  normalizeOfflineFormConfig,
} from '../offline-form-runtime'

function createMemoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) || null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
  }
}

describe('offline form runtime', () => {
  it('isolates drafts and restores only the matching record', () => {
    const runtime = createOfflineFormRuntime({
      config: {
        enabled: true,
        applicationCode: 'presale',
        objectCode: 'order',
        formCode: 'main',
        publishedVersion: 3,
        schemaHash: 'schema-3',
      },
      scope: { tenantId: 1, userId: 9 },
      storage: createMemoryStorage(),
      now: () => 100,
    })

    runtime.save({ draftId: 'draft-new', recordId: '', data: { memberName: '张三' } })
    runtime.save({ draftId: 'draft-edit', recordId: 10, baseRecordVersion: 'r1', data: { memberName: '李四' } })

    expect(runtime.namespace).toBe('forge-lowcode:1:9:presale:order:main')
    expect(runtime.findDraft(10)).toMatchObject({ draftId: 'draft-edit', baseRecordVersion: 'r1' })
    expect(runtime.findDraft('')).toMatchObject({ draftId: 'draft-new' })
  })

  it('creates replay intents only for an explicitly configured business action', () => {
    const storage = createMemoryStorage()
    const disabledReplay = createOfflineFormRuntime({
      config: { enabled: true, applicationCode: 'presale', objectCode: 'order', formCode: 'main' },
      scope: { tenantId: 1, userId: 9 },
      storage,
    })
    const draft = disabledReplay.save({ draftId: 'draft-new', data: {} })
    expect(disabledReplay.appendSubmitIntent(draft.draftId, {
      formData: { amount: 10 },
      idempotencyKey: 'ui:offline-1',
    })).toBeNull()

    const replay = createOfflineFormRuntime({
      config: {
        enabled: true,
        applicationCode: 'presale',
        objectCode: 'order',
        formCode: 'submit',
        replayActionCode: 'submit_order',
      },
      scope: { tenantId: 1, userId: 9 },
      storage,
    })
    const replayDraft = replay.save({ draftId: 'draft-submit', data: {} })
    expect(replay.appendSubmitIntent(replayDraft.draftId, {
      formData: { amount: 10 },
      idempotencyKey: 'ui:offline-2',
    })).toMatchObject({ actionCode: 'submit_order', objectCode: 'order' })
  })

  it('creates a stable schema hash independent of object key order', () => {
    expect(createOfflineSchemaHash({ fields: [{ name: 'amount' }], version: 1 }))
      .toBe(createOfflineSchemaHash({ version: 1, fields: [{ name: 'amount' }] }))
  })

  it('keeps only managed runtime identifiers needed for version checks and replay', () => {
    expect(normalizeOfflineFormConfig({
      enabled: true,
      applicationCode: 'presale',
      objectCode: 'order',
      formCode: 'main',
      configKey: 'presale_order',
      suiteCode: 'sales',
      replayActionCode: 'submit_order',
      recordVersionField: 'versionNo',
      authorization: 'never-forward',
    })).toMatchObject({
      enabled: true,
      configKey: 'presale_order',
      suiteCode: 'sales',
      replayActionCode: 'submit_order',
      recordVersionField: 'versionNo',
    })
  })

  it('rebuilds the current publication fingerprint from the latest render config', () => {
    const snapshot = createOfflinePublishedSnapshot({
      publishedVersion: 4,
      modelSchema: { fields: [{ field: 'amount' }] },
      pageSchema: { layoutType: 'simple-crud' },
    }, 'main')

    expect(snapshot).toEqual({
      publishedVersion: '4',
      schemaHash: createOfflineSchemaHash({
        modelSchema: { fields: [{ field: 'amount' }] },
        pageSchema: { layoutType: 'simple-crud' },
        formCode: 'main',
      }),
    })
  })
})
