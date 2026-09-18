import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import DictTag from '@/components/DictTag.vue'
import { clearDictCache, getDictData, useDict } from '../useDict'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/utils', () => ({ request: requestMocks }))

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function response(label = '启用', value = '1') {
  return { code: 200, data: [{ dictLabel: label, dictValue: value }] }
}

const wrappers = []
const global = { stubs: { NTag: { template: '<span><slot /></span>' } } }

function mountTag(props) {
  const wrapper = mount(DictTag, { props, global })
  wrappers.push(wrapper)
  return wrapper
}

function mountDicts(...types) {
  let state
  const wrapper = mount(defineComponent({
    setup() {
      state = useDict(...types)
      return () => h(DictTag, { options: state.dict.value[types[0]] || [], value: 1 })
    },
  }), { global })
  wrappers.push(wrapper)
  return { state, wrapper }
}

describe('useDict managed cache endpoint', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearDictCache()
    requestMocks.get.mockReset()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    vi.restoreAllMocks()
  })

  it('loads business dictionaries through the managed type endpoint', async () => {
    requestMocks.get.mockResolvedValue({
      code: 200,
      data: [
        { dictCode: 2, dictLabel: '停用', dictValue: '0', dictSort: 2 },
        { dictCode: 1, dictLabel: '启用', dictValue: '1', dictSort: 1 },
      ],
    })

    const result = await getDictData('sys/org type', true)

    expect(requestMocks.get).toHaveBeenCalledWith(
      '/system/dict/data/type/sys%2Forg%20type',
      { needTip: false },
    )
    expect(result.map(item => item.value)).toEqual(['1', '0'])
  })

  it('一个慢字典不阻塞已成功的状态标签更新', async () => {
    const slow = deferred()
    requestMocks.get.mockImplementation(url => url.endsWith('/slow') ? slow.promise : Promise.resolve(response()))
    const { state, wrapper } = mountDicts('sys_user_status', 'slow')
    const originalDict = state.dict.value

    await flushPromises()
    expect(state.dict.value).toBe(originalDict)
    expect(state.loading.value).toBe(true)
    expect(wrapper.text()).toBe('启用')

    slow.resolve(response('普通用户'))
    await flushPromises()
    expect(state.loading.value).toBe(false)
  })

  it('失败字典立即重试，不等待同批其他慢请求', async () => {
    const slow = deferred()
    let statusCalls = 0
    requestMocks.get.mockImplementation((url) => {
      if (url.endsWith('/slow')) {
        return slow.promise
      }
      statusCalls += 1
      return statusCalls === 1 ? Promise.reject(new Error('临时网络错误')) : Promise.resolve(response())
    })
    const { state, wrapper } = mountDicts('sys_user_status', 'slow')

    await flushPromises()
    expect(statusCalls).toBe(2)
    expect(wrapper.text()).toBe('启用')
    expect(state.errors.value).toEqual({})

    slow.resolve(response())
    await flushPromises()
  })

  it('多个自加载标签共享请求，首次失败仅重试一次', async () => {
    const first = deferred()
    requestMocks.get.mockReturnValueOnce(first.promise).mockResolvedValue(response('普通角色'))
    const firstTag = mountTag({ dictType: 'sys_role_type', value: 1 })
    const secondTag = mountTag({ dictType: 'sys_role_type', value: '1' })

    expect(requestMocks.get).toHaveBeenCalledTimes(1)
    expect(firstTag.text()).toBe('—')
    first.reject(new Error('临时网络错误'))
    await flushPromises()

    expect(requestMocks.get).toHaveBeenCalledTimes(2)
    expect(firstTag.text()).toBe('普通角色')
    expect(secondTag.text()).toBe('普通角色')
  })

  it('持续失败不缓存空数组，后续重试可修复已挂载标签和页面选项', async () => {
    requestMocks.get.mockRejectedValue(new Error('网络错误'))
    const tag = mountTag({ dictType: 'sys_user_status', value: 1 })
    const { state, wrapper } = mountDicts('sys_user_status')
    await flushPromises()
    expect(tag.text()).toBe('1')
    expect(wrapper.text()).toBe('1')
    expect(state.errors.value.sys_user_status).toBeTruthy()
    const callsAfterFailure = requestMocks.get.mock.calls.length
    expect(callsAfterFailure).toBe(2)

    requestMocks.get.mockResolvedValue(response())
    await getDictData('sys_user_status')
    await flushPromises()

    expect(requestMocks.get).toHaveBeenCalledTimes(callsAfterFailure + 1)
    expect(tag.text()).toBe('启用')
    expect(wrapper.text()).toBe('启用')
    expect(state.errors.value).toEqual({})
  })

  it('缓存命中立即回显，强制刷新同步已有标签', async () => {
    requestMocks.get.mockResolvedValue(response('原标签'))
    await getDictData('sys_role_type')
    const tag = mountTag({ dictType: 'sys_role_type', value: 1 })
    expect(tag.text()).toBe('原标签')
    await flushPromises()
    expect(requestMocks.get).toHaveBeenCalledTimes(1)

    requestMocks.get.mockResolvedValue(response('新标签'))
    await getDictData('sys_role_type', true)
    await flushPromises()
    expect(tag.text()).toBe('新标签')
  })

  it('切换字典类型后，迟到的旧响应不能覆盖当前标签', async () => {
    const oldRequest = deferred()
    requestMocks.get.mockImplementation(url => url.endsWith('/old') ? oldRequest.promise : Promise.resolve(response('新类型')))
    const tag = mountTag({ dictType: 'old', value: 1 })
    await tag.setProps({ dictType: 'new' })
    await flushPromises()
    expect(tag.text()).toBe('新类型')

    oldRequest.resolve(response('旧类型'))
    await flushPromises()
    expect(tag.text()).toBe('新类型')
  })

  it('显式 options 优先，支持异步选项、数字零和未知值原样回显', async () => {
    const tag = mountTag({ dictType: 'sys_user_status', options: [], value: 0 })
    expect(requestMocks.get).not.toHaveBeenCalled()
    await tag.setProps({ options: [{ value: '0', label: '停用' }] })
    expect(tag.text()).toBe('停用')
    await tag.setProps({ value: 9 })
    expect(tag.text()).toBe('9')

    requestMocks.get.mockResolvedValue(response())
    await tag.setProps({ options: null, value: 1 })
    await flushPromises()
    expect(tag.text()).toBe('启用')
  })

  it('合法空字典正常缓存，不因无匹配值无限重试', async () => {
    requestMocks.get.mockResolvedValue({ code: 200, data: [] })
    const tag = mountTag({ dictType: 'empty', value: 9 })
    await flushPromises()
    expect(tag.text()).toBe('9')
    await getDictData('empty')
    expect(requestMocks.get).toHaveBeenCalledTimes(1)
  })

  it('刷新失败保留旧标签，其他字典更新不能清除该失败状态', async () => {
    requestMocks.get.mockResolvedValue(response())
    const { state, wrapper } = mountDicts('sys_user_status', 'sys_role_type')
    await flushPromises()

    requestMocks.get.mockRejectedValue(new Error('刷新失败'))
    await state.reload('sys_user_status')
    expect(wrapper.text()).toBe('启用')
    expect(state.errors.value.sys_user_status).toBe('刷新失败')

    requestMocks.get.mockResolvedValue(response('普通角色'))
    await getDictData('sys_role_type', true)
    await flushPromises()
    expect(state.errors.value.sys_user_status).toBe('刷新失败')

    await state.reload('extra_type')
    expect(state.dict.value.extra_type[0].label).toBe('普通角色')
  })

  it('清缓存后旧请求不能回填，后续请求结果保持有效', async () => {
    const oldRequest = deferred()
    requestMocks.get.mockReturnValueOnce(oldRequest.promise).mockResolvedValue(response('新标签'))
    const pending = getDictData('sys_role_type')
    clearDictCache('sys_role_type')
    await getDictData('sys_role_type')
    oldRequest.resolve(response('旧标签'))
    await pending

    const data = await getDictData('sys_role_type')
    expect(data[0].label).toBe('新标签')
  })
})
