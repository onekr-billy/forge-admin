import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'
import CapabilityApplicationSource from '../components/CapabilityApplicationSource.vue'

const api = vi.hoisted(() => ({ businessApplicationList: vi.fn(), businessApplicationRuntimeByCode: vi.fn() }))
vi.mock('@/api/business-application', () => api)
const context = { lockApplication: true, applicationId: '9007199254740999', applicationCode: 'law', applicationName: '案件管理' }
const published = { objects: [{ objectId: '9223372036854775801', objectCode: 'case' }], entries: [{ id: '8', objectId: '9223372036854775801', appName: '案件登记' }] }
let wrapper
let store
function start(seed = context) {
  const pinia = createPinia()
  setActivePinia(pinia)
  store = useCapabilityRegistrationStore()
  store.initialize(seed)
  wrapper = mount(CapabilityApplicationSource, { global: { plugins: [pinia], stubs: {
    NFormItem: { template: '<div><slot /></div>' },
    NSelect: { name: 'NSelect', props: ['value', 'loading', 'options', 'disabled'], template: '<div />' },
    NAlert: { template: '<div><slot /></div>' },
    NButton: { template: '<button><slot /></button>' },
    NEmpty: true,
  } } })
}
beforeEach(() => {
  vi.clearAllMocks()
  api.businessApplicationList.mockResolvedValue({ data: [] })
  api.businessApplicationRuntimeByCode.mockResolvedValue({ data: published })
})
afterEach(() => wrapper?.unmount())

describe('application-scoped registration source', () => {
  it('auto-fills the current application without listing or reselecting applications', async () => {
    start()
    await flushPromises()
    expect(wrapper.text()).toContain('当前应用案件管理')
    expect(api.businessApplicationList).not.toHaveBeenCalled()
    expect(api.businessApplicationRuntimeByCode).toHaveBeenCalledTimes(1)
    expect(api.businessApplicationRuntimeByCode).toHaveBeenCalledWith('law')
    expect(store.applicationId).toBe(context.applicationId)
    expect(store.pageId).toBe('entry/8')
    expect(wrapper.findAllComponents({ name: 'NSelect' })).toHaveLength(1)
    expect(wrapper.emitted('select').at(-1)[0].objectId).toBe('9223372036854775801')
  })
  it('shows pending page loading and does not turn it into an empty result', async () => {
    let complete
    api.businessApplicationRuntimeByCode.mockImplementationOnce(() => new Promise((resolve) => {
      complete = resolve
    }))
    start()
    await flushPromises()
    expect(wrapper.text()).toContain('正在加载应用的已发布页面')
    expect(wrapper.findComponent({ name: 'NSelect' }).props('loading')).toBe(true)
    wrapper.unmount()
    complete({ data: published })
    await flushPromises()
    expect(store.pageId).toBeNull()
  })
  it('leaves multiple pages for the user to select rather than selecting the wrong form', async () => {
    api.businessApplicationRuntimeByCode.mockResolvedValueOnce({ data: { ...published, entries: [...published.entries, { ...published.entries[0], id: '9', appName: '案件列表' }] } })
    start()
    await flushPromises()
    expect(store.pageId).toBeNull()
    expect(wrapper.findComponent({ name: 'NSelect' }).props('options')).toHaveLength(2)
  })
  it('clears locked context when reopening the global registration entry', () => {
    start()
    store.initialize()
    expect(store.sourceContext.lockApplication).toBe(false)
    expect(store.sourceContext.applicationName).toBe('')
    expect(store.applicationId).toBeNull()
  })
})
