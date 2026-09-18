/**
 * 数据源绑定通用运行时（DataSourceBinding 协议的纯函数底座）
 *
 * 分层约定：
 * - 本模块只放与具体触发器无关的通用能力：参数映射 / 结果映射 / 安全路径读取 /
 *   查询源响应解包 / 表单初始化（formInit）协议。
 * - field-event-runtime.js 等触发器实现从本模块复用工具与映射函数，避免多套协议并存。
 * - 保持纯函数、无 Vue / 网络依赖，便于单元测试与下拉、子表、选择器等多场景复用。
 */

import { isOrgSelectLikeField, isUserSelectLikeField, resolveSelectionLabelFields } from './selection-label-fields'

// ---------- 通用协议常量 ----------
export const QUERY_SOURCE_TYPES = new Set(['EXTERNAL_API', 'DATASET', 'BUSINESS_OBJECT'])
export const PARAM_SOURCE_TYPES = new Set(['FORM_FIELD', 'CONTEXT_PATH', 'ROUTE_QUERY'])
export const RESULT_MODES = new Set(['ROOT', 'FIRST_ROW'])
export const MISSING_MODES = new Set(['CLEAR', 'KEEP'])
/** 表单初始化默认值来源：登录用户上下文路径 / URL 参数 */
export const CONTEXT_DEFAULT_SOURCE_TYPES = new Set(['CONTEXT_PATH', 'ROUTE_QUERY'])

const IDENTIFIER_PATTERN = /^[a-z][\w-]{0,63}$/i
const PARAM_PATTERN = /^[a-z_][\w.-]{0,127}$/i
const SOURCE_KEY_PATTERN = /^[a-z0-9][\w.:/-]{0,128}$/i
const PATH_SEGMENT_PATTERN = /^[a-z_$][\w$-]*$/i
const UNSAFE_PATH_SEGMENTS = new Set(['__proto__', 'prototype', 'constructor'])
const DANGEROUS_CONFIG_KEYS = new Set([
  'url',
  'uri',
  'header',
  'headers',
  'authorization',
  'authentication',
  'credential',
  'credentials',
  'secret',
  'token',
  'sql',
  'script',
  'handler',
])

// ---------- 通用工具（供触发器运行时与初始化编排共用） ----------

export function isPlainObject(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

export function isSafePath(path) {
  const value = String(path || '').trim()
  if (!value)
    return false
  return value.split('.').every(segment => PATH_SEGMENT_PATTERN.test(segment)
    && !UNSAFE_PATH_SEGMENTS.has(segment)
    && !DANGEROUS_CONFIG_KEYS.has(segment.toLowerCase()))
}

export function readSafePath(root, path) {
  const value = String(path || '').trim()
  if (!value)
    return root
  if (!isSafePath(value))
    return undefined
  let current = root
  for (const segment of value.split('.')) {
    if (current === null || current === undefined || !Object.prototype.hasOwnProperty.call(current, segment))
      return undefined
    current = current[segment]
  }
  return current
}

export function isBlank(value) {
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
}

export function isEmptyResult(value) {
  return value === undefined || value === null || (Array.isArray(value) && value.length === 0)
}

export function selectResult(mode, data) {
  if (mode !== 'FIRST_ROW')
    return data
  if (Array.isArray(data))
    return data[0]
  for (const key of ['records', 'list', 'rows']) {
    if (Array.isArray(data?.[key]))
      return data[key][0]
  }
  return undefined
}

export function unwrapQuerySourceData(response) {
  if (response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'code'))
    return response.data?.data
  if (response?.sourceType && Object.prototype.hasOwnProperty.call(response, 'data'))
    return response.data
  if (response?.data && typeof response.data === 'object' && Object.prototype.hasOwnProperty.call(response.data, 'data'))
    return response.data.data
  return response?.data ?? response
}

export function containsDangerousConfigKey(value, visited = new Set()) {
  if (!value || typeof value !== 'object')
    return false
  if (visited.has(value))
    return true
  visited.add(value)
  for (const key of Object.keys(value)) {
    if (DANGEROUS_CONFIG_KEYS.has(String(key).toLowerCase()))
      return true
    if (containsDangerousConfigKey(value[key], visited))
      return true
  }
  visited.delete(value)
  return false
}

