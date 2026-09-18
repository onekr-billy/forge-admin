import {
  buildBindingParams as buildFieldEventParams,
  containsDangerousConfigKey,
  DANGEROUS_CONFIG_KEYS,
  findMissingRequiredParams,
  IDENTIFIER_PATTERN,
  isBlank,
  isPlainObject,
  isSafePath,
  mapBindingResult as mapFieldEventResult,
  MISSING_MODES,
  normalizeMessage,
  PARAM_PATTERN,
  PARAM_SOURCE_TYPES,
  QUERY_SOURCE_TYPES,
  RESULT_MODES,
  snapshotRuntimeContext,
  SOURCE_KEY_PATTERN,
  unwrapQuerySourceData,
} from './data-source-binding-runtime'

// 兼容存量引用：参数/结果映射从统一数据源绑定运行时复用，协议同源，避免两套映射并存
export { buildFieldEventParams, mapFieldEventResult }

const FIELD_EVENT_TRIGGERS = new Set(['FORM_LOAD', 'CHANGE', 'BLUR', 'MANUAL', 'SCAN_COMPLETE'])
const ERROR_MODES = new Set(['MESSAGE', 'SILENT'])

function normalizeScanRuntimeContext(runtime = {}) {
  const scan = runtime?.scan
  if (!scan || typeof scan !== 'object')
    return null
  const value = typeof scan.value === 'string' ? scan.value.trim() : ''
  if (!value || value.length > 2048)
    return null
  const type = typeof scan.type === 'string' && scan.type.trim()
    ? scan.type.trim().slice(0, 64)
    : 'UNKNOWN'
  const platform = typeof scan.platform === 'string' && scan.platform.trim()
    ? scan.platform.trim().slice(0, 32)
    : 'BROWSER'
  return { value, type, platform }
}

export function normalizeFieldEventRules(rules, fields = []) {
  const knownFields = new Set((Array.isArray(fields) ? fields : []).map(value => String(value || '').trim()).filter(Boolean))
  const seenIds = new Set()
  const result = []

  for (const candidate of Array.isArray(rules) ? rules : []) {
    const normalized = normalizeFieldEventRule(candidate, knownFields)
    if (!normalized || seenIds.has(normalized.id))
      continue
    seenIds.add(normalized.id)
    result.push(normalized)
  }
  return result
}

