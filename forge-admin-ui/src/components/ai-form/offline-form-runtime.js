import {
  buildOfflineDraftNamespace,
  createOfflineDraftStore,
  OFFLINE_DRAFT_STATUS,
} from '@/utils/offline-draft-runtime'

const ACTIVE_DRAFT_STATUSES = new Set([
  OFFLINE_DRAFT_STATUS.DRAFT,
  OFFLINE_DRAFT_STATUS.REPLAY_PENDING,
  OFFLINE_DRAFT_STATUS.REPLAY_FAILED,
  OFFLINE_DRAFT_STATUS.CONFLICT,
])

export function normalizeOfflineFormConfig(config = {}) {
  if (!config || typeof config !== 'object' || config.enabled !== true)
    return { enabled: false }
  return {
    enabled: true,
    applicationCode: normalizeCode(config.applicationCode),
    objectCode: normalizeCode(config.objectCode),
    formCode: normalizeCode(config.formCode || 'default'),
    configKey: normalizeCode(config.configKey),
    suiteCode: normalizeCode(config.suiteCode),
    publishedVersion: normalizeText(config.publishedVersion, 128),
    schemaHash: normalizeText(config.schemaHash, 128),
    replayActionCode: normalizeCode(config.replayActionCode),
    recordVersionField: normalizeCode(config.recordVersionField || 'updateTime'),
    maxDrafts: normalizeNumber(config.maxDrafts, 20, 1, 100),
    maxBytes: normalizeNumber(config.maxBytes, 1024 * 1024, 4096, 5 * 1024 * 1024),
  }
}

export function createOfflineFormRuntime({ config, scope, storage, now } = {}) {
  const normalized = normalizeOfflineFormConfig(config)
  if (!normalized.enabled)
    return null
  const namespace = buildOfflineDraftNamespace({
    tenantId: scope?.tenantId,
    userId: scope?.userId,
    applicationCode: normalized.applicationCode,
    objectCode: normalized.objectCode,
    formCode: normalized.formCode,
  })
  const store = createOfflineDraftStore({
    namespace,
    storage,
    now,
    maxDrafts: normalized.maxDrafts,
    maxBytes: normalized.maxBytes,
  })

  function findDraft(recordId = '') {
    const normalizedRecordId = normalizeText(recordId, 128)
    return store.listDrafts().find(draft => (
      ACTIVE_DRAFT_STATUSES.has(draft.status)
      && normalizeText(draft.recordId, 128) === normalizedRecordId
      && draft.applicationCode === normalized.applicationCode
      && draft.objectCode === normalized.objectCode
      && draft.formCode === normalized.formCode
    )) || null
  }

  function save({ draftId, data, recordId, baseRecordVersion } = {}) {
    return store.saveDraft({
      draftId,
      applicationCode: normalized.applicationCode,
      objectCode: normalized.objectCode,
      formCode: normalized.formCode,
      publishedVersion: normalized.publishedVersion,
      schemaHash: normalized.schemaHash,
      recordId,
      baseRecordVersion,
      data,
    })
  }

  function appendSubmitIntent(draftId, { formData, recordId, idempotencyKey, routeQuery } = {}) {
    if (!normalized.replayActionCode)
      return null
    return store.appendReplayIntent(draftId, {
      actionCode: normalized.replayActionCode,
      objectCode: normalized.objectCode,
      recordId,
      formData,
      routeQuery,
      publishedVersion: normalized.publishedVersion,
      idempotencyKey,
    })
  }

  return {
    config: normalized,
    namespace,
    store,
    appendSubmitIntent,
    findDraft,
    save,
  }
}

export function createOfflineSchemaHash(value) {
  const serialized = stableSerialize(value)
  let hash = 2166136261
  for (let index = 0; index < serialized.length; index++) {
    hash ^= serialized.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return `fnv1a:${(hash >>> 0).toString(36)}`
}

export function createOfflinePublishedSnapshot(renderConfig = {}, formCode = 'default') {
  const normalizedFormCode = normalizeCode(formCode || 'default') || 'default'
  return {
    publishedVersion: normalizeText(
      renderConfig.publishedVersion ?? renderConfig.lastPublishVersion,
      128,
    ),
    schemaHash: createOfflineSchemaHash({
      modelSchema: renderConfig.modelSchema || {},
      pageSchema: renderConfig.pageSchema || {},
      formCode: normalizedFormCode,
    }),
  }
}

function stableSerialize(value) {
  if (Array.isArray(value))
    return `[${value.map(stableSerialize).join(',')}]`
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}

function normalizeCode(value) {
  const code = String(value || '').trim()
  return /^[a-z\d][\w.:-]{0,127}$/i.test(code) ? code : ''
}

function normalizeText(value, length) {
  return String(value ?? '').trim().slice(0, length)
}

function normalizeNumber(value, fallback, min, max) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.min(max, Math.max(min, Math.trunc(number))) : fallback
}
