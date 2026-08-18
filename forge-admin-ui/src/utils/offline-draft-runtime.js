const DEFAULT_MAX_DRAFTS = 50
const DEFAULT_MAX_BYTES = 1024 * 1024
const MAX_DRAFT_ID_LENGTH = 96
const MAX_NAMESPACE_LENGTH = 120
const SAFE_IDENTIFIER = /^[a-z\d][\w.:-]{0,127}$/i
const SAFE_IDEMPOTENCY_KEY = /^[\w.:-]{8,128}$/
const SENSITIVE_KEYS = new Set([
  'access_token',
  'accessToken',
  'ak',
  'app_secret',
  'appSecret',
  'authorization',
  'cookie',
  'password',
  'secret',
  'sk',
  'tenant_id',
  'tenantId',
  'token',
  'user_id',
  'userId',
  'activeOrgId',
  'active_org_id',
].map(key => key.toLowerCase()))
const INVALID = Symbol('offline-draft-invalid')
const SKIP = Symbol('offline-draft-skip')

export const OFFLINE_DRAFT_STATUS = Object.freeze({
  DRAFT: 'DRAFT',
  REPLAY_PENDING: 'REPLAY_PENDING',
  REPLAY_FAILED: 'REPLAY_FAILED',
  CONFLICT: 'CONFLICT',
  COMPLETED: 'COMPLETED',
})

export const OFFLINE_REPLAY_STATUS = Object.freeze({
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
})

