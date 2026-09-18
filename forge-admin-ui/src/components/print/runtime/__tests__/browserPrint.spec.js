import { describe, expect, it, vi } from 'vitest'
import { createBrowserPrintSession } from '../browserPrint'

const result = { geometry: { widthMm: 210, heightMm: 297 }, pages: [{ number: 1, header: { elements: [] }, footer: { elements: [] }, fragments: [] }] }

describe('isolated browser print session', () => {
  it('prints the frozen page tree and removes the iframe after the dialog closes', async () => {
    const before = document.head.innerHTML
    const event = vi.fn()
    const session = await createBrowserPrintSession(result, { onEvent: event })
    const frame = document.querySelector('iframe[data-forge-print]')
    expect(frame.contentDocument.querySelectorAll('[data-print-page]')).toHaveLength(1)
    expect(frame.contentDocument.querySelector('style').textContent).toContain('@page')
    frame.contentWindow.focus = vi.fn()
    frame.contentWindow.print = vi.fn()
    session.print()
    expect(frame.contentWindow.print).toHaveBeenCalledOnce()
    expect(event).toHaveBeenCalledWith({ result: 'DIALOG_OPENED', pageCount: 1 })
    frame.contentWindow.dispatchEvent(new Event('afterprint'))
    expect(document.querySelector('iframe[data-forge-print]')).toBeNull()
    expect(document.head.innerHTML).toBe(before)
    session.dispose()
  })
  it('cleans failed print invocations and never reports physical success', async () => {
    const event = vi.fn()
    const session = await createBrowserPrintSession(result, { onEvent: event })
    const frame = document.querySelector('iframe[data-forge-print]')
    frame.contentWindow.focus = vi.fn()
    frame.contentWindow.print = () => {
      throw new Error('blocked')
    }
    expect(() => session.print()).toThrow()
    expect(document.querySelector('iframe[data-forge-print]')).toBeNull()
    expect(event).not.toHaveBeenCalled()
  })
  it('cancels before mounting without leaving a frame', async () => {
    const controller = new AbortController()
    controller.abort()
    await expect(createBrowserPrintSession(result, { signal: controller.signal })).rejects.toBeDefined()
    expect(document.querySelector('iframe[data-forge-print]')).toBeNull()
  })
  it('disposes a prepared iframe if its owning preview is cancelled', async () => {
    const controller = new AbortController()
    const session = await createBrowserPrintSession(result, { signal: controller.signal })
    expect(document.querySelectorAll('iframe[data-forge-print]')).toHaveLength(1)
    controller.abort()
    expect(document.querySelector('iframe[data-forge-print]')).toBeNull()
    expect(() => session.print()).toThrow()
  })
})
