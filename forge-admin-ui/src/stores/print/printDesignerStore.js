import { defineStore } from 'pinia'
import { findSurface, newPrintId, resizeElement, selectionBounds, snapResize, snapTranslation, translateElements } from '../../components/print/designer/commands'
import { cloneDocument, createHistory, recordChange, travelHistory } from '../../components/print/designer/history'
import { appendStaticTableColumn, appendStaticTableRow, deleteStaticTableColumn, deleteStaticTableRow, mergeStaticTableCells, renewStaticTableIds, splitStaticTableCell, staticTableSize } from '../../components/print/designer/staticTable'
import { validateFieldCatalog } from '../../components/print/protocol/fieldCatalog'
import { createPrintDocument } from '../../components/print/protocol/types'
import { paperGeometry, screenDeltaToMm } from '../../components/print/protocol/units'
import { assertPrintDocument } from '../../components/print/protocol/validate'

export const usePrintDesignerStore = defineStore('printDesigner', {
  state: () => ({
    document: createPrintDocument(),
    catalog: [],
    surfaceId: 'header',
    selectedIds: [],
    tableCellIds: [],
    zoom: 0.8,
    showGrid: true,
    history: createHistory(),
    saved: '',
    gesture: null,
    clipboard: [],
    error: '',
    notice: '',
    saving: false,
    generation: 0,
    previewOpen: false,
    leftPanelOpen: true,
    rightPanelOpen: true,
    alignmentGuides: { x: [], y: [], position: null },
  }),
  getters: {
    activeSurface: state => findSurface(state.document, state.surfaceId),
    selectedElements() {
      return this.activeSurface?.elements?.filter(e => this.selectedIds.includes(e.id)) || []
    },
    activeElement() {
      return this.selectedElements.length === 1 ? this.selectedElements[0] : null
    },
    selectedTableCells() {
      return this.activeElement?.type === 'STATIC_TABLE' ? this.activeElement.table.cells.filter(cell => this.tableCellIds.includes(cell.id)) : []
    },
    activeTableCell() {
      return this.selectedTableCells.length === 1 ? this.selectedTableCells[0] : null
    },
    hasLockedSelection() {
      return this.selectedElements.some(element => element.locked)
    },
    dirty: state => JSON.stringify(state.document) !== state.saved,
    canUndo: state => state.history.past.length > 0,
    canRedo: state => state.history.future.length > 0,
    fieldIssues: state => validateFieldCatalog(state.document, state.catalog),
  },
  actions: {
    load(document, catalog = this.catalog) {
      assertPrintDocument(document)
      this.document = cloneDocument(document)
      this.catalog = cloneDocument(catalog)
      this.saved = this.serialize()
      this.history = createHistory()
      this.selectedIds = []
      this.tableCellIds = []
      this.surfaceId = document.body.length ? `section:${document.body[0].id}` : 'header'
      this.gesture = null
      this.clipboard = []
      this.error = ''
      this.notice = ''
      this.saving = false
      this.previewOpen = false
      this.alignmentGuides = { x: [], y: [], position: null }
      this.generation++
    },
    serialize() {
      return JSON.stringify(this.document)
    },
    selectSurface(id) {
      if (id !== 'header' && id !== 'footer' && !id.startsWith('section:')) {
        id = `section:${id}`
      }
      if (this.surfaceId !== id) {
        this.cancelGesture()
        this.surfaceId = id
        this.selectedIds = []
        this.tableCellIds = []
      }
    },
    selectElement(id, additive = false) {
      const sameSingle = this.selectedIds.length === 1 && this.selectedIds[0] === id
      if (additive) {
        this.selectedIds = this.selectedIds.includes(id) ? this.selectedIds.filter(value => value !== id) : [...this.selectedIds, id]
      }
      else {
        this.selectedIds = [id]
      }
      if (additive || !sameSingle)
        this.tableCellIds = []
    },
    selectTableCell(id, additive = false) {
      if (this.activeElement?.type !== 'STATIC_TABLE' || !this.activeElement.table.cells.some(cell => cell.id === id))
        return
      this.tableCellIds = additive
        ? (this.tableCellIds.includes(id) ? this.tableCellIds.filter(value => value !== id) : [...this.tableCellIds, id])
        : [id]
    },
    selectAll() {
      this.selectedIds = this.activeSurface?.elements?.map(element => element.id) || []
    },
    reconcileSelection() {
      if (!this.activeSurface) {
        this.surfaceId = this.document.body.length ? `section:${this.document.body[0].id}` : 'header'
      }
      this.selectedIds = this.selectedIds.filter(id => this.activeSurface?.elements?.some(e => e.id === id))
      if (this.activeElement?.type !== 'STATIC_TABLE')
        this.tableCellIds = []
      else this.tableCellIds = this.tableCellIds.filter(id => this.activeElement.table.cells.some(cell => cell.id === id))
    },
    execute(change) {
      this.cancelGesture()
      const candidate = cloneDocument(this.document)
      try {
        change(candidate)
        assertPrintDocument(candidate)
        recordChange(this.history, this.document, candidate)
        this.document = candidate
        this.reconcileSelection()
        this.error = ''
        this.notice = ''
        return true
      }
      catch (error) {
        this.error = `${error.message}${error.path ? `（${error.path}）` : ''}`
        return false
      }
    },
    patchSelected(patch) {
      return this.execute((document) => {
        findSurface(document, this.surfaceId)?.elements?.filter(e => this.selectedIds.includes(e.id)).forEach((e) => {
          const next = cloneDocument(patch)
          if (e.type === 'STATIC_TABLE' && (next.widthMm !== undefined || next.heightMm !== undefined)) {
            resizeElement(document, this.surfaceId, e.id, (next.widthMm ?? e.widthMm) - e.widthMm, (next.heightMm ?? e.heightMm) - e.heightMm)
            delete next.widthMm
            delete next.heightMm
          }
          Object.assign(e, next)
        })
      })
    },
    patchSelectedTableCells(patch) {
      if (!this.tableCellIds.length || this.activeElement?.locked)
        return false
      const elementId = this.activeElement.id
      return this.execute((document) => {
        const element = findSurface(document, this.surfaceId).elements.find(item => item.id === elementId)
        element.table.cells.filter(cell => this.tableCellIds.includes(cell.id)).forEach(cell => Object.assign(cell, cloneDocument(patch)))
      })
    },
    patchSelectedTableCellStyle(patch) {
      if (!this.tableCellIds.length || this.activeElement?.locked)
        return false
      const elementId = this.activeElement.id
      return this.execute((document) => {
        const element = findSurface(document, this.surfaceId).elements.find(item => item.id === elementId)
        element.table.cells.filter(cell => this.tableCellIds.includes(cell.id)).forEach(cell => cell.style = { ...cell.style, ...cloneDocument(patch) })
      })
    },
    patchStaticTableTrack(axis, index, value) {
      if (this.activeElement?.type !== 'STATIC_TABLE' || this.activeElement.locked || !['row', 'column'].includes(axis) || !Number.isFinite(value))
        return false
      const elementId = this.activeElement.id
      return this.execute((document) => {
        const surface = findSurface(document, this.surfaceId)
        const element = surface.elements.find(item => item.id === elementId)
        const tracks = axis === 'row' ? element.table.rows : element.table.columns
        const key = axis === 'row' ? 'heightMm' : 'widthMm'
        tracks[index][key] = value
        Object.assign(element, staticTableSize(element.table))
        surface.heightMm = Math.max(surface.heightMm, Number((element.yMm + element.heightMm).toFixed(3)))
      })
    },
    patchSurface(patch) {
      return this.execute(document => Object.assign(findSurface(document, this.surfaceId), cloneDocument(patch)))
    },
    moveSelection(dx, dy) {
      if (this.hasLockedSelection)
        return false
      return this.execute(document => translateElements(document, this.surfaceId, this.selectedIds, dx, dy))
    },
    beginGesture() {
      if (this.hasLockedSelection)
        return false
      this.cancelGesture()
      this.gesture = { before: cloneDocument(this.document), surfaceId: this.surfaceId, ids: [...this.selectedIds], zoom: this.zoom }
      this.alignmentGuides = { x: [], y: [], position: null }
      return true
    },
    moveGesture(dx, dy, resize = false) {
      if (!this.gesture) {
        return
      }
      const { before, surfaceId, ids, zoom } = this.gesture
      const candidate = cloneDocument(before)
      const x = screenDeltaToMm(dx, zoom)
      const y = screenDeltaToMm(dy, zoom)
      if (resize) {
        const snapped = snapResize(before, surfaceId, ids[0], x, y)
        resizeElement(candidate, surfaceId, ids[0], snapped.dx, snapped.dy)
        this.alignmentGuides = snapped.guides
      }
      else {
        const snapped = snapTranslation(before, surfaceId, ids, x, y)
        translateElements(candidate, surfaceId, ids, snapped.dx, snapped.dy)
        this.alignmentGuides = snapped.guides
      }
      this.document = candidate
    },
    endGesture() {
      if (this.gesture) {
        recordChange(this.history, this.gesture.before, this.document)
        this.gesture = null
        this.alignmentGuides = { x: [], y: [], position: null }
      }
    },
    cancelGesture() {
      if (this.gesture) {
        this.document = this.gesture.before
        this.gesture = null
      }
      this.alignmentGuides = { x: [], y: [], position: null }
    },
    undo() {
      this.cancelGesture()
      this.document = travelHistory(this.history, this.document, 'undo')
      this.reconcileSelection()
      this.error = ''
      this.notice = ''
    },
    redo() {
      this.cancelGesture()
      this.document = travelHistory(this.history, this.document, 'redo')
      this.reconcileSelection()
      this.error = ''
      this.notice = ''
    },
    copySelection() {
      this.clipboard = cloneDocument(this.selectedElements)
    },
    pasteSelection() {
      if (!this.activeSurface?.elements || !this.clipboard.length) {
        return false
      }
      const copies = cloneDocument(this.clipboard).map(element => renewStaticTableIds({ ...element, id: newPrintId(), locked: false }))
      const ok = this.execute((document) => {
        findSurface(document, this.surfaceId).elements.push(...copies)
        translateElements(document, this.surfaceId, copies.map(e => e.id), 3, 3)
      })
      if (ok) {
        this.selectedIds = copies.map(e => e.id)
      }
      return ok
    },
    duplicateSelection() {
      if (!this.selectedElements.length)
        return false
      const copies = cloneDocument(this.selectedElements).map(element => renewStaticTableIds({ ...element, id: newPrintId(), locked: false }))
      const ok = this.execute((document) => {
        findSurface(document, this.surfaceId).elements.push(...copies)
        translateElements(document, this.surfaceId, copies.map(element => element.id), 3, 3)
      })
      if (ok)
        this.selectedIds = copies.map(element => element.id)
      return ok
    },
    moveSelectionLayer(position) {
      if (!this.selectedElements.length || this.hasLockedSelection || !['front', 'back'].includes(position))
        return false
      return this.execute((document) => {
        const surface = findSurface(document, this.surfaceId)
        const selected = surface.elements.filter(element => this.selectedIds.includes(element.id))
        const rest = surface.elements.filter(element => !this.selectedIds.includes(element.id))
        surface.elements = position === 'front' ? [...rest, ...selected] : [...selected, ...rest]
      })
    },
    removeSelection() {
      if (this.hasLockedSelection)
        return false
      return this.execute((document) => {
        const surface = findSurface(document, this.surfaceId)
        if (surface?.elements) {
          surface.elements = surface.elements.filter(e => !this.selectedIds.includes(e.id))
        }
      })
    },
    alignSelection(alignment) {
      if (this.selectedElements.length < 2 || this.hasLockedSelection) {
        return false
      }
      return this.execute((document) => {
        const elements = findSurface(document, this.surfaceId).elements.filter(element => this.selectedIds.includes(element.id))
        const bounds = selectionBounds(elements)
        for (const element of elements) {
          if (alignment === 'left')
            element.xMm = bounds.xMm
          else if (alignment === 'center')
            element.xMm = Number((bounds.xMm + (bounds.widthMm - element.widthMm) / 2).toFixed(3))
          else if (alignment === 'right')
            element.xMm = Number((bounds.xMm + bounds.widthMm - element.widthMm).toFixed(3))
          else if (alignment === 'top')
            element.yMm = bounds.yMm
          else if (alignment === 'middle')
            element.yMm = Number((bounds.yMm + (bounds.heightMm - element.heightMm) / 2).toFixed(3))
          else if (alignment === 'bottom')
            element.yMm = Number((bounds.yMm + bounds.heightMm - element.heightMm).toFixed(3))
        }
      })
    },
    distributeSelection(axis) {
      if (this.selectedElements.length < 3 || this.hasLockedSelection) {
        return false
      }
      return this.execute((document) => {
        const horizontal = axis === 'horizontal'
        const positionKey = horizontal ? 'xMm' : 'yMm'
        const sizeKey = horizontal ? 'widthMm' : 'heightMm'
        const elements = findSurface(document, this.surfaceId).elements.filter(element => this.selectedIds.includes(element.id)).sort((a, b) => a[positionKey] - b[positionKey])
        const first = elements[0][positionKey]
        const lastEdge = elements.at(-1)[positionKey] + elements.at(-1)[sizeKey]
        const occupied = elements.reduce((total, element) => total + element[sizeKey], 0)
        const gap = (lastEdge - first - occupied) / (elements.length - 1)
        let cursor = first
        for (const element of elements) {
          element[positionKey] = Number(cursor.toFixed(3))
          cursor += element[sizeKey] + gap
        }
      })
    },
    rotateSelection(delta) {
      if (!this.selectedElements.length || this.hasLockedSelection || !Number.isFinite(delta))
        return false
      return this.execute((document) => {
        findSurface(document, this.surfaceId).elements.filter(element => this.selectedIds.includes(element.id)).forEach((element) => {
          const value = (element.rotationDeg || 0) + delta
          element.rotationDeg = ((value + 180) % 360 + 360) % 360 - 180
        })
      })
    },
    flipSelection(axis) {
      if (!this.selectedElements.length || this.hasLockedSelection || !['x', 'y'].includes(axis))
        return false
      return this.execute((document) => {
        const key = axis === 'x' ? 'flipX' : 'flipY'
        findSurface(document, this.surfaceId).elements.filter(element => this.selectedIds.includes(element.id)).forEach((element) => {
          element[key] = !element[key]
        })
      })
    },
    toggleSelectionLock(value) {
      if (!this.selectedElements.length)
        return false
      return this.execute((document) => {
        findSurface(document, this.surfaceId).elements.filter(element => this.selectedIds.includes(element.id)).forEach((element) => {
          element.locked = value ?? !element.locked
        })
      })
    },
    addStaticTableRow() {
      if (this.activeElement?.type !== 'STATIC_TABLE' || this.activeElement.locked)
        return false
      const id = this.activeElement.id
      let created = []
      const ok = this.execute((document) => {
        const surface = findSurface(document, this.surfaceId)
        const element = surface.elements.find(item => item.id === id)
        created = appendStaticTableRow(element.table)
        Object.assign(element, staticTableSize(element.table))
        surface.heightMm = Math.max(surface.heightMm, Number((element.yMm + element.heightMm).toFixed(3)))
      })
      if (ok)
        this.tableCellIds = created
      return ok
    },
    addStaticTableColumn() {
      if (this.activeElement?.type !== 'STATIC_TABLE' || this.activeElement.locked)
        return false
      const id = this.activeElement.id
      let created = []
      const ok = this.execute((document) => {
        const element = findSurface(document, this.surfaceId).elements.find(item => item.id === id)
        created = appendStaticTableColumn(element.table)
        Object.assign(element, staticTableSize(element.table))
        const available = paperGeometry(document).contentWidthMm - element.xMm
        if (element.widthMm > available) {
          const ratio = available / element.widthMm
          element.table.columns.forEach(column => column.widthMm = Number((column.widthMm * ratio).toFixed(3)))
          Object.assign(element, staticTableSize(element.table))
          element.table.columns.at(-1).widthMm = Number((element.table.columns.at(-1).widthMm + available - element.widthMm).toFixed(3))
          element.widthMm = available
        }
      })
      if (ok)
        this.tableCellIds = created
      return ok
    },
    deleteStaticTableRow() {
      if (!this.activeTableCell || this.activeElement.locked)
        return false
      const elementId = this.activeElement.id
      const row = this.activeTableCell.row
      const ok = this.execute((document) => {
        const element = findSurface(document, this.surfaceId).elements.find(item => item.id === elementId)
        if (!deleteStaticTableRow(element.table, row))
          throw new Error('空白表格至少保留一行')
        Object.assign(element, staticTableSize(element.table))
      })
      if (ok)
        this.tableCellIds = []
      return ok
    },
    deleteStaticTableColumn() {
      if (!this.activeTableCell || this.activeElement.locked)
        return false
      const elementId = this.activeElement.id
      const column = this.activeTableCell.column
      const ok = this.execute((document) => {
        const element = findSurface(document, this.surfaceId).elements.find(item => item.id === elementId)
        if (!deleteStaticTableColumn(element.table, column))
          throw new Error('空白表格至少保留一列')
        Object.assign(element, staticTableSize(element.table))
      })
      if (ok)
        this.tableCellIds = []
      return ok
    },
    mergeStaticTableSelection() {
      if (this.selectedTableCells.length < 2 || this.activeElement.locked)
        return false
      const elementId = this.activeElement.id
      let mergedId = null
      const ok = this.execute((document) => {
        const element = findSurface(document, this.surfaceId).elements.find(item => item.id === elementId)
        mergedId = mergeStaticTableCells(element.table, this.tableCellIds)
        if (!mergedId)
          throw new Error('请选择连续的矩形单元格区域')
      })
      if (ok)
        this.tableCellIds = [mergedId]
      return ok
    },
    splitStaticTableSelection() {
      if (!this.activeTableCell || this.activeElement.locked)
        return false
      const elementId = this.activeElement.id
      const cellId = this.activeTableCell.id
      let created = []
      const ok = this.execute((document) => {
        const element = findSurface(document, this.surfaceId).elements.find(item => item.id === elementId)
        created = splitStaticTableCell(element.table, cellId)
        if (!created.length)
          throw new Error('当前单元格没有合并')
      })
      if (ok)
        this.tableCellIds = created
      return ok
    },
    async save(writer) {
      if (this.saving || this.gesture) {
        return false
      }
      const generation = this.generation
      const serialized = this.serialize()
      this.saving = true
      try {
        await writer(JSON.parse(serialized))
        if (this.generation === generation) {
          this.saved = serialized
          this.error = ''
          this.notice = this.dirty ? '已保存此版本，仍有新的修改未保存' : '草稿已保存'
        }
        return true
      }
      catch (error) {
        if (this.generation === generation) {
          this.error = `保存失败：${error.message}`
        }
        return false
      }
      finally {
        if (this.generation === generation) {
          this.saving = false
        }
      }
    },
  },
})
