/**
 * 与 PC 的 resolveAiFormSchemaFromUiDocument 对齐：组件树决定审批主表实际画布，
 * fields/fieldPermissions 仍是字段定义和审批权限来源。
 */
import { adaptBusinessTaskFields } from './business-task-form-adapter.js'
import { parseJson } from './lowcode-runtime.js'
import { normalizeMobileComponentType, resolveMobileComponent } from '../components/lowcode/mobile-component-registry.js'

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

function resolveFieldNode(node, source, permissions) {
  const field = fieldKey(node)
  const type = preferredType(node, source)
  const raw = {
    ...(source || {}),
    field,
    type,
    componentKey: node.componentKey || source?.componentKey,
    label: source?.label || node.label || field,
    props: { ...(source?.props || {}), ...(node.props || {}) },
    dictType: node.dictType || source?.dictType || node.props?.dictType,
    required: source?.required === true || node.required === true,
    readable: source?.readable !== false && source?.visible !== false && node.visible !== false,
    writable: source ? (source.writable === true || (source.writable == null && source.editable === true)) : node.editable === true,
    readonly: source?.readonly === true || node.editable === false,
    hidden: source?.hidden === true || node.visible === false,
  }
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
    if (!section || (section.sectionType && section.sectionType !== 'card')) continue
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
      sections: mapped.fields.length
        ? [{ sectionId: 'main', sectionType: 'card', title: '', fields: mapped.fields.map(field => field.field) }]
        : [],
      hasComponentTree: true,
    }
  }
  return sectionFallback(document, adaptBusinessTaskFields(rawFields, permissions))
}