export function createOfflineDraftStore(options = {}) {
  const storage = options.storage || resolveStorage()
  const namespace = normalizeNamespace(options.namespace)
  const maxDrafts = normalizeLimit(options.maxDrafts, DEFAULT_MAX_DRAFTS, 1, 200)
  const maxBytes = normalizeLimit(options.maxBytes, DEFAULT_MAX_BYTES, 4096, 5 * 1024 * 1024)
  const now = typeof options.now === 'function' ? options.now : () => Date.now()
  const indexKey = `${namespace}:index`

  function saveDraft(input = {}) {
    const existingId = String(input.draftId || '').trim()
    const draftId = existingId || createDraftId()
    if (!isSafeDraftId(draftId))
      throw createDraftError('DRAFT_INVALID_ID', '草稿标识无效')
    const data = sanitizeJsonValue(input.data && typeof input.data === 'object' ? input.data : {})
    if (data === INVALID)
      throw createDraftError('DRAFT_INVALID_DATA', '草稿数据不可保存')
    const previous = readDraft(draftId)
    const timestamp = now()
    const draft = {
      version: 1,
      draftId,
      applicationCode: normalizeIdentifier(input.applicationCode, 'application'),
      objectCode: normalizeIdentifier(input.objectCode, 'object'),
      formCode: normalizeIdentifier(input.formCode, 'form'),
      publishedVersion: normalizeVersion(input.publishedVersion || previous?.publishedVersion),
      schemaHash: normalizeText(input.schemaHash || previous?.schemaHash, 128),
      recordId: normalizeText(input.recordId ?? previous?.recordId, 128),
      baseRecordVersion: normalizeText(input.baseRecordVersion ?? previous?.baseRecordVersion, 128),
      data,
      replayLog: Array.isArray(input.replayLog)
        ? normalizeReplayLog(input.replayLog)
        : (previous?.replayLog || []),
      status: normalizeDraftStatus(input.status || previous?.status || OFFLINE_DRAFT_STATUS.DRAFT),
      conflict: input.conflict || previous?.conflict || null,
      failureMessage: normalizeText(input.failureMessage || previous?.failureMessage, 200),
      createdAt: previous?.createdAt || timestamp,
      updatedAt: timestamp,
    }
    persistDraft(draft)
    enforceDraftLimit()
    return clone(draft)
  }

  function getDraft(draftId) {
    const normalizedId = String(draftId || '').trim()
    if (!isSafeDraftId(normalizedId))
      return null
    return clone(readDraft(normalizedId))
  }

  function listDrafts() {
    const ids = readIndex()
    const drafts = []
    const validIds = []
    ids.forEach((draftId) => {
      const draft = readDraft(draftId)
      if (!draft)
        return
      validIds.push(draftId)
      drafts.push(draft)
    })
    if (validIds.length !== ids.length)
      writeIndex(validIds)
    return drafts
      .sort((left, right) => Number(right.updatedAt || 0) - Number(left.updatedAt || 0))
      .map(clone)
  }

  function removeDraft(draftId) {
    const normalizedId = String(draftId || '').trim()
    if (!isSafeDraftId(normalizedId))
      return false
    storage.removeItem(draftKey(normalizedId))
    writeIndex(readIndex().filter(id => id !== normalizedId))
    return true
  }

  function appendReplayIntent(draftId, input = {}) {
    const draft = getDraft(draftId)
    if (!draft)
      throw createDraftError('DRAFT_NOT_FOUND', '草稿不存在')
    if (draft.status === OFFLINE_DRAFT_STATUS.CONFLICT)
      throw createDraftError('DRAFT_CONFLICT', '草稿存在冲突，不能继续追加重放')
    const intent = normalizeReplayIntent(input, draft.publishedVersion)
    if (!intent)
      throw createDraftError('DRAFT_INVALID_REPLAY', '重放意图无效')
    const existing = draft.replayLog.find(item => item.idempotencyKey === intent.idempotencyKey)
    if (existing)
      return clone(existing)
    draft.replayLog.push({
      ...intent,
      status: OFFLINE_REPLAY_STATUS.PENDING,
      createdAt: now(),
      updatedAt: now(),
      failureMessage: '',
    })
    draft.status = OFFLINE_DRAFT_STATUS.REPLAY_PENDING
    draft.updatedAt = now()
    persistDraft(draft)
    return clone(draft.replayLog.at(-1))
  }

  async function replayDraft(draftId, replayOptions = {}) {
    let draft = getDraft(draftId)
    if (!draft)
      return { status: 'error', code: 'DRAFT_NOT_FOUND' }
    if (replayOptions.confirmed !== true) {
      return {
        status: 'confirmation_required',
        code: 'DRAFT_REPLAY_CONFIRMATION_REQUIRED',
        draft,
      }
    }
    if (typeof replayOptions.loadCurrent !== 'function' || typeof replayOptions.execute !== 'function')
      return markReplayFailure(draft, 'DRAFT_REPLAY_UNAVAILABLE', '缺少重放校验或执行器')

    let current
    try {
      current = await replayOptions.loadCurrent({ draft: clone(draft) })
    }
    catch {
      return markReplayFailure(draft, 'DRAFT_REPLAY_UNAVAILABLE', '无法读取最新记录')
    }
    const conflict = detectDraftConflict(draft, current)
    if (conflict) {
      draft = updateDraft(draftId, {
        status: OFFLINE_DRAFT_STATUS.CONFLICT,
        conflict,
        failureMessage: conflict.message,
      })
      return { status: 'conflict', ...conflict, draft }
    }

    for (const entry of draft.replayLog) {
      if (entry.status === OFFLINE_REPLAY_STATUS.COMPLETED)
        continue
      draft = updateReplayEntry(draft, entry.idempotencyKey, {
        status: OFFLINE_REPLAY_STATUS.RUNNING,
        updatedAt: now(),
      })
      try {
        await replayOptions.execute({
          ...clone(entry),
          draft: clone(draft),
          current: clone(current),
        })
        draft = updateReplayEntry(draft, entry.idempotencyKey, {
          status: OFFLINE_REPLAY_STATUS.COMPLETED,
          updatedAt: now(),
          failureMessage: '',
        })
      }
      catch {
        draft = updateReplayEntry(draft, entry.idempotencyKey, {
          status: OFFLINE_REPLAY_STATUS.FAILED,
          updatedAt: now(),
          failureMessage: '重放执行失败',
        })
        draft = updateDraft(draftId, {
          status: OFFLINE_DRAFT_STATUS.REPLAY_FAILED,
          failureMessage: '重放执行失败',
        })
        return { status: 'failed', code: 'DRAFT_REPLAY_FAILED', draft }
      }
    }
    draft = updateDraft(draftId, {
      status: OFFLINE_DRAFT_STATUS.COMPLETED,
      conflict: null,
      failureMessage: '',
    })
    return { status: 'completed', draft }
  }

  function enforceDraftLimit() {
    const drafts = listDrafts()
    drafts
      .sort((left, right) => Number(left.updatedAt || 0) - Number(right.updatedAt || 0))
      .slice(0, Math.max(0, drafts.length - maxDrafts))
      .forEach(draft => removeDraft(draft.draftId))
  }

  function persistDraft(draft) {
    const serialized = JSON.stringify(draft)
    if (serialized.length > maxBytes)
      throw createDraftError('DRAFT_TOO_LARGE', '草稿超过本地存储上限')
    try {
      storage.setItem(draftKey(draft.draftId), serialized)
      const ids = readIndex().filter(id => id !== draft.draftId)
      writeIndex([draft.draftId, ...ids])
    }
    catch {
      throw createDraftError('DRAFT_STORAGE_FAILED', '草稿保存失败')
    }
  }

  function readDraft(draftId) {
    try {
      const raw = storage.getItem(draftKey(draftId))
      if (!raw)
        return null
      const parsed = JSON.parse(raw)
      return parsed && parsed.draftId === draftId ? parsed : null
    }
    catch {
      return null
    }
  }

  function updateDraft(draftId, patch) {
    const draft = readDraft(draftId)
    if (!draft)
      return null
    const next = {
      ...draft,
      ...patch,
      updatedAt: now(),
    }
    persistDraft(next)
    return clone(next)
  }

  function updateReplayEntry(draft, idempotencyKey, patch) {
    const next = {
      ...draft,
      replayLog: draft.replayLog.map((entry) => {
        if (entry.idempotencyKey !== idempotencyKey)
          return entry
        return { ...entry, ...patch }
      }),
      updatedAt: now(),
    }
    persistDraft(next)
    return next
  }

  function markReplayFailure(draft, code, message) {
    const next = updateDraft(draft.draftId, {
      status: OFFLINE_DRAFT_STATUS.REPLAY_FAILED,
      failureMessage: message,
    })
    return { status: 'failed', code, draft: next }
  }

  function readIndex() {
    try {
      const parsed = JSON.parse(storage.getItem(indexKey) || '[]')
      return Array.isArray(parsed) ? parsed.filter(isSafeDraftId) : []
    }
    catch {
      return []
    }
  }

  function writeIndex(ids) {
    storage.setItem(indexKey, JSON.stringify([...new Set(ids)].slice(0, maxDrafts + 20)))
  }

  function draftKey(draftId) {
    return `${namespace}:draft:${draftId}`
  }

  return {
    appendReplayIntent,
    getDraft,
    listDrafts,
    removeDraft,
    replayDraft,
    saveDraft,
  }
}

