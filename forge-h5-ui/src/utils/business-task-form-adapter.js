/**
 * 业务待办表单上下文适配器
 * 将 BusinessTaskFormContextVO 的返回数据转换为 PageSectionRenderer / LowcodeForm 所需的格式
 */

import { parseJson } from '@/utils/lowcode-runtime'

/**
 * 将后端 context.fields 转换为 LowcodeForm 所需的 mainFields 格式
 * @param {Array} rawFields - 后端返回的 fields 数组
 * @returns {Array} mainFields - LowcodeForm 格式的字段数组
 */
export function adaptBusinessTaskFields(rawFields = []) {
  return (Array.isArray(rawFields) ? rawFields : [])
    .map(item => {
      const field = String(item?.field || item?.fieldCode || '').trim()
      if (!field) return null
      const dictType = String(item?.dictType || item?.props?.dictType || '').trim() || undefined
      const rawType = String(item?.type || item?.componentType || item?.componentKey || 'input').toLowerCase()
      const type = resolveFieldType(rawType, dictType, item)
      const writable = item?.writable === true
      const readonly = !writable || item?.readonly === true
      const props = {
        ...(item?.props || {}),
        dictType: dictType || undefined,
        readonly,
        disabled: !writable,
      }
      return {
        field,
        fieldCode: field,
        label: item?.label || item?.fieldName || field,
        type,
        props,
        required: item?.required === true && writable,
        readonly,
        hidden: item?.hidden === true,
        formVisible: item?.readable !== false,
        defaultValue: item?.defaultValue ?? item?.props?.defaultValue,
        runtimeRules: item?.runtimeRules || item?.props?.runtimeRules || [],
        options: normalizeFieldOptions(item?.options || item?.props?.options),
      }
    })
    .filter(Boolean)
}

/**
 * 根据后端原始类型、dictType 推断 LowcodeField 支持的类型
 */
function resolveFieldType(rawType, dictType, item) {
  const normalizedType = String(rawType || '').replace(/[-_]/g, '')
  if (dictType) {
    if (String(item?.props?.displayMode || '').toLowerCase() === 'pill' || rawType.includes('pill')) {
      return 'pillSelect'
    }
    return 'dictSelect'
  }
  if (rawType.includes('textarea')) return 'textarea'
  if (rawType.includes('number') || rawType.includes('integer') || rawType.includes('money')) return 'number'
  if (normalizedType.includes('datetimerange')) return 'datetimerange'
  if (normalizedType.includes('daterange')) return 'daterange'
  if (normalizedType.includes('timerange')) return 'timerange'
  if (normalizedType === 'range' || normalizedType.includes('numberrange')) return 'numberrange'
  if (rawType.includes('select') || rawType.includes('picker') || rawType.includes('radio')) return 'select'
  if (rawType.includes('datetime')) return 'datetime'
  if (rawType === 'date' || rawType.includes('date-picker')) return 'date'
  if (rawType.includes('switch') || rawType.includes('boolean')) return 'switch'
  if (rawType.includes('barcode') || rawType.includes('scan')) return 'barcodeScanner'
  if (rawType.includes('file') || rawType.includes('upload')) return 'input'
  return 'input'
}

function normalizeFieldOptions(raw) {
  const source = Array.isArray(raw) ? raw : Array.isArray(raw?.options) ? raw.options : []
  return source.map(item => {
    if (typeof item === 'string' || typeof item === 'number') return { label: String(item), value: item }
    return {
      label: item?.label ?? item?.name ?? item?.text ?? item?.dictLabel ?? String(item?.value ?? item?.id ?? ''),
      value: item?.value ?? item?.id ?? item?.key ?? item?.dictValue ?? '',
    }
  }).filter(option => option.value !== '' && option.value !== undefined)
}

/**
 * 构建默认 pageSections（当后端未返回分区配置时）
 * 将所有字段放入一个 card 类型的分区中
 * @param {Array} fields - mainFields 数组
 * @returns {Array} pageSections
 */
export function buildDefaultPageSections(fields = []) {
  if (!fields.length) return []
  return [{
    sectionId: 'main',
    sectionType: 'card',
    title: '',
    fields: fields.map(f => f.field),
    fieldOverrides: {},
    collapsible: false,
    collapsedByDefault: false,
  }]
}

/**
 * 尝试从 formAssets 或 formRef 中提取已有的 pageSections
 * @param {Object} context - BusinessTaskFormContextVO
 * @returns {Array|null} pageSections 或 null
 */
export function extractPageSections(context = {}) {
  // 尝试从 formAssets 中查找包含 pageSections 的 schema
  const assets = Array.isArray(context.formAssets) ? context.formAssets : []
  for (const asset of assets) {
    const schema = parseJson(asset?.schema, null)
    if (Array.isArray(schema?.pageSections) && schema.pageSections.length) {
      return schema.pageSections
    }
  }
  // 尝试从 formRef 中查找
  const formRef = context.formRef || {}
  const refSchema = parseJson(formRef.formDesignerSchema || formRef.schema, null)
  if (Array.isArray(refSchema?.pageSections) && refSchema.pageSections.length) {
    return refSchema.pageSections
  }
  return null
}

