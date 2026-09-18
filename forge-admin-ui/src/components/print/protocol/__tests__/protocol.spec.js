import { describe, expect, it } from 'vitest'
import { createPrintDocument } from '../types'
import { mmToPx, paperGeometry, pxToMm, screenDeltaToMm } from '../units'
import { assertPrintDocument, validatePrintDocument } from '../validate'

function textElement(overrides = {}) {
  return {
    id: 'title',
    type: 'TEXT',
    xMm: 0,
    yMm: 0,
    widthMm: 60,
    heightMm: 10,
    binding: { source: 'CONSTANT', value: '申请单' },
    ...overrides,
  }
}

function fixedDocument(element = textElement()) {
  const doc = createPrintDocument()
  doc.body.push({ id: 'head', kind: 'FIXED', heightMm: 20, elements: [element] })
  return doc
}

describe('print document protocol v1', () => {
  it('creates independent serializable defaults and preserves a template round trip', () => {
    const doc = fixedDocument()
    expect(assertPrintDocument(JSON.parse(JSON.stringify(doc)))).toEqual(doc)
    doc.paper.marginMm.top = 30
    expect(createPrintDocument().paper.marginMm.top).toBe(10)
  })

  it('rejects unknown protocols, versions, section kinds and element types', () => {
    for (const change of [
      doc => doc.protocol = 'hiprint',
      doc => doc.schemaVersion = 2,
      doc => doc.body[0].kind = 'HTML',
      doc => doc.body[0].elements[0].type = 'SCRIPT',
    ]) {
      const doc = fixedDocument()
      change(doc)
      expect(() => assertPrintDocument(doc)).toThrow()
    }
  })

  it('reports paths instead of silently dropping unsupported properties', () => {
    const doc = fixedDocument(textElement({ formatter: '() => alert(1)' }))
    expect(validatePrintDocument(doc)).toContainEqual(expect.objectContaining({
      path: 'body[0].elements[0].formatter',
      code: 'UNKNOWN_PROPERTY',
    }))
  })

  it('checks finite dimensions, margins and the printable body', () => {
    for (const width of [0, -1, Number.NaN, Number.POSITIVE_INFINITY, '210']) {
      const doc = createPrintDocument()
      doc.paper.widthMm = width
      expect(validatePrintDocument(doc).length).toBeGreaterThan(0)
    }
    const doc = createPrintDocument()
    doc.header.heightMm = 280
    expect(() => assertPrintDocument(doc)).toThrow()
  })

  it('rejects elements outside their fixed section and duplicate IDs', () => {
    const doc = fixedDocument(textElement({ yMm: 15, heightMm: 10 }))
    expect(validatePrintDocument(doc)).toContainEqual(expect.objectContaining({ code: 'OUT_OF_BOUNDS' }))
    const duplicate = fixedDocument()
    duplicate.body[0].elements.push(textElement())
    expect(validatePrintDocument(duplicate)).toContainEqual(expect.objectContaining({ code: 'DUPLICATE_ID' }))
  })

  it('allows primitive constants including zero and false, but rejects executable styles', () => {
    expect(validatePrintDocument(fixedDocument(textElement({ binding: { source: 'CONSTANT', value: 0 } })))).toEqual([])
    expect(validatePrintDocument(fixedDocument(textElement({ binding: { source: 'CONSTANT', value: false } })))).toEqual([])
    const doc = fixedDocument(textElement({ style: { backgroundImage: 'url(https://example.invalid/a)' } }))
    expect(() => assertPrintDocument(doc)).toThrow()
  })

  it('checks table column widths, merged header spans and safe collection paths', () => {
    const doc = createPrintDocument()
    doc.body.push({
      id: 'items',
      kind: 'TABLE',
      collectionPath: 'children.items',
      repeatHeader: true,
      columns: [{ id: 'name', field: 'name', title: '名称', widthMm: 100 }],
      headerRows: [{ cells: [{ text: '明细', span: 1 }] }],
    })
    expect(validatePrintDocument(doc)).toEqual([])
    doc.body[0].headerRows[0].cells[0].span = 2
    expect(() => assertPrintDocument(doc)).toThrow()
    doc.body[0].headerRows[0].cells[0].span = 1
    doc.body[0].collectionPath = 'children.__proto__.items'
    expect(() => assertPrintDocument(doc)).toThrow()
  })

  it('rejects prototype keys, cycles, excessive collections and foreign image URLs', () => {
    const poisoned = JSON.parse(JSON.stringify(createPrintDocument()).replace('"resources":[]', '"resources":[],"__proto__":{}'))
    expect(() => assertPrintDocument(poisoned)).toThrow()
    const circular = createPrintDocument()
    circular.body.push(circular)
    expect(() => assertPrintDocument(circular)).toThrow()
    const doc = fixedDocument(textElement({ type: 'IMAGE', binding: { source: 'CONSTANT', value: 'https://example.invalid/private.png' } }))
    expect(() => assertPrintDocument(doc)).toThrow()
  })
})

describe('physical paper coordinates', () => {
  it('round trips millimetres without rounding physical coordinates', () => {
    expect(mmToPx(25.4)).toBeCloseTo(96, 10)
    expect(pxToMm(mmToPx(210.125))).toBeCloseTo(210.125, 8)
  })

  it('converts screen movement by zoom without changing paper geometry', () => {
    for (const zoom of [0.5, 1, 1.5]) {
      expect(screenDeltaToMm(mmToPx(10) * zoom, zoom)).toBeCloseTo(10, 8)
    }
    expect(() => screenDeltaToMm(10, 0)).toThrow()
    const doc = createPrintDocument()
    doc.paper.orientation = 'LANDSCAPE'
    expect(paperGeometry(doc)).toMatchObject({ widthMm: 297, heightMm: 210, contentWidthMm: 277 })
  })
})
