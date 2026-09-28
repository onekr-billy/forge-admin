/**
 * 与 PC 的 resolveAiFormSchemaFromUiDocument 对齐：组件树决定审批主表实际画布，
 * fields/fieldPermissions 仍是字段定义和审批权限来源。
 */
import { adaptBusinessTaskFields } from './business-task-form-adapter.js'
import { parseJson } from './lowcode-runtime.js'
import { normalizeMobileComponentType, resolveMobileComponent } from '../components/lowcode/mobile-component-registry.js'
import { normalizeMobileFieldContract } from './mobile-field-contract.js'

const DOCUMENT_LAYOUT_ALIASES = {
  elTabPane: 'tabPane',
  elCollapseItem: 'collapseItem',
  elDivider: 'divider',
  AiFormSectionTitle: 'formSectionTitle',
  aiFormSectionTitle: 'formSectionTitle',
  FormSectionTitle: 'formSectionTitle',
  fcTitle: 'groupTitle',
  title: 'groupTitle',
  tableGrid: 'table',
}

function fieldKey(item) {
  return String(item?.field || item?.fieldCode || '').trim()
}

function isWeakType(type) {
  return ['', 'input', 'text', 'string'].includes(String(type || '').trim())
}

function preferredType(node, source = {}) {
  const documentType = String(node.componentKey || node.type || node.componentType || '').trim()
  const sourceType = String(source.componentKey || source.type || source.componentType || '').trim()
  const normalizedDocumentType = normalizeMobileComponentType(documentType)
  if (documentType && !isWeakType(normalizedDocumentType)) return documentType
  if (sourceType && !isWeakType(normalizeMobileComponentType(sourceType))) return sourceType
  return documentType || sourceType || 'input'
}

function normalizePermissionField(field = '') {
  return String(field || '').replace(/[_-]/g, '').toLowerCase()
}

function normalizePermissionList(value) {
  const parsed = parseJson(value, value)
  if (Array.isArray(parsed)) return parsed
  if (!parsed || typeof parsed !== 'object') return []
  const candidates = parsed.fields || parsed.fieldPermissions || parsed.permissions || []
  return Array.isArray(candidates) ? candidates : []
}

function resolveMainFieldPermission(permissions, field) {
  const normalizedField = normalizePermissionField(field)
  return normalizePermissionList(permissions).find((permission) => {
    if (String(permission?.scope || '').toLowerCase() === 'child') return false
    return normalizePermissionField(permission?.field || permission?.fieldCode) === normalizedField
  })
}

function permissionFlag(source, primary, legacy, fallback) {
  if (typeof source?.[primary] === 'boolean') return source[primary]
  if (typeof source?.[legacy] === 'boolean') return source[legacy]
  return fallback
}

function resolveFieldNode(node, source, permissions) {
  const field = fieldKey(node)
  const type = preferredType(node, source)
  const approvalPermission = resolveMainFieldPermission(permissions, field)
  const validation = {
    ...(source?.props?.validation || {}),
    ...(source?.validation || {}),
    ...(node.props?.validation || {}),
    ...(node.validation || {}),
  }
  const visibility = {
    ...(source?.visibility || {}),
    ...(node.visibility || {}),
  }
  // uiDocument 由后端按当前节点权限编译，editable=false 与 readonly/disabled
  // 一样是硬约束。审批 writable 只能进一步收紧权限，不能把明确的只读字段重新打开。
  const hardReadonly = source?.editable === false
    || node.editable === false
    || source?.props?.readonly === true
    || source?.props?.disabled === true
    || node.props?.readonly === true
    || node.props?.disabled === true
    || visibility.readonly === true
  const approvalWritable = approvalPermission
    ? permissionFlag(approvalPermission, 'writable', 'editable', false)
    : null
  const raw = normalizeMobileFieldContract({
    ...(source || {}),
    ...node,
    field,
    type,
    componentKey: node.componentKey || source?.componentKey,
    label: source?.label || node.label || field,
    props: { ...(source?.props || {}), ...(node.props || {}) },
    dictType: node.dictType || source?.dictType || node.props?.dictType,
    validation,
    visibility,
    rules: node.rules || validation.rules || source?.rules || [],
    required: source?.required === true || node.required === true || validation.required === true,
    requiredMessage: source?.requiredMessage || node.requiredMessage || validation.requiredMessage,
    readable: source?.readable !== false && source?.visible !== false && node.visible !== false && visibility.hidden !== true,
    writable: approvalPermission
      ? approvalWritable && !hardReadonly
      : source
        ? (source.writable === true || (source.writable == null && source.editable === true)) && !hardReadonly
        : node.editable !== false && !hardReadonly,
    readonly: hardReadonly || (!approvalPermission && source?.readonly === true),
    hidden: source?.hidden === true || node.visible === false || visibility.hidden === true,
    defaultValue: node.defaultValue ?? node.props?.defaultValue ?? source?.defaultValue ?? source?.props?.defaultValue,
  })
  const adapted = adaptBusinessTaskFields([raw], permissions)[0]
  if (!adapted || adapted.hidden || adapted.formVisible === false) return null
  return {
    ...(source || {}),
    ...node,
    ...adapted,
    id: node.id,
    componentKey: node.componentKey || source?.componentKey,
    span: node.span ?? source?.span,
    gridStyle: node.gridStyle || source?.gridStyle,
    align: node.align || source?.align,
  }
}