/**
 * 将后端 childrenConfig 转换为 normalizeChildrenConfig 所需的格式
 * @param {Array} rawChildren - 后端返回的 childrenConfig
 * @returns {Array} children - 标准化的子表配置
 */
export function adaptChildrenConfig(rawChildren = []) {
  return (Array.isArray(rawChildren) ? rawChildren : [])
    .map((child, index) => {
      const key = String(child?.key || child?.relationKey || child?.modelCode || `children_${index}`)
      const modelCode = String(child?.modelCode || child?.tableName || key)
      const relationKey = String(child?.relationKey || child?.key || child?.modelCode || key)
      return {
        ...child,
        key,
        modelCode,
        relationKey,
        fields: adaptBusinessTaskFields(child?.fields || []),
        rowActions: Array.isArray(child?.rowActions) ? child.rowActions : [],
        toolbarActions: Array.isArray(child?.toolbarActions) ? child.toolbarActions : [],
      }
    })
}

/**
 * 从 recordData 中提取主表数据
 * @param {Object} recordData - 后端返回的 recordData
 * @returns {Object} mainData
 */
export function extractMainData(recordData = {}) {
  if (!recordData || typeof recordData !== 'object' || Array.isArray(recordData)) return {}
  if (recordData.main && typeof recordData.main === 'object' && !Array.isArray(recordData.main)) {
    return { ...recordData.main }
  }
  const { children, ...main } = recordData
  return { ...main }
}

/**
 * 从 recordData 中提取子表数据
 * @param {Object} recordData - 后端返回的 recordData
 * @returns {Object} childData - { relationKey: [...] }
 */
export function extractChildData(recordData = {}) {
  if (!recordData || typeof recordData !== 'object' || Array.isArray(recordData)) return {}
  if (recordData.children && typeof recordData.children === 'object' && !Array.isArray(recordData.children)) {
    return { ...recordData.children }
  }
  return {}
}

/**
 * 收集所有需要加载的字典类型
 * @param {Array} fields - mainFields
 * @param {Array} children - adaptChildrenConfig 的结果
 * @returns {Set} dictTypes
 */
export function collectDictTypes(fields = [], children = []) {
  const types = new Set()
  const collect = fieldList => {
    fieldList.forEach(field => {
      const dictType = field?.dictType || field?.props?.dictType
      if (dictType && (field.type === 'dictSelect' || field.type === 'pillSelect')) {
        types.add(dictType)
      }
    })
  }
  collect(fields)
  children.forEach(child => collect(child.fields || []))
  return types
}

/**
 * 构建流程交互配置（用于 PageSectionRenderer 的 flowInteraction prop）
 * @param {Object} context - BusinessTaskFormContextVO
 * @returns {Object} flowInteraction
 */
export function buildFlowInteraction(context = {}) {
  const permissions = Array.isArray(context.fieldPermissions) ? context.fieldPermissions : []
  return {
    approvalActions: [],
    timeline: { enabled: false, title: '审批记录' },
    nodePermissions: permissions.map(perm => ({
      nodeKey: String(perm?.nodeKey || perm?.taskDefKey || ''),
      visibleSectionIds: Array.isArray(perm?.visibleSectionIds) ? perm.visibleSectionIds : [],
      readonlySectionIds: Array.isArray(perm?.readonlySectionIds) ? perm.readonlySectionIds : [],
    })),
    callbacks: {},
  }
}

/**
 * 完整适配：将 BusinessTaskFormContextVO 转换为 PageSectionRenderer 所需的全部 props
 * @param {Object} context - BusinessTaskFormContextVO
 * @param {string} mode - 'edit' | 'detail'
 * @returns {Object} { sections, mainFields, mainData, children, childData, flowInteraction, dictTypes }
 */
export function adaptBusinessTaskFormContext(context = {}, mode = 'edit') {
  const mainFields = adaptBusinessTaskFields(context.fields || context.formRef?.fields || [])
  const children = adaptChildrenConfig(context.childrenConfig || [])
  const sections = extractPageSections(context) || buildDefaultPageSections(mainFields)
  const mainData = extractMainData(context.recordData)
  const childData = extractChildData(context.recordData)
  const flowInteraction = buildFlowInteraction(context)
  const dictTypes = collectDictTypes(mainFields, children)
  const currentFlowNodeKey = String(context.taskDefKey || '')
  return {
    sections,
    mainFields,
    mainData,
    children,
    childData,
    flowInteraction,
    dictTypes,
    currentFlowNodeKey,
  }
}
