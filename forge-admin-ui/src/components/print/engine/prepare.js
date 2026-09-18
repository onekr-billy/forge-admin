import { readOwnPath, resolveBinding, resolveCollection } from '../protocol/binding'
import { formatValue } from '../protocol/formatters'
import { PrintError } from '../protocol/types'

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
    return node
  })
}

function preparedRow(cells, kind, key, measure) {
  return { cells, kind, key, heightMm: measure.row(cells) }
}

function mergedCells(row, columns, context, sectionStyle) {
  let column = 0
  return row.cells.map((cell) => {
    const widthMm = columns.slice(column, column + cell.span).reduce((sum, item) => sum + item.widthMm, 0)
    column += cell.span
    return { text: cell.binding ? formatValue(resolveBinding(cell.binding, context), cell.format) : cell.text, widthMm, style: { ...sectionStyle, ...cell.style } }
  })
}

export function prepareSection(section, context, measure, geometry, resources) {
  if (section.kind === 'FIXED') {
    return { ...section, elements: prepareElements(section.elements, context, measure, resources), widthMm: geometry.contentWidthMm }
  }
  if (section.kind === 'TEXT') {
    const text = formatValue(resolveBinding(section.binding, context), section.format)
    return { ...section, type: 'TEXT', text, widthMm: geometry.contentWidthMm, ...measure.text(text, geometry.contentWidthMm, section.style) }
  }
  const source = resolveCollection(section.collectionPath, context)
  const widthMm = section.columns.reduce((sum, column) => sum + column.widthMm, 0)
  const defaultHeader = { cells: section.columns.map(column => ({ text: column.title, span: 1, style: { ...column.style, fontWeight: 700 } })) }
  const headers = (section.headerRows?.length ? section.headerRows : [defaultHeader]).map((row, i) => preparedRow(mergedCells(row, section.columns, context, section.style), 'header', `header-${i}`, measure))
  const rows = source.map((record, i) => preparedRow(section.columns.map(column => ({
    text: formatValue(readOwnPath(record, column.field), column.format),
    widthMm: column.widthMm,
    style: { ...section.style, ...column.style },
  })), 'data', `${section.id}-${i}`, measure))
  if (!rows.length) {
    rows.push(preparedRow([{ text: section.emptyText ?? '暂无明细', widthMm, style: section.style }], 'empty', 'empty', measure))
  }
  const footer = section.footer ? preparedRow(mergedCells(section.footer, section.columns, context, section.style), 'footer', 'footer', measure) : null
  return { ...section, widthMm, headers, rows, footer, sourceRowCount: source.length, heightMm: [...headers, ...rows, ...(footer ? [footer] : [])].reduce((sum, row) => sum + row.heightMm, 0) }
}