export function normalizeMessage(value, maxLength) {
  return String(value || '').trim().slice(0, maxLength)
}

export {
  DANGEROUS_CONFIG_KEYS,
  IDENTIFIER_PATTERN,
  PARAM_PATTERN,
  PATH_SEGMENT_PATTERN,
  SOURCE_KEY_PATTERN,
  UNSAFE_PATH_SEGMENTS,
}

// ---------- 通用绑定映射（参数入参 / 结果回填） ----------

/**
 * 按绑定的 paramMappings 组装查询入参。
 * 来源三种：FORM_FIELD（表单字段）/ CONTEXT_PATH（运行上下文路径）/ ROUTE_QUERY（URL 参数名）。
 */
export function buildBindingParams(binding, runtime = {}) {
  const params = {}
  for (const mapping of Array.isArray(binding?.paramMappings) ? binding.paramMappings : []) {
    let root
    let path
    if (mapping.source === 'FORM_FIELD') {
      root = runtime.formData || {}
      path = mapping.field
    }
    else if (mapping.source === 'CONTEXT_PATH') {
      root = runtime.context || {}
      path = mapping.path
    }
    else if (mapping.source === 'ROUTE_QUERY') {
      root = runtime.routeQuery || {}
      path = mapping.path
    }
    else {
      continue
    }
    params[mapping.param] = readSafePath(root, path)
  }
  return params
}

/**
 * 找出绑定中标记为必填（paramMappings[].required）但取值为空的参数名。
 * 用于执行前的短路校验：必填参数缺失时不发起请求，避免无意义的全量查询。
 */
export function findMissingRequiredParams(binding, runtime = {}) {
  const missing = []
  for (const mapping of Array.isArray(binding?.paramMappings) ? binding.paramMappings : []) {
    if (mapping?.required !== true)
      continue
    let value
    if (mapping.source === 'FORM_FIELD')
      value = readSafePath(runtime.formData || {}, mapping.field)
    else if (mapping.source === 'CONTEXT_PATH')
      value = readSafePath(runtime.context || {}, mapping.path)
    else if (mapping.source === 'ROUTE_QUERY')
      value = readSafePath(runtime.routeQuery || {}, mapping.path)
    else
      continue
    if (isBlank(value))
      missing.push(mapping.param)
  }
  return missing
}

/**
 * 运行上下文快照：递归拷贝 plain object / 数组（限深防循环），剔除函数引用。
 * 用于 CONTEXT_PATH 参数在规则多次执行间保持取值稳定，避免上下文对象被替换后参数漂移。
 */
export function snapshotRuntimeContext(context, depth = 4) {
  if (Array.isArray(context)) {
    if (depth <= 0)
      return [...context]
    return context.map(item => isPlainObject(item) || Array.isArray(item)
      ? snapshotRuntimeContext(item, depth - 1)
      : item)
  }
  if (!isPlainObject(context))
    return {}
  const snapshot = {}
  for (const key of Object.keys(context)) {
    const value = context[key]
    if (value === null || value === undefined || typeof value === 'function')
      continue
    if (depth > 0 && (isPlainObject(value) || Array.isArray(value))) {
      snapshot[key] = snapshotRuntimeContext(value, depth - 1)
      continue
    }
    if (isPlainObject(value))
      snapshot[key] = { ...value }
    else if (Array.isArray(value))
      snapshot[key] = [...value]
    else
      snapshot[key] = value
  }
  return snapshot
}

/**
 * 按绑定的 resultMappings 将查询结果映射为表单 patch。
 * resultMode ROOT / FIRST_ROW 选择结果根；whenMissing CLEAR / KEEP 决定缺省字段行为。
 */
export function mapBindingResult(binding, data) {
  const selected = selectResult(binding?.resultMode, data)
  const found = !isEmptyResult(selected)
  const patch = {}

  for (const mapping of Array.isArray(binding?.resultMappings) ? binding.resultMappings : []) {
    const value = found ? readSafePath(selected, mapping.from) : undefined
    if (value !== undefined)
      patch[mapping.to] = value
    else if (mapping.whenMissing === 'CLEAR')
      patch[mapping.to] = undefined
  }
  return { found, patch }
}

