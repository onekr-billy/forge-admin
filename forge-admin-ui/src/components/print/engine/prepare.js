import { readOwnPath, resolveBinding, resolveCollection } from '../protocol/binding'
import { formatValue } from '../protocol/formatters'
import { PrintError } from '../protocol/types'
import { mergeDataTableCellStyle } from '../designer/dataTableCellStyles'
import { tableImageResourceKey } from './resources'

export function prepareElements(elements, context, measure, resources) {
  return elements.map((element) => {
    const node = { ...element }
    if (element.type === 'TEXT') {
      node.text = formatValue(resolveBinding(element.binding, context), element.format)
      if (element.binding.source === 'SYSTEM' && ['system.pageNumber', 'system.totalPages'].includes(element.binding.path)) {
        return node
      }
      Object.assign(node, measure.text(node.text, node.widthMm, node.style))
      if (node.heightMm > element.heightMm + 0.0001) {
        throw new PrintError('TEXT_OVERFLOW', '固定文本超过元素高度，请加高或使用流式文本区块', element.id)
      }
      node.heightMm = element.heightMm
    }
    if (element.type === 'HTML') {
      node.html = String(resolveBinding(element.binding, context) ?? '')
      node.text = node.html
      node.heightMm = element.heightMm
    }
    if (['IMAGE', 'BARCODE', 'QRCODE'].includes(element.type)) {
      node.src = resources?.images.get(element.id) || ''
      const value = resolveBinding(element.binding, context)
      if (!node.src && value !== null && value !== '') {
        throw new PrintError('RESOURCE_NOT_READY', '图片或编码尚未完成准备', element.id)
      }
      if (['BARCODE', 'QRCODE'].includes(element.type)) {
        node.text = formatValue(resolveBinding(element.binding, context), element.format)
      }
    }
    if (element.type === 'STATIC_TABLE') {
      node.table = {
        ...element.table,
        cells: element.table.cells.map((cell) => {
          const image = cell.contentType === 'IMAGE'
          const value = resolveBinding(cell.binding, context)
          if (image) {
            const src = resources?.images.get(`static-cell:${element.id}:${cell.id}`) || ''
            if (value !== null && value !== '' && !src)
              throw new PrintError('RESOURCE_NOT_READY', '表格图片尚未完成准备', cell.id)
            return {
              ...cell,
              type: 'IMAGE',
              text: '',
              src,
              imageWidthMm: cell.imageWidthMm || Number(Math.max(3, (
                element.table.columns.slice(cell.column, cell.column + cell.colSpan)
                  .reduce((sum, col) => sum + (col.widthMm || 0), 0) - 2
              )).toFixed(2)),
              imageHeightMm: cell.imageHeightMm || Number(Math.max(3, (
                element.table.rows.slice(cell.row, cell.row + cell.rowSpan)
                  .reduce((sum, row) => sum + (row.heightMm || 0), 0) - 2
              )).toFixed(2)),
            }
          }
          return {
            ...cell,
            type: 'TEXT',
            text: formatValue(value, cell.format),
          }
        }),
      }
    }
    return node
  })
}

function preparedRow(cells, kind, key, measure) {
  return { cells, kind, key, heightMm: measure.row(cells) }
}

function mergedCells(row, columns, context, sectionStyle, resources, sectionId, bandKey) {
  let column = 0
  return row.cells.map((cell, index) => {
    const widthMm = columns.slice(column, column + cell.span).reduce((sum, item) => sum + item.widthMm, 0)
    column += cell.span
    if (cell.contentType === 'IMAGE') {
      const value = cell.binding ? resolveBinding(cell.binding, context) : null
      const src = resources?.images.get(`band:${sectionId}:${bandKey}:${index}`) || ''
      if (value !== null && value !== '' && !src)
        throw new PrintError('RESOURCE_NOT_READY', '表格图片尚未完成准备', `${sectionId}:${bandKey}:${index}`)
      return {
        text: '',
        type: 'IMAGE',
        src,
        imageHeightMm: src ? Math.min(18, Math.max(8, widthMm * 0.4)) : 0,
        widthMm,
        style: { ...sectionStyle, ...cell.style },
      }
    }
    return { text: cell.binding ? formatValue(resolveBinding(cell.binding, context), cell.format) : cell.text, widthMm, style: { ...sectionStyle, ...cell.style } }
  })
}

