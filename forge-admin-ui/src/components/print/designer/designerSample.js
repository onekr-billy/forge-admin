import { readOwnPath, resolveBinding, resolveCollection } from '../protocol/binding'
import { formatValue } from '../protocol/formatters'
import { isSafeFieldPath } from '../protocol/validate'

const SAMPLE_DATE = '2026-09-19 10:30:00'

function ownObject(target, key) {
  if (!Object.hasOwn(target, key) || !target[key] || typeof target[key] !== 'object' || Array.isArray(target[key]))
    target[key] = {}
  return target[key]
}

function writeOwnPath(target, path, value) {
  if (!isSafeFieldPath(path))
    return
  const parts = path.split('.')
  let current = target
  for (const part of parts.slice(0, -1)) current = ownObject(current, part)
  current[parts.at(-1)] = value
}

function explicitSample(field) {
  for (const key of ['sampleValue', 'example', 'defaultValue']) {
    const value = field?.[key]
    if (value === null || ['string', 'number', 'boolean'].includes(typeof value))
      return value
  }
  return undefined
}

export function sampleFieldValue(field, rowIndex = -1) {
  const explicit = explicitSample(field)
  if (explicit !== undefined)
    return explicit
  const suffix = rowIndex >= 0 ? `${rowIndex + 1}` : '示例'
  if (field?.type === 'MONEY')
    return 128800 + Math.max(0, rowIndex) * 23600
  if (field?.type === 'NUMBER')
    return rowIndex >= 0 ? rowIndex + 1 : 128
  if (field?.type === 'DATE')
    return SAMPLE_DATE
  if (field?.type === 'BOOLEAN')
    return rowIndex % 2 !== 1
  if (field?.type === 'IMAGE')
    return ''
  return `${field?.label || field?.path?.split('.').at(-1) || '文本'}${suffix}`
}

export function createDesignerSampleContext(catalog = []) {
  const context = { main: {}, children: {}, flow: {}, system: { generatedAt: SAMPLE_DATE, pageNumber: 1, totalPages: 1 } }
  const collections = catalog.filter(field => field.type === 'COLLECTION' && isSafeFieldPath(field.path))
  const collectionPaths = collections.map(field => field.path).sort((a, b) => b.length - a.length)

  for (const collection of collections) {
    const children = catalog.filter(field => field.type !== 'COLLECTION' && field.path.startsWith(`${collection.path}.`))
    const rows = [0, 1, 2].map((rowIndex) => {
      const row = {}
      for (const field of children)
        writeOwnPath(row, field.path.slice(collection.path.length + 1), sampleFieldValue(field, rowIndex))
      return row
    })
    writeOwnPath(context, collection.path, rows)
  }

  for (const field of catalog.filter(item => item.type !== 'COLLECTION')) {
    if (!collectionPaths.some(path => field.path.startsWith(`${path}.`)))
      writeOwnPath(context, field.path, sampleFieldValue(field))
  }
  return context
}

export function hasDesignerData(value, seen = new WeakSet()) {
  if (value === null || value === undefined || value === '')
    return false
  if (['string', 'number', 'boolean'].includes(typeof value))
    return true
  if (typeof value !== 'object' || seen.has(value))
    return false
  seen.add(value)
  return Object.values(value).some(item => hasDesignerData(item, seen))
}

export function fieldLabel(catalog, path) {
  return catalog.find(field => field.path === path)?.label || path?.split('.').at(-1) || '字段'
}

export function designerBindingValue(binding, context) {
  try {
    return binding ? resolveBinding(binding, context) : null
  }
  catch {
    return null
  }
}

export function designerBindingText(binding, format, catalog, context) {
  const value = designerBindingValue(binding, context)
  try {
    const text = formatValue(value, format)
    if (text !== '')
      return text
  }
  catch {
    // Invalid sample formatting should not make the editor unusable.
  }
  return binding?.source === 'FIELD' ? fieldLabel(catalog, binding.path) : ''
}

export function designerTableRows(section, catalog, context, limit = 3) {
  let records = []
  try {
    records = resolveCollection(section.collectionPath, context).slice(0, limit)
  }
  catch {
    return []
  }
  return records.map((record, rowIndex) => section.columns.map((column) => {
    const field = catalog.find(item => item.path === `${section.collectionPath}.${column.field}`)
    try {
      const value = readOwnPath(record, column.field)
      if (field?.type === 'IMAGE')
        return value ? '图片' : '图片示例'
      return formatValue(value, column.format)
    }
    catch {
      return field?.type === 'IMAGE' ? '图片' : fieldLabel(catalog, `${section.collectionPath}.${column.field}`) || `${rowIndex + 1}`
    }
  }))
}

function mergedPreviewCells(row, columns, catalog, context, sectionStyle) {
  let columnIndex = 0
  return row.cells.map((cell, index) => {
    const widthMm = columns.slice(columnIndex, columnIndex + cell.span).reduce((sum, column) => sum + column.widthMm, 0)
    columnIndex += cell.span
    return {
      key: index,
      text: cell.binding ? designerBindingText(cell.binding, cell.format, catalog, context) : cell.text,
      widthMm,
      style: { ...sectionStyle, ...cell.style },
    }
  })
}

export function designerTablePreview(section, catalog, context, limit = 3) {
  const defaultHeader = [{
    cells: section.columns.map(column => ({ key: column.id, text: column.title, widthMm: column.widthMm, style: { ...section.style, ...column.style, fontWeight: 700 } })),
    kind: 'header',
  }]
  const headers = section.headerRows?.length
    ? section.headerRows.map((row, index) => ({ cells: mergedPreviewCells(row, section.columns, catalog, context, section.style), kind: 'header', key: `header-${index}` }))
    : defaultHeader
  const data = designerTableRows(section, catalog, context, limit).map((row, rowIndex) => ({
    kind: 'data',
    key: `data-${rowIndex}`,
    cells: row.map((text, columnIndex) => ({ key: section.columns[columnIndex].id, text, widthMm: section.columns[columnIndex].widthMm, style: { ...section.style, ...section.columns[columnIndex].style } })),
  }))
  const footer = section.footer
    ? [{ cells: mergedPreviewCells(section.footer, section.columns, catalog, context, section.style), kind: 'footer', key: 'footer' }]
    : []
  return [...headers, ...data, ...footer]
}

export function defaultFieldFormat(field) {
  if (!['MONEY', 'NUMBER', 'DATE', 'BOOLEAN'].includes(field?.type))
    return undefined
  return field.type === 'DATE' ? { type: 'DATE', datePattern: 'YYYY-MM-DD' } : { type: field.type }
}
