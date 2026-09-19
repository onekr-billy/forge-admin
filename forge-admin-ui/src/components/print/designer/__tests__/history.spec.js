import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { usePrintDesignerStore } from '../../../../stores/print/printDesignerStore'
import { createPrintDocument } from '../../protocol/types'
import { mmToPx } from '../../protocol/units'

function fixture() {
  const doc = createPrintDocument()
  doc.body.push({ id: 'section', kind: 'FIXED', heightMm: 60, elements: [
    { id: 'a', type: 'TEXT', xMm: 10, yMm: 10, widthMm: 30, heightMm: 10, binding: { source: 'CONSTANT', value: '文字' } },
    { id: 'b', type: 'TEXT', xMm: 50, yMm: 20, widthMm: 30, heightMm: 10, binding: { source: 'CONSTANT', value: '第二项' } },
  ] })
  return doc
}

describe('print designer commands and history', () => {
  let store
  beforeEach(() => {
    setActivePinia(createPinia())
    store = usePrintDesignerStore()
    store.load(fixture())
    store.selectSurface('section')
    store.selectElement('a')
  })

  it('records an entire zoomed drag as one undoable operation', () => {
    store.zoom = 0.5
    store.beginGesture()
    store.moveGesture(mmToPx(2) * 0.5, 0)
    store.moveGesture(mmToPx(9) * 0.5, mmToPx(4) * 0.5)
    store.endGesture()
    expect(store.selectedElements[0].xMm).toBeCloseTo(19)
    expect(store.history.past).toHaveLength(1)
    store.undo()
    expect(store.selectedElements[0].xMm).toBe(10)
    expect(store.dirty).toBe(false)
    store.redo()
    expect(store.selectedElements[0].yMm).toBeCloseTo(14)
  })

  it('snaps movement to element edges and clears dynamic guides after the gesture', () => {
    store.beginGesture()
    store.moveGesture(mmToPx(9.4) * store.zoom, 0)
    expect(store.selectedElements[0].xMm).toBeCloseTo(20)
    expect(store.alignmentGuides.x).toEqual([50])
    expect(store.alignmentGuides.position).toMatchObject({ xMm: 20, yMm: 10 })
    store.endGesture()
    expect(store.alignmentGuides).toEqual({ x: [], y: [], position: null })
  })

  it('does not show an alignment line outside the snap threshold and clears on cancel', () => {
    store.beginGesture()
    store.moveGesture(mmToPx(8.5) * store.zoom, mmToPx(2.5) * store.zoom)
    expect(store.alignmentGuides.x).toEqual([])
    expect(store.alignmentGuides.y).toEqual([])
    store.cancelGesture()
    expect(store.selectedElements[0]).toMatchObject({ xMm: 10, yMm: 10 })
    expect(store.alignmentGuides.position).toBeNull()
  })

  it('snaps resize handles to neighbouring element edges', () => {
    store.beginGesture()
    store.moveGesture(mmToPx(9.5) * store.zoom, 0, true)
    expect(store.selectedElements[0].widthMm).toBeCloseTo(40)
    expect(store.alignmentGuides.x).toEqual([50])
    store.endGesture()
  })

  it('cancels gestures and ignores no-op commands', () => {
    store.beginGesture()
    store.moveGesture(50, 10)
    store.cancelGesture()
    store.patchSelected({ xMm: 10 })
    expect(store.dirty).toBe(false)
    expect(store.history.past).toHaveLength(0)
  })

  it('clamps a selected group without changing relative spacing', () => {
    store.selectElement('b', true)
    store.moveSelection(-100, 100)
    expect(store.selectedElements.map(e => [e.xMm, e.yMm])).toEqual([[0, 40], [40, 50]])
  })

  it('aligns selected elements on every edge and distributes three elements', () => {
    store.selectElement('b', true)
    expect(store.alignSelection('right')).toBe(true)
    expect(store.selectedElements.map(element => element.xMm + element.widthMm)).toEqual([80, 80])
    store.undo()
    expect(store.alignSelection('middle')).toBe(true)
    expect(store.selectedElements.map(element => element.yMm + element.heightMm / 2)).toEqual([20, 20])

    store.execute(document => document.body[0].elements.push({ id: 'c', type: 'TEXT', xMm: 100, yMm: 30, widthMm: 20, heightMm: 10, binding: { source: 'CONSTANT', value: '第三项' } }))
    store.selectElement('c', true)
    expect(store.distributeSelection('horizontal')).toBe(true)
    expect(store.selectedElements.map(element => element.xMm)).toEqual([10, 55, 100])
    expect(store.distributeSelection('vertical')).toBe(true)
    expect(store.selectedElements.map(element => element.yMm)).toEqual([15, 22.5, 30])
  })

  it('rejects out-of-bounds properties and invalid imports atomically', () => {
    const before = store.serialize()
    expect(store.patchSelected({ widthMm: 300 })).toBe(false)
    expect(store.error).toBeTruthy()
    expect(store.serialize()).toBe(before)
    expect(() => store.load({ protocol: 'untrusted' })).toThrow()
    expect(store.serialize()).toBe(before)
  })

  it('gives pasted elements distinct IDs and preserves them through undo/redo', () => {
    store.selectElement('b', true)
    store.copySelection()
    store.pasteSelection()
    const ids = store.selectedIds.slice()
    expect(new Set(store.activeSurface.elements.map(e => e.id)).size).toBe(4)
    store.undo()
    expect(store.activeSurface.elements).toHaveLength(2)
    store.redo()
    expect(store.activeSurface.elements.slice(2).map(e => e.id)).toEqual(ids)
  })

  it('selects all, duplicates and changes selected stacking order atomically', () => {
    store.selectAll()
    expect(store.selectedIds).toEqual(['a', 'b'])
    expect(store.duplicateSelection()).toBe(true)
    const duplicates = store.selectedIds.slice()
    expect(store.activeSurface.elements).toHaveLength(4)
    expect(store.moveSelectionLayer('back')).toBe(true)
    expect(store.activeSurface.elements.slice(0, 2).map(element => element.id)).toEqual(duplicates)
    expect(store.moveSelectionLayer('front')).toBe(true)
    expect(store.activeSurface.elements.slice(-2).map(element => element.id)).toEqual(duplicates)
    store.undo()
    expect(store.activeSurface.elements.slice(0, 2).map(element => element.id)).toEqual(duplicates)
  })

  it('limits retained history and clears redo when editing after undo', () => {
    for (let i = 0; i < 70; i++) {
      store.patchSelected({ binding: { source: 'CONSTANT', value: String(i) } })
    }
    expect(store.history.past.length).toBeLessThanOrEqual(50)
    store.undo()
    store.patchSelected({ binding: { source: 'CONSTANT', value: 'new' } })
    expect(store.canRedo).toBe(false)
  })

  it('marks only the saved revision clean and preserves edits during async save', async () => {
    store.patchSelected({ xMm: 11 })
    let complete
    const saving = store.save(() => new Promise((resolve) => {
      complete = resolve
    }))
    store.patchSelected({ xMm: 12 })
    complete()
    await saving
    expect(store.dirty).toBe(true)
    store.undo()
    expect(store.dirty).toBe(false)
  })

  it('keeps a failed save dirty and does not mark another loaded document saved', async () => {
    store.patchSelected({ xMm: 11 })
    expect(await store.save(async () => {
      throw new Error('保存失败')
    })).toBe(false)
    expect(store.dirty).toBe(true)
    let complete
    const saving = store.save(() => new Promise((resolve) => {
      complete = resolve
    }))
    store.load(createPrintDocument())
    complete()
    await saving
    expect(store.dirty).toBe(false)
    expect(store.document.body).toHaveLength(0)
  })
  it('keeps imported section IDs distinct from header/footer editor surfaces', () => {
    const document = fixture()
    document.body[0].id = 'header'
    store.load(document)
    expect(store.activeSurface.kind).toBe('FIXED')
    store.selectElement('a')
    store.patchSelected({ xMm: 11 })
    expect(store.document.body[0].elements[0].xMm).toBe(11)
    expect(store.document.header.elements).toHaveLength(0)
    store.selectSurface('header')
    expect(store.activeSurface).toEqual(store.document.header)
  })
})
