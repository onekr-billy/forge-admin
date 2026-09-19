import { defineStore } from 'pinia'
import { findSurface, newPrintId, resizeElement, selectionBounds, snapResize, snapTranslation, translateElements } from '../../components/print/designer/commands'
import { cloneDocument, createHistory, recordChange, travelHistory } from '../../components/print/designer/history'
import { validateFieldCatalog } from '../../components/print/protocol/fieldCatalog'
import { createPrintDocument } from '../../components/print/protocol/types'
import { screenDeltaToMm } from '../../components/print/protocol/units'
import { assertPrintDocument } from '../../components/print/protocol/validate'

export const usePrintDesignerStore = defineStore('printDesigner', {
  state: () => ({
    document: createPrintDocument(),
    catalog: [],
    surfaceId: 'header',
    selectedIds: [],
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
      }
    },
    selectElement(id, additive = false) {
      if (additive) {
        this.selectedIds = this.selectedIds.includes(id) ? this.selectedIds.filter(value => value !== id) : [...this.selectedIds, id]
      }
      else {
        this.selectedIds = [id]
      }
    },
    selectAll() {
      this.selectedIds = this.activeSurface?.elements?.map(element => element.id) || []
    },
    reconcileSelection() {
      if (!this.activeSurface) {
        this.surfaceId = this.document.body.length ? `section:${this.document.body[0].id}` : 'header'
      }
      this.selectedIds = this.selectedIds.filter(id => this.activeSurface?.elements?.some(e => e.id === id))
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
        findSurface(document, this.surfaceId)?.elements?.filter(e => this.selectedIds.includes(e.id)).forEach(e => Object.assign(e, cloneDocument(patch)))
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
      const copies = cloneDocument(this.clipboard).map(element => ({ ...element, id: newPrintId(), locked: false }))
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
      const copies = cloneDocument(this.selectedElements).map(element => ({ ...element, id: newPrintId(), locked: false }))
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
