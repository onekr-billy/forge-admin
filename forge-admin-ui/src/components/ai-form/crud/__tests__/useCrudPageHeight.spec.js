import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, nextTick, reactive, ref } from 'vue'
import { useCrudPageHeight } from '../composables/useCrudPageHeight'

const wrappers = []
let rafs
let observers
let sequence
function flushRaf() {
  const callbacks = [...rafs.values()]
  rafs.clear()
  callbacks.forEach(callback => callback())
}
async function setup({ props: rawProps = {}, contentDriven = true, hidden = false, excluded = '', top = 100, gap = 400 } = {}) {
  const props = reactive({ formOnly: false, maxHeight: undefined, ...rawProps })
  const showInlineFormWorkspacePane = ref(false)
  let runtime
  const wrapper = mount({
    setup() {
      runtime = useCrudPageHeight({ props, showInlineFormWorkspacePane })
      return () => h('div', { class: excluded }, [h('div', { ref: runtime.crudRootRef })])
    },
  })
  wrappers.push(wrapper)
  const element = runtime.crudRootRef.value
  let visible = !hidden
  Object.defineProperty(element, 'offsetParent', { configurable: true, get: () => visible ? wrapper.element : null })
  Object.defineProperty(wrapper.element, 'clientHeight', {
    configurable: true,
    get: () => contentDriven && element.style.display === 'none' ? 600 - gap : 600,
  })
  vi.spyOn(element, 'getBoundingClientRect').mockReturnValue({ top })
  await nextTick()
  flushRaf()
  return {
    ...runtime,
    wrapper,
    props,
    showInlineFormWorkspacePane,
    reveal: () => {
      visible = true
    },
  }
}

beforeEach(() => {
  sequence = 0
  rafs = new Map()
  observers = []
  vi.stubGlobal('innerHeight', 900)
  vi.stubGlobal('requestAnimationFrame', vi.fn((callback) => {
    rafs.set(++sequence, callback)
    return sequence
  }))
  vi.stubGlobal('cancelAnimationFrame', vi.fn(id => rafs.delete(id)))
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback) {
      this.callback = callback
      this.observe = vi.fn()
      this.disconnect = vi.fn()
      observers.push(this)
    }
  })
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('页面高度适配', () => {
  it('接管内容驱动的父容器并随视口更新，不反复探测', async () => {
    const runtime = await setup()
    expect(runtime.pageHeightStyle.value).toEqual({ height: '784px' })
    expect(runtime.crudRootRef.value.style.display).toBe('')
    expect(observers[0].observe).toHaveBeenCalledWith(runtime.crudRootRef.value)
    vi.stubGlobal('innerHeight', 800)
    window.dispatchEvent(new Event('resize'))
    flushRaf()
    expect(runtime.pageHeightStyle.value).toEqual({ height: '684px' })
  })

  it('明确高度的父容器不接管', async () => {
    const runtime = await setup({ contentDriven: false })
    expect(runtime.pageHeightStyle.value).toBeNull()
  })

  it.each([7, 8])('父高度变化 %s px 不超过探测容差', async (gap) => {
    expect((await setup({ gap })).pageHeightStyle.value).toBeNull()
  })

  it('保留最小高度 320 的边界', async () => {
    expect((await setup({ top: 565 })).pageHeightStyle.value).toBeNull()
    expect((await setup({ top: 564 })).pageHeightStyle.value).toEqual({ height: '320px' })
  })

  it.each([{ formOnly: true }, { maxHeight: 500 }])('排除指定布局 %o', async (props) => {
    expect((await setup({ props })).pageHeightStyle.value).toBeNull()
  })

  it.each(['n-modal', 'n-drawer-content', 'ai-crud-preview'])('排除 %s 容器', async (excluded) => {
    expect((await setup({ excluded })).pageHeightStyle.value).toBeNull()
  })

  it('隐藏页签等待可见及 observer 通知后再探测', async () => {
    const runtime = await setup({ hidden: true })
    expect(runtime.pageHeightStyle.value).toBeNull()
    runtime.reveal()
    observers[0].callback()
    flushRaf()
    expect(runtime.pageHeightStyle.value).toEqual({ height: '784px' })
  })

  it('显式 maxHeight 不妨碍内联表单工作区接管', async () => {
    const runtime = await setup({ props: { maxHeight: 500 } })
    runtime.showInlineFormWorkspacePane.value = true
    await nextTick()
    flushRaf()
    expect(runtime.pageHeightStyle.value).toEqual({ height: '784px' })
  })

  it('卸载取消已排定 RAF、断开 observer 并移除同一个监听器', async () => {
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')
    const runtime = await setup()
    window.dispatchEvent(new Event('resize'))
    const resizeCallback = add.mock.calls.find(([event]) => event === 'resize')[1]
    runtime.wrapper.unmount()
    wrappers.splice(wrappers.indexOf(runtime.wrapper), 1)
    expect(cancelAnimationFrame).toHaveBeenCalled()
    expect(rafs.size).toBe(0)
    expect(observers[0].disconnect).toHaveBeenCalledOnce()
    expect(remove).toHaveBeenCalledWith('resize', resizeCallback)
  })

  it('两个实例的探测与清理互不影响', async () => {
    const first = await setup({ top: 100 })
    const second = await setup({ top: 200 })
    first.wrapper.unmount()
    wrappers.splice(wrappers.indexOf(first.wrapper), 1)
    vi.stubGlobal('innerHeight', 800)
    window.dispatchEvent(new Event('resize'))
    flushRaf()
    expect(first.pageHeightStyle.value).toEqual({ height: '784px' })
    expect(second.pageHeightStyle.value).toEqual({ height: '584px' })
    expect(observers[1].disconnect).not.toHaveBeenCalled()
  })
})
