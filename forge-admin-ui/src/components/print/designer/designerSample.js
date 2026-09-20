import { readOwnPath, resolveCollection } from '../protocol/binding'
import { formatValue } from '../protocol/formatters'
import { isSafeFieldPath } from '../protocol/validate'
import { mergeDataTableCellStyle } from './dataTableCellStyles'

const SAMPLE_DATE = '2026-09-19 10:30:00'
/** Tiny inline placeholder so IMAGE bindings preview without a real fileId. */
export const SAMPLE_IMAGE_DATA_URL = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="96" viewBox="0 0 160 96">'
  + '<rect width="160" height="96" rx="6" fill="#e2e8f0"/>'
  + '<rect x="18" y="18" width="124" height="60" rx="4" fill="#f8fafc" stroke="#94a3b8" stroke-width="1.5"/>'
  + '<circle cx="52" cy="42" r="10" fill="#94a3b8"/>'
  + '<path d="M28 70l28-22 18 14 22-20 26 28H28z" fill="#64748b"/>'
  + '<text x="80" y="88" text-anchor="middle" fill="#475569" font-size="11" font-family="sans-serif">示例图片</text>'
  + '</svg>',
)

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
    return SAMPLE_IMAGE_DATA_URL
  if (field?.type === 'COLLECTION')
    return []
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
    if (!binding)
      return null
    if (binding.source === 'CONSTANT')
      return binding.value ?? null
    // Designer preview may show arrays/images; runtime still uses strict resolveBinding.
    return readOwnPath(context, binding.path)
  }
  catch {
    return null
  }
}

export function designerBindingText(binding, format, catalog, context) {
  const value = designerBindingValue(binding, context)
  if (Array.isArray(value)) {
    if (!value.length)
      return format?.emptyText ?? ''
    if (value.every(item => item == null || ['string', 'number', 'boolean'].includes(typeof item)))
      return value.filter(item => item != null && item !== '').map(String).join('、')
    return '（对象列表请用左侧「子表 · 明细表」）'
  }
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

/** True when a static-table cell should render as an image (explicit or IMAGE field). */
export function isDesignerImageCell(cell, catalog = []) {
  if (!cell)
    return false
  if (cell.contentType === 'IMAGE')
    return true
  if (cell.binding?.source === 'FIELD' && cell.binding.path) {
    const field = catalog.find(item => item.path === cell.binding.path)
    return field?.type === 'IMAGE'
  }
  return false
}

/** Resolve fileId / data-url / http url for a cell image binding. */
export function designerCellImageRef(cell, context) {
  if (!cell?.binding)
    return ''
  if (cell.binding.source === 'CONSTANT')
    return String(cell.binding.value || '')
  if (cell.binding.source === 'FIELD') {
    const value = designerBindingValue(cell.binding, context)
    return value == null || value === '' ? '' : String(value)
  }
  return ''
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
    const colStart = columnIndex
    const widthMm = columns.slice(columnIndex, columnIndex + cell.span).reduce((sum, column) => sum + column.widthMm, 0)
    columnIndex += cell.span
    const image = cell.contentType === 'IMAGE'
    return {
      key: index,
      colStart,
      colSpan: cell.span,
      contentType: image ? 'IMAGE' : 'TEXT',
      type: image ? 'IMAGE' : 'TEXT',
      text: image ? (cell.binding ? '图片' : (cell.text || '图片')) : (cell.binding ? designerBindingText(cell.binding, cell.format, catalog, context) : cell.text),
      widthMm,
      style: { ...sectionStyle, ...cell.style },
    }
  })
}

export function designerTablePreview(section, catalog, context, limit = 3) {
  const cellStyles = section.cellStyles || {}
  const defaultHeader = [{
    cells: section.columns.map((column, index) => ({
      key: column.id,
      colStart: index,
      colSpan: 1,
      text: column.title,
      widthMm: column.widthMm,
      style: mergeDataTableCellStyle({
        ...section.style,
        backgroundColor: '#f1f5f9',
        fontWeight: 700,
        ...section.headerStyle,
        ...column.headerStyle,
      }, cellStyles, 'header', 0, column.id),
    })),
    kind: 'header',
  }]
  const headers = section.headerRows?.length
    ? section.headerRows.map((row, index) => ({
        cells: mergedPreviewCells(row, section.columns, catalog, context, {
          ...section.style,
          backgroundColor: section.headerStyle?.backgroundColor || '#f1f5f9',
          ...section.headerStyle,
        }).map((cell, cellIndex) => {
          // Approximate column id from colStart for overrides.
          const column = section.columns[cell.colStart]
          return {
            ...cell,
            style: mergeDataTableCellStyle(cell.style, cellStyles, 'header', index, column?.id || cell.key),
          }
        }),
        kind: 'header',
        key: `header-${index}`,
      }))
    : defaultHeader
  const data = designerTableRows(section, catalog, context, limit).map((row, rowIndex) => {
    const bandStyle = rowIndex % 2 === 0 ? section.oddRowStyle : section.evenRowStyle
    return {
      kind: 'data',
      key: `data-${rowIndex}`,
      cells: row.map((text, columnIndex) => {
        const field = catalog.find(item => item.path === `${section.collectionPath}.${section.columns[columnIndex].field}`)
        const image = field?.type === 'IMAGE'
        const column = section.columns[columnIndex]
        return {
          key: column.id,
          colStart: columnIndex,
          colSpan: 1,
          contentType: image ? 'IMAGE' : 'TEXT',
          type: image ? 'IMAGE' : 'TEXT',
          text,
          widthMm: column.widthMm,
          style: mergeDataTableCellStyle(
            { ...section.style, ...bandStyle, ...column.style },
            cellStyles,
            'data',
            rowIndex,
            column.id,
          ),
        }
      }),
    }
  })
  const footer = section.footer
    ? [{
        cells: mergedPreviewCells(section.footer, section.columns, catalog, context, section.style).map((cell) => {
          const column = section.columns[cell.colStart]
          return {
            ...cell,
            style: mergeDataTableCellStyle(cell.style, cellStyles, 'footer', 0, column?.id || cell.key),
          }
        }),
        kind: 'footer',
        key: 'footer',
      }]
    : []
  return [...headers, ...data, ...footer]
}

export function defaultFieldFormat(field) {
  if (!['MONEY', 'NUMBER', 'DATE', 'BOOLEAN'].includes(field?.type))
    return undefined
  return field.type === 'DATE' ? { type: 'DATE', datePattern: 'YYYY-MM-DD' } : { type: field.type }
}