export function createFieldEventRuntime(options = {}) {
  let normalizedRules = normalizeFieldEventRules(options.rules, options.fields)
  const timers = new Map()
  const controllers = new Map()
  const sequences = new Map()
  const contextSnapshots = new Map()
  let disposed = false

  function setRules(rules, fields = options.fields) {
    cancelPending()
    normalizedRules = normalizeFieldEventRules(rules, fields)
    contextSnapshots.clear()
  }

  function getRules(trigger, sourceField) {
    const normalizedTrigger = String(trigger || '').trim().toUpperCase()
    const normalizedField = String(sourceField || '').trim()
    return normalizedRules.filter(rule => rule.enabled
      && rule.trigger === normalizedTrigger
      && (rule.trigger === 'FORM_LOAD' || rule.sourceField === normalizedField))
  }

  function hasRule(trigger, sourceField) {
    return getRules(trigger, sourceField).length > 0
  }

  function dispatch(trigger, sourceField, runtime = {}) {
    if (disposed)
      return Promise.resolve([])
    return Promise.all(getRules(trigger, sourceField).map(rule => schedule(rule, runtime)))
  }

  function schedule(rule, runtime = {}) {
    const formData = options.getFormData?.() || {}
    if (rule.skipWhenEmpty && rule.sourceField && isBlank(formData[rule.sourceField])) {
      settleTimer(rule.id, { status: 'cancelled' })
      nextSequence(rule.id)
      abortRule(rule.id)
      const patch = buildClearPatch(rule)
      if (Object.keys(patch).length)
        options.applyPatch?.(patch, rule)
      updateState(rule, 'idle', '')
      return Promise.resolve({ status: 'skipped' })
    }
    const delay = rule.trigger === 'CHANGE' ? rule.debounceMs : 0
    if (delay <= 0)
      return run(rule, runtime)

    settleTimer(rule.id, { status: 'cancelled' })
    return new Promise((resolve) => {
      const timer = setTimeout(async () => {
        timers.delete(rule.id)
        resolve(await run(rule, runtime))
      }, delay)
      timers.set(rule.id, { timer, resolve })
    })
  }

  async function run(rule, runtime = {}) {
    if (disposed)
      return { status: 'cancelled' }

    const sequence = nextSequence(rule.id)
    abortRule(rule.id)
    const formData = options.getFormData?.() || {}
    if (rule.skipWhenEmpty && rule.sourceField && isBlank(formData[rule.sourceField])) {
      const patch = buildClearPatch(rule)
      if (Object.keys(patch).length)
        options.applyPatch?.(patch, rule)
      updateState(rule, 'idle', '')
      return { status: 'skipped' }
    }

    // 必填参数前置校验短路：required 参数取值为空时跳过本次请求。
    // 与 skipWhenEmpty 的清空语义不同：这里保留旧回填值，仅不发请求。
    const runtimeContext = resolveRuleContext(rule, runtime)
    const missingParams = findMissingRequiredParams(rule, {
      formData,
      context: runtimeContext,
      routeQuery: options.getRouteQuery?.() || {},
    })
    if (missingParams.length) {
      updateState(rule, 'idle', '')
      return { status: 'skipped_params', missing: missingParams }
    }

    if (rule.clearTargetsOnTrigger) {
      const patch = buildClearPatch(rule)
      if (Object.keys(patch).length)
        options.applyPatch?.(patch, rule)
    }

    const controller = typeof AbortController === 'undefined' ? null : new AbortController()
    if (controller)
      controllers.set(rule.id, controller)
    updateState(rule, 'loading', '')

    try {
      const params = buildFieldEventParams(rule, {
        formData,
        context: runtimeContext,
        routeQuery: options.getRouteQuery?.() || {},
      })
      const response = await options.execute?.({
        sourceType: rule.sourceType,
        sourceKey: rule.sourceKey,
        params,
        ...(rule.sourceType === 'DATASET'
          ? {
              pageNum: rule.pageNum || 1,
              pageSize: rule.pageSize || 20,
              maxRows: rule.maxRows || rule.pageSize || 20,
            }
          : {}),
      }, {
        signal: controller?.signal,
        needTip: false,
      })
      if (!isCurrent(rule.id, sequence) || disposed)
        return { status: 'stale' }

      const mapped = mapFieldEventResult(rule, unwrapQuerySourceData(response))
      if (Object.keys(mapped.patch).length)
        options.applyPatch?.(mapped.patch, rule)
      if (!mapped.found) {
        updateState(rule, 'not_found', rule.notFoundMessage)
        notify(rule, rule.notFoundMessage, 'warning')
        return { status: 'not_found', patch: mapped.patch }
      }
      updateState(rule, 'success', '')
      return { status: 'success', patch: mapped.patch }
    }
    catch (error) {
      if (!isCurrent(rule.id, sequence) || disposed || isAbortError(error, controller))
        return { status: 'cancelled' }
      updateState(rule, 'error', rule.errorMessage)
      notify(rule, rule.errorMessage, 'error')
      return { status: 'error' }
    }
    finally {
      if (controllers.get(rule.id) === controller)
        controllers.delete(rule.id)
    }
  }

  function notify(rule, message, type) {
    if (rule.errorMode === 'SILENT' || !message)
      return
    options.onNotify?.({ ruleId: rule.id, field: rule.sourceField, message, type })
  }

  function updateState(rule, status, message) {
    options.onStateChange?.({
      ruleId: rule.id,
      field: rule.sourceField,
      status,
      loading: status === 'loading',
      message: message || '',
    })
  }

  function abortRule(ruleId) {
    const controller = controllers.get(ruleId)
    if (controller && !controller.signal.aborted)
      controller.abort()
    controllers.delete(ruleId)
  }

  function nextSequence(ruleId) {
    const next = (sequences.get(ruleId) || 0) + 1
    sequences.set(ruleId, next)
    return next
  }

  function isCurrent(ruleId, sequence) {
    return sequences.get(ruleId) === sequence
  }

  // CONTEXT 快照语义：开启 options.snapshotContext 后，每条规则首次执行时冻结基础上下文，
  // 后续执行复用快照，避免 CONTEXT_PATH 参数在多次触发间取值漂移（scan 等事件级数据仍实时合并）。
  function resolveRuleContext(rule, runtime) {
    const base = options.snapshotContext === true
      ? resolveContextSnapshot(rule)
      : (options.getContext?.() || {})
    return buildRuntimeContext(base, runtime)
  }

  function resolveContextSnapshot(rule) {
    if (!contextSnapshots.has(rule.id))
      contextSnapshots.set(rule.id, snapshotRuntimeContext(options.getContext?.() || {}))
    return contextSnapshots.get(rule.id)
  }

  function settleTimer(ruleId, result) {
    const pending = timers.get(ruleId)
    if (!pending)
      return
    clearTimeout(pending.timer)
    timers.delete(ruleId)
    pending.resolve(result)
  }

  function cancelPending() {
    for (const ruleId of [...timers.keys()])
      settleTimer(ruleId, { status: 'cancelled' })
    for (const ruleId of [...controllers.keys()]) {
      nextSequence(ruleId)
      abortRule(ruleId)
    }
  }

  function dispose() {
    if (disposed)
      return
    disposed = true
    cancelPending()
    contextSnapshots.clear()
  }

  return {
    dispatch,
    dispose,
    getRules,
    hasRule,
    setRules,
  }
}

function buildRuntimeContext(baseContext, runtime) {
  const scan = normalizeScanRuntimeContext(runtime)
  if (!scan)
    return baseContext
  return {
    ...(isPlainObject(baseContext) ? baseContext : {}),
    scan,
  }
}