export function detectDraftConflict(draft = {}, current = {}) {
  if (current?.available === false || (draft.recordId && !current))
    return conflict('RECORD_UNAVAILABLE', '当前记录不可用或无权访问')
  const expectedPublished = normalizeVersion(draft.publishedVersion)
  const actualPublished = normalizeVersion(current?.publishedVersion || current?.version)
  if (expectedPublished && actualPublished && expectedPublished !== actualPublished)
    return conflict('PUBLISHED_VERSION_CONFLICT', '应用发布版本已变化，请重新加载')
  const expectedSchema = normalizeText(draft.schemaHash, 128)
  const actualSchema = normalizeText(current?.schemaHash, 128)
  if (expectedSchema && actualSchema && expectedSchema !== actualSchema)
    return conflict('SCHEMA_CONFLICT', '表单结构已变化，请重新加载')
  const expectedRecord = normalizeText(draft.baseRecordVersion, 128)
  const actualRecord = normalizeText(current?.recordVersion || current?.recordVersionNo, 128)
  if (expectedRecord && actualRecord && expectedRecord !== actualRecord)
    return conflict('RECORD_VERSION_CONFLICT', '记录已被其他人更新，请重新加载')
  return null
}

export function buildOfflineDraftNamespace(scope = {}) {
  const keys = ['tenantId', 'userId', 'applicationCode', 'objectCode', 'formCode']
  const values = keys.map((key) => {
    const value = String(scope[key] ?? '').trim()
    if (!value)
      throw createDraftError('DRAFT_INVALID_NAMESPACE', `草稿作用域缺少${key}`)
    return value.replace(/[^\w.:-]/g, '_').slice(0, 64)
  })
  const full = `forge-lowcode:${values.join(':')}`
  if (full.length <= MAX_NAMESPACE_LENGTH)
    return full
  const suffix = simpleHash(full)
  return `${full.slice(0, MAX_NAMESPACE_LENGTH - suffix.length - 1)}:${suffix}`
}

export function createDraftError(code, message) {
  const error = new Error(message || code)
  error.code = code
  return error
}

function normalizeReplayIntent(input, fallbackVersion) {
  if (!input || typeof input !== 'object')
    return null
  const actionCode = normalizeIdentifier(input.actionCode, '')
  const objectCode = normalizeIdentifier(input.objectCode, '')
  const idempotencyKey = String(input.idempotencyKey || '').trim()
  if (!actionCode || !objectCode || !SAFE_IDEMPOTENCY_KEY.test(idempotencyKey))
    return null
  const formData = sanitizeJsonValue(input.formData && typeof input.formData === 'object' ? input.formData : {})
  const routeQuery = sanitizeJsonValue(input.routeQuery && typeof input.routeQuery === 'object' ? input.routeQuery : {})
  if (formData === INVALID || routeQuery === INVALID)
    return null
  return {
    actionCode,
    objectCode,
    recordId: normalizeText(input.recordId, 128),
    parentRecordId: normalizeText(input.parentRecordId, 128),
    childRecordId: normalizeText(input.childRecordId, 128),
    relationKey: normalizeText(input.relationKey, 128),
    publishedVersion: normalizeVersion(input.publishedVersion || fallbackVersion),
    idempotencyKey,
    formData,
    routeQuery,
  }
}