export function prepareSection(section, context, measure, geometry, resources, catalog = []) {
  if (section.kind === 'PAGE_BREAK') {
    return { ...section, heightMm: 0 }
  }
  if (section.kind === 'FIXED') {
    return { ...section, elements: prepareElements(section.elements, context, measure, resources), widthMm: geometry.contentWidthMm }
  }
  if (section.kind === 'TEXT') {
    const text = formatValue(resolveBinding(section.binding, context), section.format)
    return { ...section, type: 'TEXT', text, widthMm: geometry.contentWidthMm, ...measure.text(text, geometry.contentWidthMm, section.style) }
  }
  const source = resolveCollection(section.collectionPath, context)
  const fieldTypes = new Map(catalog.map(field => [field.path, field.type]))
  const widthMm = section.columns.reduce((sum, column) => sum + column.widthMm, 0)
  const cellStyles = section.cellStyles || {}
  const defaultHeader = { cells: section.columns.map(column => ({
    text: column.title,
    span: 1,
    style: mergeDataTableCellStyle({
      backgroundColor: '#f1f5f9',
      fontWeight: 700,
      ...section.headerStyle,
      ...column.headerStyle,
    }, cellStyles, 'header', 0, column.id),
  })) }
  const headers = (section.headerRows?.length ? section.headerRows : [defaultHeader]).map((row, i) => {
    let colCursor = 0
    const cells = mergedCells(row, section.columns, context, {
      ...section.style,
      backgroundColor: section.headerStyle?.backgroundColor || '#f1f5f9',
      ...section.headerStyle,
    }, resources, section.id, `header-${i}`).map((cell) => {
      const column = section.columns[colCursor]
      const start = colCursor
      colCursor += cell.span || 1
      return {
        ...cell,
        style: mergeDataTableCellStyle(cell.style, cellStyles, 'header', i, column?.id || `c${start}`),
      }
    })
    return preparedRow(cells, 'header', `header-${i}`, measure)
  })
  const rows = source.map((record, i) => preparedRow(section.columns.map((column) => {
    const value = readOwnPath(record, column.field)
    const image = fieldTypes.get(`${section.collectionPath}.${column.field}`) === 'IMAGE'
    const src = image ? resources?.images.get(tableImageResourceKey(section.id, i, column.id)) || '' : ''
    if (image && value !== null && value !== '' && !src) {
      throw new PrintError('RESOURCE_NOT_READY', '明细图片或签名尚未完成准备', `${section.collectionPath}[${i}].${column.field}`)
    }
    const bandStyle = i % 2 === 0 ? section.oddRowStyle : section.evenRowStyle
    return {
      text: image ? '' : formatValue(value, column.format),
      type: image ? 'IMAGE' : 'TEXT',
      src,
      imageHeightMm: src ? Math.min(18, Math.max(8, column.widthMm * 0.4)) : 0,
      widthMm: column.widthMm,
      style: mergeDataTableCellStyle(
        { ...section.style, ...bandStyle, ...column.style },
        cellStyles,
        'data',
        i,
        column.id,
      ),
    }
  }), 'data', `${section.id}-${i}`, measure))
  if (!rows.length) {
    rows.push(preparedRow([{ text: section.emptyText ?? '暂无明细', widthMm, style: section.style }], 'empty', 'empty', measure))
  }
  const footer = section.footer
    ? (() => {
        let colCursor = 0
        const cells = mergedCells(section.footer, section.columns, context, section.style, resources, section.id, 'footer').map((cell) => {
          const column = section.columns[colCursor]
          const start = colCursor
          colCursor += cell.span || 1
          return {
            ...cell,
            style: mergeDataTableCellStyle(cell.style, cellStyles, 'footer', 0, column?.id || `c${start}`),
          }
        })
        return preparedRow(cells, 'footer', 'footer', measure)
      })()
    : null
  return { ...section, widthMm, headers, rows, footer, sourceRowCount: source.length, heightMm: [...headers, ...rows, ...(footer ? [footer] : [])].reduce((sum, row) => sum + row.heightMm, 0) }
}