function normalizeFieldEventRule(candidate, knownFields) {
  if (!isPlainObject(candidate) || containsDangerousConfigKey(candidate))
    return null

  const id = String(candidate.id || '').trim()
  const trigger = String(candidate.trigger || '').trim().toUpperCase()
  const sourceField = String(candidate.sourceField || '').trim()
  const sourceType = String(candidate.sourceType || '').trim().toUpperCase()
  const sourceKey = String(candidate.sourceKey || '').trim()
  const resultMode = String(candidate.resultMode || 'ROOT').trim().toUpperCase()
  const errorMode = String(candidate.errorMode || 'MESSAGE').trim().toUpperCase()
  if (!IDENTIFIER_PATTERN.test(id)
    || !FIELD_EVENT_TRIGGERS.has(trigger)
    || !QUERY_SOURCE_TYPES.has(sourceType)
    || !SOURCE_KEY_PATTERN.test(sourceKey)
    || !RESULT_MODES.has(resultMode)
    || !ERROR_MODES.has(errorMode)) {
    return null
  }
  if (trigger !== 'FORM_LOAD' && (!sourceField || (knownFields.size && !knownFields.has(sourceField))))
    return null

  const debounceMs = normalizeDebounce(candidate.debounceMs, trigger)
  if (debounceMs === null)
    return null
  const pageNum = normalizePageNum(candidate.pageNum)
  const pageSize = normalizePageSize(candidate.pageSize)
  const maxRows = candidate.maxRows === undefined || candidate.maxRows === null || candidate.maxRows === ''
    ? null
    : normalizePageSize(candidate.maxRows)
  const paramMappings = normalizeParamMappings(candidate.paramMappings, knownFields)
  const resultMappings = normalizeResultMappings(candidate.resultMappings, knownFields)
  if (!paramMappings || !resultMappings || !resultMappings.length)
    return null

  return {
    id,
    name: normalizeMessage(candidate.name, 80) || id,
    enabled: candidate.enabled !== false,
    trigger,
    sourceField,
    sourceType,
    sourceKey,
    pageNum,
    pageSize,
    ...(maxRows !== null ? { maxRows } : {}),
    debounceMs,
    skipWhenEmpty: candidate.skipWhenEmpty !== false,
    clearTargetsOnTrigger: candidate.clearTargetsOnTrigger === true,
    paramMappings,
    resultMode,
    resultMappings,
    notFoundMessage: normalizeMessage(candidate.notFoundMessage, 200) || '未匹配到数据',
    errorMessage: normalizeMessage(candidate.errorMessage, 200) || '查询失败，请重试',
    errorMode,
  }
}

function normalizePageNum(value) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? Math.min(number, 100000) : 1
}

function normalizePageSize(value) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? Math.min(number, 100) : 20
}

function normalizeParamMappings(mappings, knownFields) {
  if (!Array.isArray(mappings))
    return null
  const seen = new Set()
  const result = []
  for (const item of mappings) {
    if (!isPlainObject(item))
      return null
    const param = String(item.param || '').trim()
    const source = String(item.source || '').trim().toUpperCase()
    const field = String(item.field || '').trim()
    const path = String(item.path || field).trim()
    if (!PARAM_PATTERN.test(param)
      || DANGEROUS_CONFIG_KEYS.has(param.toLowerCase())
      || seen.has(param)
      || !PARAM_SOURCE_TYPES.has(source)) {
      return null
    }
    if (source === 'FORM_FIELD' && (!field || !isSafePath(field) || (knownFields.size && !knownFields.has(field))))
      return null
    if (source !== 'FORM_FIELD' && !isSafePath(path))
      return null
    seen.add(param)
    const normalized = source === 'FORM_FIELD' ? { param, source, field } : { param, source, path }
    if (item.required === true)
      normalized.required = true
    result.push(normalized)
  }
  return result
}

function normalizeResultMappings(mappings, knownFields) {
  if (!Array.isArray(mappings))
    return null
  const seen = new Set()
  const result = []
  for (const item of mappings) {
    if (!isPlainObject(item))
      return null
    const from = String(item.from || '').trim()
    const to = String(item.to || '').trim()
    const whenMissing = String(item.whenMissing || 'CLEAR').trim().toUpperCase()
    if ((from && !isSafePath(from)) || !to || seen.has(to) || !MISSING_MODES.has(whenMissing))
      return null
    if (knownFields.size && !knownFields.has(to))
      return null
    seen.add(to)
    result.push({ from, to, whenMissing })
  }
  return result
}

function normalizeDebounce(value, trigger) {
  if (value === undefined || value === null || value === '')
    return trigger === 'CHANGE' ? 300 : 0
  const result = Number(value)
  if (!Number.isInteger(result) || result < 0 || result > 5000)
    return null
  return result
}

function buildClearPatch(rule) {
  return (Array.isArray(rule?.resultMappings) ? rule.resultMappings : []).reduce((patch, mapping) => {
    if (mapping.whenMissing === 'CLEAR')
      patch[mapping.to] = undefined
    return patch
  }, {})
}

function isAbortError(error, controller) {
  return controller?.signal?.aborted || error?.name === 'AbortError' || error?.code === 'ERR_CANCELED'
}
