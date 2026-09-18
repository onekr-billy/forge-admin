export function normalizeSelectorMappings(mappings = []) {
  if (!mappings)
    return {}
  if (!Array.isArray(mappings) && typeof mappings === 'object')
    return { ...mappings }
  return (Array.isArray(mappings) ? mappings : []).reduce((result, item) => {
    if (!item || typeof item !== 'object')
      return result
    const sourceField = String(item.sourceField || item.source || '').trim()
    const targetField = String(item.targetField || item.target || '').trim()
    if (sourceField && targetField)
      result[sourceField] = targetField
    return result
  }, {})
}

export function applyRecordFieldMappings(record = {}, mappings = {}) {
  const source = extractSelectorRawRecord(record)
  const normalizedMappings = normalizeSelectorMappings(mappings)
  const result = {}
  Object.entries(normalizedMappings).forEach(([sourceField, targetField]) => {
    result[targetField] = readPath(source, sourceField)
  })
  return result
}

export function resolveSelectorSearchParams(searchParams = {}, context = {}) {
  const result = {}
  Object.entries(searchParams || {}).forEach(([key, value]) => {
    const resolved = resolveSelectorParamValue(value, context)
    if (!isEmptySelectorParam(resolved))
      result[key] = resolved
  })
  return result
}

export function normalizeRecordSelectorConfig(source = {}) {
  const field = source && typeof source === 'object' ? source : {}
  const config = mergeSelectorFragments(field)
  const suiteCode = firstText(
    config.suiteCode,
    config.businessSuiteCode,
    field.suiteCode,
    field.businessSuiteCode,
    field.props?.suiteCode,
    field.props?.businessSuiteCode,
    field.basicProps?.suiteCode,
    field.basicProps?.businessSuiteCode,
  )
  const objectCode = firstText(
    config.objectCode,
    config.object_code,
    config.businessObjectCode,
    config.business_object_code,
    config.targetObjectCode,
    config.target_object_code,
    config.targetEntityCode,
    config.target_entity_code,
    config.candidateObjectCode,
    config.candidate_object_code,
    config.referenceObjectCode,
    config.reference_object_code,
    config.refObjectCode,
    config.ref_object_code,
    config.sourceObjectCode,
    config.source_object_code,
    config.targetCode,
    config.target_code,
    config.recordSelectorObjectCode,
    config.record_selector_object_code,
    config.selectorObjectCode,
    config.selector_object_code,
    field.objectCode,
    field.object_code,
    field.businessObjectCode,
    field.business_object_code,
    field.targetObjectCode,
    field.target_object_code,
    field.targetEntityCode,
    field.target_entity_code,
    field.candidateObjectCode,
    field.candidate_object_code,
    field.referenceObjectCode,
    field.reference_object_code,
    field.refObjectCode,
    field.ref_object_code,
    field.sourceObjectCode,
    field.source_object_code,
    field.targetCode,
    field.target_code,
    field.recordSelectorObjectCode,
    field.record_selector_object_code,
    field.selectorObjectCode,
    field.selector_object_code,
    field.props?.objectCode,
    field.props?.object_code,
    field.props?.businessObjectCode,
    field.props?.business_object_code,
    field.props?.targetObjectCode,
    field.props?.target_object_code,
    field.props?.targetEntityCode,
    field.props?.target_entity_code,
    field.props?.candidateObjectCode,
    field.props?.candidate_object_code,
    field.props?.referenceObjectCode,
    field.props?.reference_object_code,
    field.props?.refObjectCode,
    field.props?.ref_object_code,
    field.props?.sourceObjectCode,
    field.props?.source_object_code,
    field.props?.targetCode,
    field.props?.target_code,
    field.props?.recordSelectorObjectCode,
    field.props?.record_selector_object_code,
    field.props?.selectorObjectCode,
    field.props?.selector_object_code,
    field.basicProps?.objectCode,
    field.basicProps?.object_code,
    field.basicProps?.businessObjectCode,
    field.basicProps?.business_object_code,
    field.basicProps?.targetObjectCode,
    field.basicProps?.target_object_code,
    field.basicProps?.targetEntityCode,
    field.basicProps?.target_entity_code,
    field.basicProps?.candidateObjectCode,
    field.basicProps?.candidate_object_code,
    field.basicProps?.referenceObjectCode,
    field.basicProps?.reference_object_code,
    field.basicProps?.refObjectCode,
    field.basicProps?.ref_object_code,
    field.basicProps?.sourceObjectCode,
    field.basicProps?.source_object_code,
    field.basicProps?.targetCode,
    field.basicProps?.target_code,
    field.basicProps?.recordSelectorObjectCode,
    field.basicProps?.record_selector_object_code,
    field.basicProps?.selectorObjectCode,
    field.basicProps?.selector_object_code,
  )
  return {
    ...config,
    suiteCode,
    objectCode,
    businessObjectCode: firstText(config.businessObjectCode, config.business_object_code, field.businessObjectCode, field.business_object_code, field.props?.businessObjectCode, field.props?.business_object_code, field.basicProps?.businessObjectCode, field.basicProps?.business_object_code, objectCode),
    targetObjectCode: firstText(config.targetObjectCode, config.target_object_code, field.targetObjectCode, field.target_object_code, field.props?.targetObjectCode, field.props?.target_object_code, field.basicProps?.targetObjectCode, field.basicProps?.target_object_code, objectCode),
    targetEntityCode: firstText(config.targetEntityCode, config.target_entity_code, field.targetEntityCode, field.target_entity_code, field.props?.targetEntityCode, field.props?.target_entity_code, field.basicProps?.targetEntityCode, field.basicProps?.target_entity_code, objectCode),
    candidateObjectCode: firstText(config.candidateObjectCode, config.candidate_object_code, field.candidateObjectCode, field.candidate_object_code, field.props?.candidateObjectCode, field.props?.candidate_object_code, field.basicProps?.candidateObjectCode, field.basicProps?.candidate_object_code),
    referenceObjectCode: firstText(config.referenceObjectCode, config.reference_object_code, field.referenceObjectCode, field.reference_object_code, field.props?.referenceObjectCode, field.props?.reference_object_code, field.basicProps?.referenceObjectCode, field.basicProps?.reference_object_code),
    refObjectCode: firstText(config.refObjectCode, config.ref_object_code, field.refObjectCode, field.ref_object_code, field.props?.refObjectCode, field.props?.ref_object_code, field.basicProps?.refObjectCode, field.basicProps?.ref_object_code),
    sourceObjectCode: firstText(config.sourceObjectCode, config.source_object_code, field.sourceObjectCode, field.source_object_code, field.props?.sourceObjectCode, field.props?.source_object_code, field.basicProps?.sourceObjectCode, field.basicProps?.source_object_code),
    targetCode: firstText(config.targetCode, config.target_code, field.targetCode, field.target_code, field.props?.targetCode, field.props?.target_code, field.basicProps?.targetCode, field.basicProps?.target_code),
    title: firstText(config.title, config.selectorTitle, field.selectorTitle, field.title),
    buttonText: firstText(config.buttonText, field.buttonText),
    displayFields: firstArray(config.displayFields, field.displayFields, field.props?.displayFields, field.basicProps?.displayFields),
    keywordFields: firstArray(config.keywordFields, field.keywordFields, field.props?.keywordFields, field.basicProps?.keywordFields),
    fieldMappings: config.fieldMappings || config.mappings || field.fieldMappings || field.mappings || field.props?.fieldMappings || field.props?.mappings || field.basicProps?.fieldMappings || field.basicProps?.mappings || [],
    searchParams: config.searchParams || field.searchParams || field.props?.searchParams || field.basicProps?.searchParams || {},
    filterFields: firstFilterFields(config.filterFields, field.filterFields, field.props?.filterFields, field.basicProps?.filterFields),
    multiple: config.multiple === true || field.multiple === true || field.props?.multiple === true || field.basicProps?.multiple === true,
    // 受管查询源（DataSourceBinding 协议对齐）：配置后选择器可查数据集/接口，不再依赖业务对象编码
    querySourceType: firstText(
      config.querySource?.sourceType,
      config.querySourceType,
      field.querySource?.sourceType,
      field.querySourceType,
      field.props?.querySource?.sourceType,
      field.props?.querySourceType,
      field.basicProps?.querySource?.sourceType,
      field.basicProps?.querySourceType,
    ),
    querySourceKey: firstText(
      config.querySource?.sourceKey,
      config.querySourceKey,
      field.querySource?.sourceKey,
      field.querySourceKey,
      field.props?.querySource?.sourceKey,
      field.props?.querySourceKey,
      field.basicProps?.querySource?.sourceKey,
      field.basicProps?.querySourceKey,
    ),
    keywordParam: firstText(
      config.keywordParam,
      field.keywordParam,
      field.props?.keywordParam,
      field.basicProps?.keywordParam,
    ),
  }
}

