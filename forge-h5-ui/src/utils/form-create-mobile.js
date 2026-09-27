import { parseJson } from './lowcode-runtime.js'
import { adaptBusinessTaskFields } from './business-task-form-adapter.js'

const LAYOUT_TYPES = new Set(['row', 'col', 'fcrow', 'fccol', 'grid', 'tabs', 'tabpane', 'collapse', 'collapseitem', 'card', 'divider'])

function typeName(value) {
  return String(value || '').replace(/[\s_-]/g, '').toLowerCase()
    .replace(/^(el|n|fc)(?=input|select|radio|checkbox|switch|date|time|upload|cascader|tree|slider|rate|color)/, '')
}

function schemaRules(source) {
  const parsed = parseJson(source, source)
  if (Array.isArray(parsed)) return parsed
  if (!parsed || typeof parsed !== 'object') return []
  return parsed.rule || parsed.rules || parsed.schema || parsed.fields || parsed.children || []
}

function fieldType(rule) {
  const type = typeName(rule.type || rule.component || rule.componentKey)
  const propType = typeName(rule.props?.type)
  if (['group', 'tableform', 'subform', 'array'].includes(type)) return 'array'
  if (type === 'input' && propType === 'textarea') return 'textarea'
  if (['inputnumber', 'number'].includes(type)) return 'number'
  if (['datepicker', 'date'].includes(type)) return propType || 'date'
  if (['timepicker', 'time'].includes(type)) return propType === 'timerange' ? 'timerange' : 'time'
  if (type === 'upload') return rule.props?.listType === 'picture-card' ? 'imageUpload' : 'fileUpload'
  return type || 'input'
}

function fieldKey(rule) {
  return String(rule.field || rule.fieldCode || rule.name || rule.props?.field || rule.props?.fieldCode || rule.fieldBinding?.fieldCode || rule._forge?.fieldBinding?.fieldCode || '').trim()
}

function collect(source, seen = new Set()) {
  const result = []
  const visit = (nodes) => {
    if (Array.isArray(nodes)) { nodes.forEach(visit); return }
    if (!nodes || typeof nodes !== 'object') return
    const rawType = typeName(nodes.type || nodes.component || nodes.componentKey)
    const key = fieldKey(nodes)
    if (key && !key.startsWith('ref_') && !LAYOUT_TYPES.has(rawType) && !seen.has(key)) {
      seen.add(key)
      const props = { ...(nodes.props || {}) }
      const type = fieldType(nodes)
      const validation = Array.isArray(nodes.validate) ? nodes.validate : []
      const rules = Array.isArray(nodes.rules) ? nodes.rules : []
      const required = nodes.required === true || [...validation, ...rules].some(rule => rule?.required === true)
      const options = nodes.options || props.options || []
      const field = {
        field: key,
        label: nodes.title || nodes.label || nodes.fieldName || props.title || props.label || key,
        type,
        props: { ...props, ...(nodes.placeholder ? { placeholder: nodes.placeholder } : {}) },
        options,
        required,
        writable: nodes.disabled !== true && nodes.readonly !== true && props.disabled !== true && props.readonly !== true,
        readonly: nodes.readonly === true || props.readonly === true,
        hidden: nodes.hidden === true,
        defaultValue: nodes.value ?? props.defaultValue,
      }
      if (type === 'array') {
        const columns = props.columns || nodes.columns || []
        const childRules = rawType === 'tableform' ? columns.flatMap(column => column.rule || []) : (props.rule || nodes.rule || nodes.children || [])
        field.itemSchema = collect(childRules)
        field.arrayConfig = {
          allowCreate: props.addable !== false && props.button !== false,
          allowUpdate: props.disabled !== true,
          allowDelete: props.deletable !== false && props.button !== false,
          displayMode: rawType === 'tableform' ? 'table' : 'list',
          min: Number(props.min || 0),
          max: Number(props.max || 0),
        }
      }
      result.push(field)
      if (type === 'array') return
    }
    visit(nodes.children)
    visit(nodes.rule)
    visit(nodes.rules)
    visit(nodes.columns)
  }
  visit(schemaRules(source))
  return result
}

/** Match PC formCreateToAiSchema's field extraction, then let the mobile field adapter apply node permissions. */
export function formCreateToMobileFields(source) {
  return collect(source)
}

export function resolveTaskFormFields(info = {}) {
  const candidates = [
    formCreateToMobileFields(info?.formJson),
    parseJson(info?.formJson),
    info?.fields,
    info?.formRef?.fields,
    info?.fieldCatalog,
    info?.formRef?.fieldCatalog,
  ]
  return candidates.find(candidate => adaptBusinessTaskFields(candidate).length) || []
}

export function hasDeclaredFormCreateRules(source) {
  if (!source) return false
  const schema = parseJson(source, null)
  if (schema == null) return true
  if (Array.isArray(schema)) return schema.length > 0
  if (typeof schema !== 'object') return true
  const rules = schema.rule || schema.rules || schema.schema || schema.fields || schema.children
  return Array.isArray(rules) ? rules.length > 0 : Boolean(schema.type && schema.field)
}
