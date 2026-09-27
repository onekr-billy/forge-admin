import { UI_DOCUMENT_PROTOCOL_VERSION, UI_DOCUMENT_UI_TYPES } from './constants'

function text(value) {
  if (value == null)
    return null
  const next = String(value).trim()
  return next || null
}

function firstNonBlank(...values) {
  for (const value of values) {
    const next = text(value)
    if (next)
      return next
  }
  return null
}

function asBoolean(value) {
  if (typeof value === 'boolean')
    return value
  if (value == null)
    return null
  const raw = String(value).trim().toLowerCase()
  if (raw === 'true' || raw === '1')
    return true
  if (raw === 'false' || raw === '0')
    return false
  return null
}

function readObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? { ...value } : {}
}

function readArray(value) {
  return Array.isArray(value) ? value : []
}

function copyStringList(raw) {
  return readArray(raw).map(item => text(item)).filter(Boolean)
}

function indexPermissions(fieldPermissions = []) {
  const map = new Map()
  readArray(fieldPermissions).forEach((item) => {
    if (!item || typeof item !== 'object')
      return
    const field = firstNonBlank(item.field, item.fieldCode)
    if (!field)
      return
    const readable = asBoolean(item.readable) !== false && asBoolean(item.visible) !== false
    const writable = asBoolean(item.writable) === true || asBoolean(item.editable) === true
    map.set(field, { visible: readable, writable })
  })
  return map
}

function compileComponentNode(raw = {}, permissionMap = new Map()) {
  const binding = readObject(raw.fieldBinding)
  const props = readObject(raw.props)
  const validation = readObject(raw.validation)
  const layout = readObject(raw.layout)
  const field = firstNonBlank(binding.fieldCode, raw.field, raw.fieldCode, props.field)
  const type = firstNonBlank(raw.componentKey, raw.type, raw.componentType, 'input')

  const node = {
    type,
    id: firstNonBlank(raw.id, field),
  }
  if (field) {
    node.field = field
    node.fieldCode = field
    node.path = field
  }
  node.label = firstNonBlank(raw.label, props.label, props.title, field)
  node.required = validation.required === true

  const permission = field ? permissionMap.get(field) : null
  // 无权限 overlay 时：默认可写（低代码设计态）；显式 visibility.readonly / editable 再收紧。
  // 审批端通过 fieldPermissions 传入时走 permission 分支，仍默认偏只读。
  let visible = true
  let editable = true
  if (permission) {
    visible = permission.visible
    editable = permission.writable
  }
  else if (raw.visible === false || raw.visibility?.hidden === true) {
    visible = false
  }
  if (!permission && (raw.editable != null || raw.visibility?.readonly != null)) {
    editable = raw.editable === true || raw.visibility?.readonly === false
  }
  node.visible = visible
  node.editable = editable

  const dictType = firstNonBlank(props.dictType, raw.dictType)
  if (dictType)
    node.dictType = dictType

  if (Object.keys(props).length)
    node.props = props

  // 轻量布局扩展：可选，渲染端不认识可忽略
  if (layout.span != null)
    node.span = layout.span
  if (layout.gridStyle)
    node.gridStyle = layout.gridStyle
  if (layout.align)
    node.align = layout.align

  return node
}

function walkComponents(components, permissionMap) {
  const out = []
  readArray(components).forEach((raw) => {
    if (!raw || typeof raw !== 'object')
      return
    const node = compileComponentNode(raw, permissionMap)
    const children = walkComponents(raw.children, permissionMap)
    if (children.length)
      node.children = children
    out.push(node)
  })
  return out
}

function resolveComponents(formSchema = {}) {
  const root = readArray(formSchema.components)
  if (root.length)
    return root
  const settings = readObject(formSchema.settings)
  return readArray(settings.components)
}