// ---------- 表单初始化（formInit）协议 ----------
//
// 存储位置：schema.settings.governance.formInit
// 协议结构：
// {
//   contextDefaults: [{ id, field, source: 'CONTEXT_PATH' | 'ROUTE_QUERY', path, enabled }],
//   recordLoad: { enabled, keyNames: ['recordId', ...] },
// }
// initQueries 不单独立项：表单打开后的自动查询复用 fieldEvents 中 trigger=FORM_LOAD 的规则。

/**
 * 归一化 formInit 配置，拦截非法字段 / 路径，保证运行时只消费白名单结构。
 */
export function normalizeFormInitConfig(config) {
  const source = isPlainObject(config) ? config : {}
  const contextDefaults = []
  const seenFields = new Set()

  for (const item of Array.isArray(source.contextDefaults) ? source.contextDefaults : []) {
    if (!isPlainObject(item))
      continue
    const field = String(item.field || '').trim()
    const sourceType = String(item.source || 'CONTEXT_PATH').trim().toUpperCase()
    const path = String(item.path || '').trim()
    if (!field || !PATH_SEGMENT_PATTERN.test(field) || seenFields.has(field))
      continue
    if (!CONTEXT_DEFAULT_SOURCE_TYPES.has(sourceType) || !isSafePath(path))
      continue
    seenFields.add(field)
    contextDefaults.push({
      id: String(item.id || `ctx_default_${field}`).trim().slice(0, 80) || `ctx_default_${field}`,
      field,
      source: sourceType,
      path,
      enabled: item.enabled !== false,
    })
  }

  const recordLoadSource = isPlainObject(source.recordLoad) ? source.recordLoad : {}
  const keyNames = []
  const seenKeys = new Set()
  for (const key of Array.isArray(recordLoadSource.keyNames) ? recordLoadSource.keyNames : []) {
    const name = String(key || '').trim()
    // keyNames 同时充当 URL 参数名与上下文路径，两种形态都必须通过安全校验
    if (!name || seenKeys.has(name) || (!isSafePath(name) && !PATH_SEGMENT_PATTERN.test(name)))
      continue
    seenKeys.add(name)
    keyNames.push(name)
  }

  return {
    contextDefaults,
    recordLoad: {
      enabled: recordLoadSource.enabled === true,
      keyNames,
    },
  }
}

/**
 * 计算 contextDefaults 生成的表单 patch（纯映射，不含“是否应用”的时机判断）。
 *
 * 写入规则（优先级：存量记录值 > 查询回填值 > 初始化默认值 > 静态默认值）：
 * - 目标字段当前为空 → 写入初始化默认值；
 * - 目标字段当前值等于静态默认值（组件 defaultValue）→ 覆盖为初始化默认值；
 * - 其余情况（已有存量值）→ 保留不动。
 */
export function buildContextDefaultsPatch(config, runtime = {}) {
  const { contextDefaults } = normalizeFormInitConfig(config)
  const formData = isPlainObject(runtime.formData) ? runtime.formData : {}
  const context = isPlainObject(runtime.context) ? runtime.context : {}
  const routeQuery = isPlainObject(runtime.routeQuery) ? runtime.routeQuery : {}
  const fieldStaticDefaults = isPlainObject(runtime.fieldStaticDefaults) ? runtime.fieldStaticDefaults : {}
  const patch = {}

  for (const item of contextDefaults) {
    if (item.enabled === false)
      continue
    const value = item.source === 'ROUTE_QUERY'
      ? readSafePath(routeQuery, item.path)
      : readSafePath(context, item.path)
    if (isBlank(value))
      continue

    const current = formData[item.field]
    if (!isBlank(current)) {
      const staticDefault = fieldStaticDefaults[item.field]
      if (staticDefault === undefined || !isLooseEqual(current, staticDefault))
        continue
    }
    patch[item.field] = normalizeContextValue(value)
  }
  return patch
}

/** 选择器 ID 上下文路径 → 对应姓名/名称上下文路径 */
const CONTEXT_SELECTION_LABEL_PATHS = {
  'currentUser.userId': 'currentUser.realName',
  'currentUser.staffId': 'currentUser.staffName',
  'currentUser.activeOrgId': 'currentUser.activeOrgName',
}

