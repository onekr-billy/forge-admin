import { paperGeometry } from '../protocol/units'
import { findSurface, newPrintId } from './commands'
import { defaultFieldFormat } from './designerSample'
import { createStaticTable, staticTableSize } from './staticTable'

// These options describe the fixed print protocol, not configurable business enums.
export const elementCatalog = [
  { key: 'title', type: 'TEXT', preset: 'TITLE', label: '标题文本' },
  { key: 'text', type: 'TEXT', preset: 'TEXT', label: '固定文本' },
  { key: 'image', type: 'IMAGE', label: '图片' },
  { key: 'line-horizontal', type: 'LINE', preset: 'HORIZONTAL', label: '横线' },
  { key: 'line-vertical', type: 'LINE', preset: 'VERTICAL', label: '竖线' },
  { key: 'rectangle', type: 'RECTANGLE', label: '矩形' },
  { key: 'ellipse', type: 'ELLIPSE', label: '椭圆' },
  { key: 'barcode', type: 'BARCODE', label: '条形码' },
  { key: 'qrcode', type: 'QRCODE', label: '二维码' },
  { key: 'page-number', type: 'PAGE_NUMBER', label: '页码' },
  { key: 'static-table', type: 'STATIC_TABLE', label: '空白表格' },
]
export const PRINT_DRAG_TYPE = 'application/x-forge-print-item'

export function addElement(store, type, binding, position, preset) {
  if (!elementCatalog.some(item => item.type === type))
    return false
  const id = newPrintId()
  let targetId = store.surfaceId
  const ok = store.execute((doc) => {
    let surface = findSurface(doc, targetId)
    if (!surface?.elements) {
      surface = { id: newPrintId(), kind: 'FIXED', heightMm: 40, elements: [] }
      doc.body.push(surface)
      targetId = surface.id
    }
    if (surface.heightMm < (type === 'STATIC_TABLE' ? 34 : 20))
      surface.heightMm = type === 'STATIC_TABLE' ? 34 : 20
    const presetSize = preset === 'TITLE'
      ? { widthMm: 90, heightMm: 14 }
      : preset === 'VERTICAL'
        ? { widthMm: 1, heightMm: 30 }
        : type === 'QRCODE'
          ? { widthMm: 18, heightMm: 18 }
          : type === 'STATIC_TABLE'
            ? { widthMm: 75, heightMm: 27 }
            : type === 'ELLIPSE'
              ? { widthMm: 32, heightMm: 20 }
              : type === 'LINE'
                ? { widthMm: 45, heightMm: 1 }
                : type === 'TEXT' || type === 'PAGE_NUMBER'
                  ? { widthMm: 45, heightMm: 10 }
                  : { widthMm: 45, heightMm: 18 }
    const widthMm = Math.min(presetSize.widthMm, paperGeometry(doc).contentWidthMm)
    const heightMm = presetSize.heightMm
    const e = { id, type, xMm: Math.max(0, Math.min(position?.xMm ?? 3, paperGeometry(doc).contentWidthMm - widthMm)), yMm: Math.max(0, Math.min(position?.yMm ?? 3, surface.heightMm - heightMm)), widthMm, heightMm }
    if (type === 'STATIC_TABLE') {
      e.table = createStaticTable(3, 3, widthMm, heightMm / 3)
      Object.assign(e, staticTableSize(e.table))
    }
    if (['TEXT', 'IMAGE', 'BARCODE', 'QRCODE'].includes(type)) {
      e.binding = binding || { source: 'CONSTANT', value: type === 'TEXT' ? (preset === 'TITLE' ? '标题文本' : '固定文本') : type === 'IMAGE' ? '' : '123456' }
      const field = e.binding.source === 'FIELD' ? store.catalog.find(item => item.path === e.binding.path) : null
      e.format = defaultFieldFormat(field)
    }
    if (preset === 'TITLE')
      e.style = { fontSizePt: 18, fontWeight: 700, textAlign: 'center', lineHeight: 1.2 }
    if (['LINE', 'RECTANGLE', 'ELLIPSE'].includes(type))
      e.style = { borderWidthMm: 0.2, borderColor: '#000000' }
    surface.elements.push(e)
  })
  if (ok) {
    store.selectSurface(targetId)
    store.selectedIds = [id]
  }
  return ok
}

export function addSection(store, kind, field) {
  const id = newPrintId()
  const ok = store.execute((doc) => {
    const section = { id, kind, gapAfterMm: 2 }
    if (kind === 'FIXED')
      Object.assign(section, { heightMm: 40, elements: [] })
    if (kind === 'TEXT')
      section.binding = { source: 'CONSTANT', value: '流式文本，内容变长时自动换行和续页。' }
    if (kind === 'TABLE') {
      const collection = field || store.catalog.find(f => f.type === 'COLLECTION')
      const fields = store.catalog.filter(f => f.type !== 'COLLECTION' && f.path.startsWith(`${collection?.path}.`)).slice(0, 6)
      if (!collection || !fields.length)
        throw new Error('请先提供包含明细列的字段目录')
      Object.assign(section, { collectionPath: collection.path, repeatHeader: true, emptyText: '暂无明细', columns: fields.map(f => ({ id: newPrintId(), field: f.path.slice(collection.path.length + 1), title: f.label || f.path.split('.').at(-1), widthMm: Math.floor(paperGeometry(doc).contentWidthMm / fields.length * 100) / 100, format: defaultFieldFormat(f) })) })
    }
    doc.body.push(section)
  })
  if (ok)
    store.selectSurface(id)
  return ok
}

export function addField(store, path, position) {
  const field = store.catalog.find(f => f.path === path)
  if (!field)
    return false
  if (field.type === 'COLLECTION')
    return addSection(store, 'TABLE', field)
  const collection = store.catalog.filter(f => f.type === 'COLLECTION' && path.startsWith(`${f.path}.`)).sort((a, b) => b.path.length - a.path.length)[0]
  if (collection) {
    const surface = store.activeSurface
    if (surface?.kind !== 'TABLE' || surface.collectionPath !== collection.path) {
      store.error = '请先选中此明细对应的表格，再添加明细列'
      return false
    }
    return store.execute((doc) => {
      const table = findSurface(doc, store.surfaceId)
      const relativePath = path.slice(collection.path.length + 1)
      if (table.columns.some(column => column.field === relativePath))
        return
      if (table.headerRows || table.footer)
        throw new Error('含合并表头或表尾时，请先在表格属性中清除合并配置后再添加列')
      table.columns.push({ id: newPrintId(), field: relativePath, title: field.label || relativePath, widthMm: 20, format: defaultFieldFormat(field) })
      const width = Math.floor(paperGeometry(doc).contentWidthMm / table.columns.length * 100) / 100
      table.columns.forEach((column) => {
        column.widthMm = width
      })
    })
  }
  return addElement(store, field.type === 'IMAGE' ? 'IMAGE' : 'TEXT', { source: 'FIELD', path }, position, 'FIELD')
}

export function startItemDrag(event, item) {
  event.dataTransfer.setData(PRINT_DRAG_TYPE, JSON.stringify(item))
  event.dataTransfer.effectAllowed = 'copy'
}