/** 筛选字段为对象数组，不能走 firstArray 的字符串拆分逻辑 */
function firstFilterFields(...values) {
  for (const value of values) {
    if (Array.isArray(value))
      return value
  }
  return []
}

export function extractSelectorRawRecord(record = {}) {
  return record && typeof record === 'object'
    ? { ...(record._raw || {}), ...record }
    : {}
}

export function readPath(source = {}, path = '') {
  const keys = String(path || '').split('.').filter(Boolean)
  let cursor = source
  for (const key of keys) {
    if (!cursor || typeof cursor !== 'object')
      return undefined
    cursor = cursor[key]
  }
  return cursor
}

function resolveSelectorParamValue(value, context = {}) {
  if (Array.isArray(value))
    return value.map(item => resolveSelectorParamValue(item, context)).filter(item => !isEmptySelectorParam(item))
  if (value && typeof value === 'object') {
    return Object.entries(value).reduce((result, [key, item]) => {
      const resolved = resolveSelectorParamValue(item, context)
      if (!isEmptySelectorParam(resolved))
        result[key] = resolved
      return result
    }, {})
  }
  if (typeof value !== 'string')
    return value
  const text = value.trim()
  const matched = text.match(/^\$\{(.+)\}$/)
  if (!matched)
    return value
  return readSelectorContextPath(context, matched[1])
}

