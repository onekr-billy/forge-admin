import { describe, expect, it, vi } from 'vitest'
import { createPrintDocument } from '../../protocol/types'
import { requireLocalFont } from '../fonts'
import { MeasurementCache } from '../measurementCache'
import { preparePrintResources } from '../resources'

function imageDocument() {
  const doc = createPrintDocument()
  doc.header = { heightMm: 20, repeat: true, elements: [{ id: 'logo', type: 'IMAGE', xMm: 0, yMm: 0, widthMm: 20, heightMm: 20, binding: { source: 'FIELD', path: 'main.logo' } }] }
  return doc
}

describe('bounded print resource session', () => {
  it('rejects a requested local font when the browser cannot load it', async () => {
    class MissingFont {
      async load() {
        throw new Error('font missing')
      }
    }
    await expect(requireLocalFont('Unavailable Font, sans-serif', MissingFont)).rejects.toMatchObject({ code: 'FONT_UNAVAILABLE' })
    await expect(requireLocalFont('sans-serif', MissingFont)).resolves.toBeUndefined()
  })
  it('resolves authorized files once and revokes URLs exactly once', async () => {
    const revoke = vi.fn()
    const resolver = vi.fn(async () => new Blob(['image'], { type: 'image/png' }))
    const resources = await preparePrintResources(imageDocument(), { main: { logo: '123' } }, {
      resolveFile: resolver,
      decodeImage: vi.fn(async () => {}),
      fonts: null,
      createObjectURL: () => 'blob:local',
      revokeObjectURL: revoke,
    })
    expect(resources.images.get('logo')).toBe('blob:local')
    expect(resolver).toHaveBeenCalledWith('123', expect.objectContaining({ signal: expect.any(AbortSignal) }))
    resources.dispose()
    resources.dispose()
    expect(revoke).toHaveBeenCalledTimes(1)
  })
  it('rejects remote resources and cleans decoded failures', async () => {
    await expect(preparePrintResources(imageDocument(), { main: { logo: 'https://untrusted/image' } }, { fonts: null })).rejects.toMatchObject({ code: 'INVALID_RESOURCE' })
    const revoke = vi.fn()
    await expect(preparePrintResources(imageDocument(), { main: { logo: '123' } }, {
      fonts: null,
      resolveFile: async () => new Blob(['x'], { type: 'image/png' }),
      createObjectURL: () => 'blob:bad',
      revokeObjectURL: revoke,
      decodeImage: async () => { throw new Error('decode failed') },
    })).rejects.toMatchObject({ code: 'RESOURCE_FAILED', path: 'logo' })
    expect(revoke).toHaveBeenCalledWith('blob:bad')
  })
  it('bounds a hanging resolver and aborts the underlying request', async () => {
    let requestSignal
    const result = preparePrintResources(imageDocument(), { main: { logo: '123' } }, {
      fonts: null,
      timeoutMs: 10,
      resolveFile: (_, options) => {
        requestSignal = options.signal
        return new Promise(() => {})
      },
    })
    await expect(result).rejects.toMatchObject({ code: 'RESOURCE_TIMEOUT' })
    expect(requestSignal.aborted).toBe(true)
  })
  it('keys measurements by text, width and complete style and evicts old entries', () => {
    const cache = new MeasurementCache(2)
    cache.set(['A', 10, { fontSizePt: 10 }], 1)
    expect(cache.get(['A', 10, { fontSizePt: 12 }])).toBeUndefined()
    expect(cache.get(['A', 11, { fontSizePt: 10 }])).toBeUndefined()
    cache.set(['B'], 2)
    cache.set(['C'], 3)
    expect(cache.get(['A', 10, { fontSizePt: 10 }])).toBeUndefined()
  })
})