function normalizeReplayLog(log) {
  const seen = new Set()
  return log.map((source) => {
    const item = normalizeReplayIntent(source, source?.publishedVersion)
    if (!item)
      return null
    return {
      ...item,
      status: Object.values(OFFLINE_REPLAY_STATUS).includes(source.status) ? source.status : OFFLINE_REPLAY_STATUS.PENDING,
      createdAt: Number(source.createdAt) || Date.now(),
      updatedAt: Number(source.updatedAt) || Date.now(),
      failureMessage: normalizeText(source.failureMessage, 200),
    }
  })
    .filter(Boolean)
    .map((item) => {
      if (seen.has(item.idempotencyKey))
        return null
      seen.add(item.idempotencyKey)
      return item
    })
    .filter(Boolean)
}

function sanitizeJsonValue(value, seen = new Set(), depth = 0, key = '') {
  if (SENSITIVE_KEYS.has(String(key || '').toLowerCase()))
    return SKIP
  if (value === null || typeof value === 'string' || typeof value === 'boolean')
    return value
  if (typeof value === 'number')
    return Number.isFinite(value) ? value : INVALID
  if (value === undefined || typeof value === 'function' || typeof value === 'symbol' || typeof value === 'bigint')
    return INVALID
  if (depth > 20 || seen.has(value))
    return INVALID
  seen.add(value)
  let result
  if (Array.isArray(value)) {
    result = []
    for (const item of value) {
      const next = sanitizeJsonValue(item, seen, depth + 1)
      if (next === INVALID) {
        seen.delete(value)
        return INVALID
      }
      result.push(next === SKIP ? null : next)
    }
  }
  else if (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null) {
    result = {}
    for (const [childKey, childValue] of Object.entries(value)) {
      if (SENSITIVE_KEYS.has(String(childKey).toLowerCase()))
        continue
      const next = sanitizeJsonValue(childValue, seen, depth + 1, childKey)
      if (next === INVALID) {
        seen.delete(value)
        return INVALID
      }
      if (next !== SKIP)
        result[childKey] = next
    }
  }
  else {
    seen.delete(value)
    return INVALID
  }
  seen.delete(value)
  return result
}

function normalizeDraftStatus(status) {
  return Object.values(OFFLINE_DRAFT_STATUS).includes(status) ? status : OFFLINE_DRAFT_STATUS.DRAFT
}

function normalizeIdentifier(value, fallback) {
  const text = String(value || '').trim()
  return text && SAFE_IDENTIFIER.test(text) ? text : fallback
}

function normalizeVersion(value) {
  return normalizeText(value, 128)
}

function normalizeText(value, maxLength) {
  const text = String(value ?? '').trim()
  return text.slice(0, maxLength)
}

function normalizeNamespace(value) {
  const text = String(value || 'forge-lowcode').trim().replace(/[^\w.:-]/g, '_')
  return text.slice(0, MAX_NAMESPACE_LENGTH) || 'forge-lowcode'
}

function simpleHash(value) {
  let hash = 2166136261
  for (let index = 0; index < value.length; index++) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0).toString(36)
}

function normalizeLimit(value, fallback, min, max) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.min(max, Math.max(min, Math.trunc(number))) : fallback
}

function isSafeDraftId(value) {
  return /^[a-z\d][\w.:-]{0,95}$/i.test(String(value || ''))
}

function conflict(code, message) {
  return { code, message }
}

function clone(value) {
  if (value === undefined || value === null)
    return value
  return JSON.parse(JSON.stringify(value))
}

function createDraftId() {
  if (typeof globalThis?.crypto?.randomUUID === 'function')
    return `draft:${globalThis.crypto.randomUUID()}`.slice(0, MAX_DRAFT_ID_LENGTH)
  return `draft:${Date.now().toString(36)}:${Math.random().toString(36).slice(2, 12)}`
}

function resolveStorage() {
  if (typeof globalThis?.localStorage !== 'undefined')
    return globalThis.localStorage
  const values = new Map()
  return {
    getItem: key => values.get(key) || null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
  }
}