function normalizeDocumentSections(document, fields) {
  const fieldKeys = new Set((Array.isArray(fields) ? fields : []).map(field => fieldKey(field)).filter(Boolean))
  const sections = []
  for (const [index, source] of (Array.isArray(document?.sections) ? document.sections : []).entries()) {
    if (!source || typeof source !== 'object') continue
    const sectionType = String(source.sectionType || source.type || 'card').toLowerCase()
    const sectionId = String(source.sectionId || source.id || `section_${index}`)
    if (sectionType === 'child_table') {
      sections.push({ ...source, sectionId, sectionType: 'child_table' })
      continue
    }
    if (sectionType !== 'card') continue
    const sectionFields = (Array.isArray(source.fields) ? source.fields : [])
      .map(value => String(value || '').trim())
      .filter(value => fieldKeys.has(value))
    if (sectionFields.length) sections.push({ ...source, sectionId, sectionType: 'card', fields: sectionFields })
  }
  if (!sections.some(section => section.sectionType === 'card') && fieldKeys.size) {
    sections.unshift({ sectionId: 'main', sectionType: 'card', title: '', fields: [...fieldKeys] })
  }
  return sections
}

function mapComponents(components, sourceFields, permissions) {
  const fieldMap = new Map(sourceFields.map(item => [fieldKey(item), item]).filter(([key]) => key))
  const usedFields = new Map()

  function visit(items) {
    const result = []
    for (const node of Array.isArray(items) ? items : []) {
      if (!node || typeof node !== 'object' || node.visible === false) continue
      const field = fieldKey(node)
      const type = String(node.componentKey || node.type || node.componentType || '').trim()
      const descriptor = resolveMobileComponent(DOCUMENT_LAYOUT_ALIASES[type] || type)
      const children = Array.isArray(node.children) ? node.children : []
      if (descriptor.kind === 'layout' || (!field && children.length)) {
        const mappedChildren = visit(children)
        if (!mappedChildren.length && !['divider', 'formSectionTitle', 'groupTitle'].includes(descriptor.type)) continue
        const layoutNode = {
          ...node,
          nodeType: descriptor.kind === 'layout' ? descriptor.type : 'grid',
          children: mappedChildren,
        }
        delete layoutNode.field
        delete layoutNode.fieldCode
        delete layoutNode.prop
        result.push(layoutNode)
        continue
      }
      if (!field) continue
      const mapped = resolveFieldNode(node, fieldMap.get(field), permissions)
      if (!mapped) continue
      usedFields.set(field, mapped)
      result.push(mapped)
    }
    return result
  }

  return { nodes: visit(components), fields: [...usedFields.values()] }
}

function sectionFallback(document, fields) {
  const fieldMap = new Map(fields.map(field => [field.field, field]))
  const ordered = []
  const sections = []
  for (const section of Array.isArray(document.sections) ? document.sections : []) {
    if (!section) continue
    if (section.sectionType === 'child_table') {
      sections.push({ ...section, sectionType: 'child_table' })
      continue
    }
    if (section.sectionType && section.sectionType !== 'card') continue
    const sectionFields = []
    for (const code of Array.isArray(section.fields) ? section.fields : []) {
      const key = String(code || '').trim()
      if (!fieldMap.has(key)) continue
      sectionFields.push(key)
      ordered.push(fieldMap.get(key))
      fieldMap.delete(key)
    }
    if (sectionFields.length) sections.push({ ...section, sectionType: 'card', fields: sectionFields })
  }
  const remaining = [...fieldMap.values()]
  if (remaining.length) {
    ordered.push(...remaining)
    sections.push({ sectionId: sections.length ? 'main:remaining' : 'main', sectionType: 'card', title: '', fields: remaining.map(field => field.field) })
  }
  return { fields: ordered, nodes: [], sections, hasComponentTree: false }
}

export function resolveTaskUiDocument(context, fallbackFields = []) {
  const document = parseJson(context?.uiDocument, null)
  const version = String(context?.protocolVersion ?? document?.version ?? '').trim()
  if (version !== '1' || !document || typeof document !== 'object' || Array.isArray(document)) return null

  const rawFields = Array.isArray(context?.fields) && context.fields.length ? context.fields : fallbackFields
  const permissions = context?.fieldPermissions || []
  const components = Array.isArray(document.components) ? document.components : []
  if (components.length) {
    const mapped = mapComponents(components, rawFields, permissions)
    return {
      ...mapped,
      sections: normalizeDocumentSections(document, mapped.fields),
      hasComponentTree: true,
    }
  }
  return sectionFallback(document, adaptBusinessTaskFields(rawFields, permissions))
}