function defaultSection(resolvedFields = [], components = []) {
  const fieldCodes = []
  const seen = new Set()
  readArray(resolvedFields).forEach((field) => {
    if (!field || typeof field !== 'object')
      return
    if (text(field.childKey))
      return
    const code = firstNonBlank(field.field, field.fieldCode)
    if (code && !seen.has(code)) {
      seen.add(code)
      fieldCodes.push(code)
    }
  })
  if (!fieldCodes.length) {
    const collect = (nodes) => {
      readArray(nodes).forEach((component) => {
        const code = firstNonBlank(component?.field, component?.fieldCode)
        if (code && !seen.has(code)) {
          seen.add(code)
          fieldCodes.push(code)
        }
        if (Array.isArray(component?.children))
          collect(component.children)
      })
    }
    collect(components)
  }
  return {
    type: 'Section',
    sectionId: 'main',
    sectionType: 'card',
    title: '',
    fields: fieldCodes,
    fieldOverrides: {},
    collapsible: false,
    collapsedByDefault: false,
  }
}

function compileSections(formSchema = {}, resolvedFields = [], components = []) {
  const pageSections = readArray(formSchema.pageSections)
  if (pageSections.length) {
    const sections = pageSections.map((raw, index) => {
      if (!raw || typeof raw !== 'object')
        return null
      return {
        type: 'Section',
        sectionId: firstNonBlank(raw.sectionId, `section_${index + 1}`),
        sectionType: firstNonBlank(raw.sectionType, 'card'),
        title: raw.title == null ? '' : String(raw.title),
        fields: copyStringList(raw.fields),
        fieldOverrides: readObject(raw.fieldOverrides),
        collapsible: raw.collapsible === true,
        collapsedByDefault: raw.collapsedByDefault === true,
      }
    }).filter(Boolean)
    if (sections.length)
      return sections
  }
  return [defaultSection(resolvedFields, components)]
}

/**
 * 将设计态 formDesignerSchema（或单表 schema）编译为运行态 uiDocument。
 * 与后端 TaskFormUiDocumentCompiler 对齐，保证审批 / 业务页 / 后续 H5 同构。
 *
 * @param {object} formSchema 设计器 schema（可含 pageSections / components）
 * @param {string} formKey
 * @param {object[]} resolvedFields 已解析字段（权限与类型的 SoT，可空）
 * @param {object[]} fieldPermissions 可选权限 overlay
 * @param {{ uiType?: string }} [options]
 */
export function compileUiDocument(
  formSchema = {},
  formKey = '',
  resolvedFields = [],
  fieldPermissions = [],
  options = {},
) {
  const permissionMap = indexPermissions(fieldPermissions)
  const components = walkComponents(resolveComponents(formSchema), permissionMap)
  const sections = compileSections(formSchema, resolvedFields, components)
  return {
    version: UI_DOCUMENT_PROTOCOL_VERSION,
    uiType: options.uiType || UI_DOCUMENT_UI_TYPES.BUSINESS_OBJECT,
    formKey: text(formKey) || '',
    sections,
    components,
    actions: [],
    // 预留扩展袋：不参与必填校验，客户端可忽略
    extensions: readObject(options.extensions),
  }
}

/**
 * 从多表单设计器产物中取出当前表单 schema 再编译。
 */
export function compileUiDocumentFromDesigner(
  formDesignerSchema = {},
  {
    formKey = '',
    resolvedFields = [],
    fieldPermissions = [],
    uiType = UI_DOCUMENT_UI_TYPES.LOWCODE_FORM,
  } = {},
) {
  const root = formDesignerSchema && typeof formDesignerSchema === 'object' ? formDesignerSchema : {}
  const forms = readArray(root.forms)
  let schema = root
  let resolvedKey = formKey || text(root.formKey) || ''

  if (forms.length) {
    const selected = forms.find(item => item?.formKey === formKey)
      || forms.find(item => item?.formKey === root.defaultFormKey)
      || forms.find(item => item?.formKey === root.settings?.defaultFormKey)
      || forms[0]
    schema = selected?.schema || selected || {}
    resolvedKey = selected?.formKey || formKey || text(root.formKey) || ''
  }

  return compileUiDocument(schema, resolvedKey, resolvedFields, fieldPermissions, { uiType })
}
