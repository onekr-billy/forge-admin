import { paperGeometry } from '../protocol/units'
import { findSurface, newPrintId } from './commands'

// These options describe the fixed print protocol, not configurable business enums.
export const elementCatalog = [
  { type: 'TEXT', label: '文本' },
  { type: 'IMAGE', label: '图片' },
  { type: 'LINE', label: '线条' },
  { type: 'RECTANGLE', label: '矩形' },
  { type: 'BARCODE', label: '条形码' },
  { type: 'QRCODE', label: '二维码' },
  { type: 'PAGE_NUMBER', label: '页码' },
]
export const PRINT_DRAG_TYPE = 'application/x-forge-print-item'

export function addElement(store, type, binding, position) {
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
    if (surface.heightMm < 20)
      surface.heightMm = 20
    const widthMm = Math.min(type === 'QRCODE' ? 18 : 45, paperGeometry(doc).contentWidthMm)
    const heightMm = type === 'LINE' ? 1 : type === 'TEXT' || type === 'PAGE_NUMBER' ? 10 : 18
    const e = { id, type, xMm: Math.max(0, Math.min(position?.xMm ?? 3, paperGeometry(doc).contentWidthMm - widthMm)), yMm: Math.max(0, Math.min(position?.yMm ?? 3, surface.heightMm - heightMm)), widthMm, heightMm }
    if (['TEXT', 'IMAGE', 'BARCODE', 'QRCODE'].includes(type)) {
      e.binding = binding || { source: 'CONSTANT', value: type === 'TEXT' ? '文本' : type === 'IMAGE' ? '' : '123456' }
    }
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
      Object.assign(section, { collectionPath: collection.path, repeatHeader: true, emptyText: '暂无明细', columns: fields.map(f => ({ id: newPrintId(), field: f.path.slice(collection.path.length + 1), title: f.label || f.path.split('.').at(-1), widthMm: Math.floor(paperGeometry(doc).contentWidthMm / fields.length * 100) / 100 })) })
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
      table.columns.push({ id: newPrintId(), field: relativePath, title: field.label || relativePath, widthMm: 20 })
      const width = Math.floor(paperGeometry(doc).contentWidthMm / table.columns.length * 100) / 100
      table.columns.forEach((column) => {
        column.widthMm = width
      })
    })
  }
  return addElement(store, field.type === 'IMAGE' ? 'IMAGE' : 'TEXT', { source: 'FIELD', path }, position)
}

export function startItemDrag(event, item) {
  event.dataTransfer.setData(PRINT_DRAG_TYPE, JSON.stringify(item))
  event.dataTransfer.effectAllowed = 'copy'
}