function readSelectorContextPath(context = {}, expression = '') {
  const source = {
    ...(context || {}),
    form: context.form || context.formData || {},
    formData: context.formData || context.form || {},
    record: context.record || context.formData || {},
    row: context.row || {},
    query: context.query || context.route?.query || {},
    params: context.params || context.route?.params || {},
  }
  return readPath(source, expression)
}

function isEmptySelectorParam(value) {
  if (value === null || value === undefined)
    return true
  if (typeof value === 'string')
    return value.trim() === ''
  if (Array.isArray(value))
    return value.length === 0
  if (value && typeof value === 'object')
    return Object.keys(value).length === 0
  return false
}

function mergeSelectorFragments(field = {}) {
  return [
    field.recordSelector,
    field.selector,
    field.selectorConfig,
    field.recordSelectorConfig,
    field.optionSource,
    field.optionSource?.params,
    field.referenceConfig,
    field.objectConfig,
    field.basicProps?.recordSelector,
    field.basicProps?.selector,
    field.basicProps?.selectorConfig,
    field.basicProps?.recordSelectorConfig,
    field.basicProps?.optionSource,
    field.basicProps?.optionSource?.params,
    field.basicProps?.referenceConfig,
    field.basicProps?.objectConfig,
    field.props?.recordSelector,
    field.props?.selector,
    field.props?.selectorConfig,
    field.props?.recordSelectorConfig,
    field.props?.optionSource,
    field.props?.optionSource?.params,
    field.props?.referenceConfig,
    field.props?.objectConfig,
  ].reduce((result, item) => {
    const fragment = normalizeSelectorFragment(item)
    if (fragment)
      return { ...result, ...fragment }
    return result
  }, {})
}

function normalizeSelectorFragment(item) {
  if (item && typeof item === 'object' && !Array.isArray(item))
    return item
  if (typeof item !== 'string' || !item.trim())
    return null
  try {
    const parsed = JSON.parse(item)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  }
  catch {
    return null
  }
}

function firstText(...values) {
  for (const value of values) {
    const text = String(value ?? '').trim()
    if (text)
      return text
  }
  return ''
}

function firstArray(...values) {
  for (const value of values) {
    if (Array.isArray(value))
      return value
    if (typeof value === 'string' && value.trim())
      return value.split(/[,\n]/).map(item => item.trim()).filter(Boolean)
  }
  return []
}