/**
 * 初始化默认值的选择器 label 伴随字段适配（就地补充 patch）。
 *
 * 人员/组织选择器的值是 ID，显示名靠 label 伴随字段（约定 xxxName 或显式
 * labelValueField）。当 CONTEXT_PATH 默认值把 currentUser.userId / staffId /
 * activeOrgId 填入选择器字段时，自动把登录用户的姓名/组织名写入 label
 * 伴随字段，避免表单初始化后选择器只显示数字 ID。
 *
 * 写入规则与 AiFormItem 手动选择时的 patchSelectionLabelValue 保持一致：
 * 显式配置的 labelValueField 优先，第一个候选无条件写，其余候选仅在表单
 * 数据已存在该键时才写，避免凭空造出多余提交字段。
 */
export function appendSelectionLabelContextDefaults(patch, config, runtime = {}) {
  if (!isPlainObject(patch) || !Object.keys(patch).length)
    return patch
  const { contextDefaults } = normalizeFormInitConfig(config)
  if (!contextDefaults.length)
    return patch
  const context = isPlainObject(runtime.context) ? runtime.context : {}
  const formData = isPlainObject(runtime.formData) ? runtime.formData : {}
  const fieldMap = new Map((Array.isArray(runtime.fields) ? runtime.fields : [])
    .map(field => [String(field?.field || '').trim(), field])
    .filter(([name]) => name))

  for (const item of contextDefaults) {
    if (item.enabled === false || item.source !== 'CONTEXT_PATH')
      continue
    const labelPath = CONTEXT_SELECTION_LABEL_PATHS[item.path]
    if (!labelPath)
      continue
    // 该默认值必须实际生效（patch 中存在）才补姓名；被存量值保护的跳过
    if (!Object.prototype.hasOwnProperty.call(patch, item.field))
      continue
    const field = fieldMap.get(item.field)
    if (!field)
      continue
    const selectionType = isUserSelectLikeField(field)
      ? 'user'
      : isOrgSelectLikeField(field)
        ? 'org'
        : ''
    if (!selectionType)
      continue
    const labelValue = normalizeContextValue(readSafePath(context, labelPath))
    if (isBlank(labelValue))
      continue
    const candidates = resolveSelectionLabelFields(field, selectionType)
    const explicitLabelField = [field.props?.labelValueField, field.labelValueField]
      .map(value => String(value || '').trim())
      .find(Boolean) || ''
    const patchTargets = explicitLabelField && candidates.includes(explicitLabelField)
      ? [explicitLabelField, ...candidates.filter(candidate => candidate !== explicitLabelField)]
      : candidates
    patchTargets.forEach((candidate, index) => {
      if (index === 0 || Object.prototype.hasOwnProperty.call(formData, candidate))
        patch[candidate] = labelValue
    })
  }
  return patch
}

/**
 * 按 recordLoad.keyNames 解析存量数据定位 ID。
 * 每个 key 先查 URL 参数，再查上下文路径（支持 currentUser.staffId 等点路径），命中即返回。
 */
export function resolveFormInitRecordId(config, runtime = {}) {
  const { recordLoad } = normalizeFormInitConfig(config)
  if (!recordLoad.enabled || !recordLoad.keyNames.length)
    return ''
  const routeQuery = isPlainObject(runtime.routeQuery) ? runtime.routeQuery : {}
  const context = isPlainObject(runtime.context) ? runtime.context : {}

  for (const key of recordLoad.keyNames) {
    if (PATH_SEGMENT_PATTERN.test(key)) {
      const fromRoute = routeQuery[key]
      if (!isBlank(fromRoute))
        return normalizeContextValue(fromRoute)
    }
    const fromContext = readSafePath(context, key)
    if (!isBlank(fromContext))
      return normalizeContextValue(fromContext)
  }
  return ''
}

/** URL / 上下文取值归一：同名多值取首个，其余保持原样 */
function normalizeContextValue(value) {
  if (Array.isArray(value))
    return value.length ? value[0] : undefined
  return value
}

/** 静态默认值宽松比较：严格相等或字符串形态相等都视为“还是默认值” */
function isLooseEqual(current, staticDefault) {
  if (current === staticDefault)
    return true
  return String(current) === String(staticDefault)
}
