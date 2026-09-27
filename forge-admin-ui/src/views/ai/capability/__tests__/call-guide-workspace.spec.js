import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import { useCapabilityCallGuide } from '../components/useCapabilityCallGuide'

const api = vi.hoisted(() => ({ getCapabilityCallGuideClients: vi.fn(), getCapabilityCallGuide: vi.fn(), useCurrentCapabilityGrantVersion: vi.fn(), downloadCapabilityMarkdown: vi.fn(), downloadCapabilityOpenApi: vi.fn() }))
const push = vi.hoisted(() => vi.fn())
vi.mock('@/api/ai/capability', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
const client = id => ({ id, clientName: `系统${id}`, clientCode: `client${id}`, status: 'ENABLED' })
function deferred() {
  let resolve
  const promise = new Promise(done => resolve = done)
  return { resolve, promise }
}
let wrapper, helper, props
function start(values = {}) {
  props = reactive({ show: true, capability: { id: 'a' }, canUpdateGrant: false, ...values })
  wrapper = mount(defineComponent({ setup() {
    helper = useCapabilityCallGuide(props, vi.fn())
    return () => h('div')
  } }), { global: { plugins: [createPinia()] } })
}
beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  api.getCapabilityCallGuideClients.mockResolvedValue({ data: [client('1')] })
  api.getCapabilityCallGuide.mockImplementation((id, clientId) => Promise.resolve({ data: { capabilityId: id, clientId, ready: true, availableAuthModes: ['OAUTH'] } }))
  window.$message = { error: vi.fn(), success: vi.fn() }
  window.$dialog = { warning: vi.fn() }
})
afterEach(() => wrapper?.unmount())
describe('call guide workspace lifecycle', () => {
  it('loads on first mount with show=true and selects the single available client', async () => {
    start()
    expect(helper.clientsLoading.value).toBe(true)
    await flushPromises()
    expect(api.getCapabilityCallGuide).toHaveBeenCalledWith('a', '1')
    expect(helper.guide.value.clientId).toBe('1')
  })
  it('requires explicit selection for multiple clients', async () => {
    api.getCapabilityCallGuideClients.mockResolvedValue({ data: [client('1'), client('2')] })
    start()
    await flushPromises()
    expect(helper.selectedClientId.value).toBeNull()
    expect(api.getCapabilityCallGuide).not.toHaveBeenCalled()
  })
  it('ignores a previous client response and a cleared selection', async () => {
    start()
    await flushPromises()
    const old = deferred()
    api.getCapabilityCallGuide.mockReturnValueOnce(old.promise)
    const pending = helper.loadGuide('old')
    await helper.loadGuide('new')
    old.resolve({ data: { clientId: 'old' } })
    await pending
    expect(helper.guide.value.clientId).toBe('new')
    const cleared = deferred()
    api.getCapabilityCallGuide.mockReturnValueOnce(cleared.promise)
    const pendingClear = helper.loadGuide('cleared')
    await helper.loadGuide(null)
    cleared.resolve({ data: { clientId: 'cleared' } })
    await pendingClear
    expect(helper.guide.value).toBeNull()
    expect(helper.guideLoading.value).toBe(false)
  })
  it('does not refill a closed workspace or one switched to another capability', async () => {
    const old = deferred()
    api.getCapabilityCallGuideClients.mockReturnValueOnce(old.promise)
    start()
    props.capability = { id: 'b' }
    await flushPromises()
    old.resolve({ data: [client('old')] })
    await flushPromises()
    expect(helper.guide.value.capabilityId).toBe('b')
    props.show = false
    await nextTick()
    expect(helper.guide.value).toBeNull()
    expect(helper.clients.value).toEqual([])
  })
  it('shows a recoverable guide error and does not display the prior guide', async () => {
    start()
    await flushPromises()
    api.getCapabilityCallGuide.mockRejectedValueOnce(new Error('检查不可用'))
    await helper.loadGuide('1')
    expect(helper.guideError.value).toBe('检查不可用')
    expect(helper.guide.value).toBeNull()
    await helper.loadGuide('1')
    expect(helper.guideError.value).toBe('')
    expect(helper.guide.value.ready).toBe(true)
  })
  it('retries client listing errors and blocks refresh during a real test', async () => {
    api.getCapabilityCallGuideClients.mockRejectedValueOnce(new Error('网络异常'))
    start()
    await flushPromises()
    expect(helper.clientsError.value).toBe('网络异常')
    await helper.loadClients()
    helper.busy.value = true
    const previous = helper.guide.value
    await helper.loadGuide('other')
    expect(helper.guide.value).toBe(previous)
  })
  it('keeps grant upgrade permission and rejects a stale confirmation', async () => {
    start()
    await flushPromises()
    helper.guide.value = { grantId: 'g', currentVersion: '2' }
    helper.confirmUseCurrentVersion()
    expect(window.$dialog.warning).not.toHaveBeenCalled()
    props.canUpdateGrant = true
    helper.confirmUseCurrentVersion()
    const confirmation = window.$dialog.warning.mock.calls[0][0]
    await helper.loadGuide('new')
    expect(confirmation.onPositiveClick()).toBeUndefined()
    expect(api.useCurrentCapabilityGrantVersion).not.toHaveBeenCalled()
  })
})
