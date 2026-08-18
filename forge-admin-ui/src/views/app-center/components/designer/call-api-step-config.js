export const CALL_API_FAILURE_STRATEGY = Object.freeze({
  THROW: 'THROW',
  LOG_AND_CONTINUE: 'LOG_AND_CONTINUE',
})

export const CALL_API_RESULT_TARGET = Object.freeze({
  STEP_CONTEXT: 'STEP_CONTEXT',
  FORM_DATA: 'FORM_DATA',
})

export function normalizeCallApiStepConfig(value = {}) {
  const source = isPlainObject(value) ? clone(value) : {}
  return {
    ...source,
    sourceType: 'EXTERNAL_API',
    sourceKey: String(source.sourceKey || source.querySourceKey || '').trim(),
    paramMappings: normalizeParamMappings(source.paramMappings),
    resultMode: String(source.resultMode || 'ROOT').toUpperCase() === 'FIRST_ROW' ? 'FIRST_ROW' : 'ROOT',
    resultMappings: normalizeResultMappings(source.resultMappings),
    failureStrategy: String(source.failureStrategy || '').toUpperCase() === CALL_API_FAILURE_STRATEGY.LOG_AND_CONTINUE
      ? CALL_API_FAILURE_STRATEGY.LOG_AND_CONTINUE
      : CALL_API_FAILURE_STRATEGY.THROW,
  }
}

export function filterExternalApiSources(catalog = []) {
  return (Array.isArray(catalog) ? catalog : [])
    .filter(item => String(item?.sourceType || '').toUpperCase() === 'EXTERNAL_API' && item?.sourceKey)
}

export function parseCallApiInputSchema(value) {
  if (Array.isArray(value))
    return value.filter(item => item?.name)
  try {
    const parsed = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed.filter(item => item?.name) : []
  }
  catch {
    return []
  }
}

export function syncCallApiParamMappings(config, metadata, fieldOptions = []) {
  const normalized = normalizeCallApiStepConfig(config)
  const current = new Map(normalized.paramMappings.map(item => [item.param, item]))
  const fields = new Set((Array.isArray(fieldOptions) ? fieldOptions : []).map(item => item?.value).filter(Boolean))
  normalized.paramMappings = parseCallApiInputSchema(metadata?.inputSchemaJson).map((definition) => {
    const existing = current.get(definition.name)
    if (existing)
      return existing
    return {
      param: definition.name,
      sourceType: 'record',
      sourceField: fields.has(definition.name) ? definition.name : '',
    }
  })
  return normalized
}

export function createCallApiBusinessActionStep(index = 1, timestamp = Date.now()) {
  return {
    stepCode: `call_api_${timestamp}_${index}`,
    stepName: '调用外部接口',
    stepType: 'CALL_API',
    rollbackOnFailure: true,
    stepConfig: normalizeCallApiStepConfig(),
  }
}

function normalizeParamMappings(value) {
  return (Array.isArray(value) ? value : []).map(item => ({
    ...clone(item || {}),
    param: String(item?.param || item?.name || '').trim(),
    sourceType: normalizeParamSource(item),
    sourceField: String(item?.sourceField || item?.field || item?.path || '').trim(),
  }))
}

function normalizeParamSource(item = {}) {
  const explicit = String(item.sourceType || '').trim().toLowerCase()
  if (['record', 'form', 'context', 'system', 'static'].includes(explicit))
    return explicit
  const legacy = String(item.source || '').trim().toUpperCase()
  return {
    FORM_FIELD: 'form',
    RECORD_FIELD: 'record',
    CONTEXT_PATH: 'context',
    ROUTE_QUERY: 'context',
    SYSTEM_CONTEXT: 'system',
    SYSTEM: 'system',
    STATIC: 'static',
    STATIC_VALUE: 'static',
  }[legacy] || 'record'
}

function normalizeResultMappings(value) {
  return (Array.isArray(value) ? value : []).map(item => ({
    ...clone(item || {}),
    from: String(item?.from || item?.source || item?.path || '').trim(),
    to: String(item?.to || item?.targetField || item?.field || '').trim(),
    target: String(item?.target || item?.targetType || '').toUpperCase() === CALL_API_RESULT_TARGET.FORM_DATA
      ? CALL_API_RESULT_TARGET.FORM_DATA
      : CALL_API_RESULT_TARGET.STEP_CONTEXT,
    whenMissing: String(item?.whenMissing || '').toUpperCase() === 'CLEAR' ? 'CLEAR' : 'KEEP',
  }))
}

function isPlainObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
}

function clone(value) {
  return JSON.parse(JSON.stringify(value ?? {}))
}
