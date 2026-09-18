import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPrintDocument } from '../../protocol/types'
import { createBrowserPrintSession } from '../browserPrint'
import PrintPreview from '../PrintPreview.vue'

const disposeResources = vi.hoisted(() => vi.fn())
vi.mock('../../engine/resources', () => ({ preparePrintResources: vi.fn(async () => ({ images: new Map(), dispose: disposeResources })) }))
vi.mock('../../engine/measure', () => ({ createBrowserMeasurer: () => ({ dispose() {} }) }))
vi.mock('../browserPrint', () => ({ createBrowserPrintSession: vi.fn() }))

describe('preview ownership', () => {
  beforeEach(() => vi.clearAllMocks())

  it('hides the print action when showing a design-only preview', async () => {
    const wrapper = mount(PrintPreview, {
      props: { template: createPrintDocument(), context: {}, catalog: [], allowPrint: false, dataLabel: '模板预览' },
      global: { stubs: { NSelect: true, NSpin: { template: '<div><slot /></div>' }, NButton: { template: '<button><slot /></button>' } } },
    })
    await flushPromises()
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(wrapper.text()).toContain('模板预览')
    expect(createBrowserPrintSession).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('disposes a pending print session when the preview unmounts and never prints stale content', async () => {
    const wrapper = mount(PrintPreview, {
      props: { template: createPrintDocument(), context: {}, catalog: [] },
      global: { stubs: { NSelect: true, NSpin: { template: '<div><slot /></div>' }, NButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' } } },
    })
    await flushPromises()
    let complete
    const print = vi.fn()
    const dispose = vi.fn()
    createBrowserPrintSession.mockImplementation(() => new Promise((resolve) => {
      complete = resolve
    }))
    await wrapper.get('button').trigger('click')
    wrapper.unmount()
    complete({ print, dispose })
    await flushPromises()
    expect(print).not.toHaveBeenCalled()
    expect(dispose).toHaveBeenCalledOnce()
    expect(disposeResources).toHaveBeenCalledOnce()
  })
})
