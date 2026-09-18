import { defineStore } from 'pinia'
import { findSurface, newPrintId, resizeElement, translateElements } from '../../components/print/designer/commands'
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
    history: createHistory(),
    saved: '',
    gesture: null,
    clipboard: [],
    error: '',
    notice: '',
    saving: false,
    generation: 0,
    previewOpen: false,
  }),
  getters: {
    activeSurface: state => findSurface(state.document, state.surfaceId),
    selectedElements() {
      return this.activeSurface?.elements?.filter(e => this.selectedIds.includes(e.id)) || []
    },
    activeElement() {
      return this.selectedElements.length === 1 ? this.selectedElements[0] : null
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
      return this.execute(document => translateElements(document, this.surfaceId, this.selectedIds, dx, dy))
    },
    beginGesture() {
      this.cancelGesture()
      this.gesture = { before: cloneDocument(this.document), surfaceId: this.surfaceId, ids: [...this.selectedIds], zoom: this.zoom }
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
        resizeElement(candidate, surfaceId, ids[0], x, y)
      }
      else {
        translateElements(candidate, surfaceId, ids, x, y)
      }
      this.document = candidate
    },
    endGesture() {
      if (this.gesture) {
        recordChange(this.history, this.gesture.before, this.document)
        this.gesture = null
      }
    },
    cancelGesture() {
      if (this.gesture) {
        this.document = this.gesture.before
        this.gesture = null
      }
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
      const copies = cloneDocument(this.clipboard).map(element => ({ ...element, id: newPrintId() }))
      const ok = this.execute((document) => {
        findSurface(document, this.surfaceId).elements.push(...copies)
        translateElements(document, this.surfaceId, copies.map(e => e.id), 3, 3)
      })
      if (ok) {
        this.selectedIds = copies.map(e => e.id)
      }
      return ok
    },
    removeSelection() {
      return this.execute((document) => {
        const surface = findSurface(document, this.surfaceId)
        if (surface?.elements) {
          surface.elements = surface.elements.filter(e => !this.selectedIds.includes(e.id))
        }
      })
    },
    alignSelection(axis) {
      if (this.selectedElements.length < 2) {
        return
      }
      const key = axis === 'left' ? 'xMm' : 'yMm'
      const value = Math.min(...this.selectedElements.map(e => e[key]))
      this.patchSelected({ [key]: value })
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
