import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import * as api from '@/api/collaboration'
import ConnectionSetupPanel from '../components/ConnectionSetupPanel.vue'

vi.mock('@/api/collaboration', () => ({ getConnectionDetail: vi.fn(), createConnectionApp: vi.fn(), updateConnectionApp: vi.fn(), deleteConnectionApp: vi.fn(), bindConnectionCapability: vi.fn(), unbindConnectionCapability: vi.fn() }))
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ dict: ref({}), getLabel: (_type, value) => value }) }))
function pending() {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}
const detail = id => ({ code: 200, data: { connection: { id, connectionName: `连接 ${id}`, platform: 'WECHAT_ENTERPRISE', status: 1 }, apps: [], bindings: [] } })
function setup() {
  return shallowMount(ConnectionSetupPanel, {
    props: { show: true, connectionId: '1', canManage: true, manageMode: true },
    global: { stubs: Object.fromEntries(['NButton', 'NSpin', 'NAlert', 'NDescriptions', 'NDescriptionsItem', 'NDataTable', 'NEmpty', 'NSpace', 'NModal', 'NForm', 'NGrid', 'NFormItemGi', 'NInput', 'NSelect', 'NFormItem', 'DictTag'].map(name => [name, true])) },
  })
}
beforeEach(() => {
  vi.resetAllMocks()
  window.$message = { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
  api.getConnectionDetail.mockImplementation(async id => detail(id))
})

describe('connection configuration requests', () => {
  it('does not let a previous connection overwrite the current workspace', async () => {
    const old = pending()
    api.getConnectionDetail.mockImplementation(id => id === '1' ? old.promise : Promise.resolve(detail(id)))
    const wrapper = setup()
    await wrapper.setProps({ connectionId: '2' })
    await flushPromises()
    old.resolve(detail('1'))
    await flushPromises()
    expect(wrapper.vm.detail.connection.id).toBe('2')
    expect(wrapper.vm.detailLoading).toBe(false)
    wrapper.unmount()
  })
  it('ignores detail after closing and clears stale values on failure', async () => {
    const request = pending()
    api.getConnectionDetail.mockReturnValueOnce(request.promise)
    const wrapper = setup()
    await wrapper.setProps({ show: false })
    request.resolve(detail('1'))
    await flushPromises()
    expect(wrapper.vm.detail).toBeNull()
    api.getConnectionDetail.mockRejectedValueOnce(new Error('连接不存在'))
    await wrapper.setProps({ show: true })
    await flushPromises()
    expect(wrapper.vm.detail).toBeNull()
    expect(wrapper.vm.detailError).toBe('连接不存在')
    wrapper.unmount()
  })
  it('submits an application only once and reloads its saved details', async () => {
    const request = pending()
    api.createConnectionApp.mockReturnValue(request.promise)
    const wrapper = setup()
    await flushPromises()
    wrapper.vm.appForm = { appCode: 'demo', appName: '演示应用', clientId: 'corp' }
    const first = wrapper.vm.handleSubmitApp()
    const second = wrapper.vm.handleSubmitApp()
    await flushPromises()
    expect(api.createConnectionApp).toHaveBeenCalledTimes(1)
    expect(api.createConnectionApp).toHaveBeenCalledWith('1', expect.objectContaining({ appCode: 'demo' }))
    request.resolve({ code: 200 })
    await Promise.all([first, second])
    expect(wrapper.emitted('saved')).toHaveLength(1)
    expect(api.getConnectionDetail).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
  it('preserves the open form after a failed save', async () => {
    api.createConnectionApp.mockRejectedValue(new Error('保存失败'))
    const wrapper = setup()
    await flushPromises()
    wrapper.vm.appModalVisible = true
    wrapper.vm.appForm = { appCode: 'draft', appName: '尚未保存' }
    await wrapper.vm.handleSubmitApp()
    expect(wrapper.vm.appModalVisible).toBe(true)
    expect(wrapper.vm.appForm.appName).toBe('尚未保存')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.vm.appSubmitLoading).toBe(false)
    wrapper.unmount()
  })
})
