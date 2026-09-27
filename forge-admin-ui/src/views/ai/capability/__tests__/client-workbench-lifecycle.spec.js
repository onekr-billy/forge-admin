import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive, ref } from 'vue'
import * as api from '@/api/ai/capability'
import { useCapabilityClientWorkbench } from '../components/useCapabilityClientWorkbench'

vi.mock('@/api/ai/capability', () => ({ addCapabilityGrant: vi.fn(), getCapabilityGrantOptions: vi.fn(), getCapabilityGrantPage: vi.fn(), getCapabilityInvocationDetail: vi.fn(), getCapabilityInvocationPage: vi.fn(), revokeCapabilityGrant: vi.fn(), updateCapabilityGrant: vi.fn(), useCurrentCapabilityGrantVersion: vi.fn() }))
vi.mock('@/composables', () => ({ useDict: () => ({ dict: ref({}), reload: vi.fn() }) }))
vi.mock('@/utils', () => ({ formatDateTime: value => value }))
function pending() {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}
function setup() {
  const props = reactive({ show: true, client: { id: '9223372036854775801' }, canGrant: true, canGrantQuery: true })
  let workbench
  const wrapper = mount(defineComponent({
    setup() {
      workbench = useCapabilityClientWorkbench(props)
      return () => null
    },
  }))
  return { props, workbench, wrapper }
}
beforeEach(() => {
  vi.resetAllMocks()
  window.$message = { success: vi.fn(), error: vi.fn() }
  api.getCapabilityGrantOptions.mockResolvedValue({ data: { capabilities: [{ id: '7', sourceType: 'SYSTEM_SERVICE', currentVersion: '1.0.0' }] } })
  api.getCapabilityGrantPage.mockResolvedValue({ data: { records: [], total: 0 } })
})
describe('client workspace lifecycle', () => {
  it('serializes validation and grant submission', async () => {
    const { workbench, wrapper } = setup()
    await workbench.openGrantModal()
    workbench.grantForm.capabilityId = '7'
    const validation = pending()
    workbench.grantFormRef.value = { validate: () => validation.promise }
    api.addCapabilityGrant.mockResolvedValue({ code: 200 })
    const first = workbench.submitGrant()
    const duplicate = workbench.submitGrant()
    validation.resolve()
    await Promise.all([first, duplicate])
    expect(api.addCapabilityGrant).toHaveBeenCalledTimes(1)
    expect(api.addCapabilityGrant).toHaveBeenCalledWith(expect.objectContaining({ clientId: '9223372036854775801', capabilityId: '7' }))
    wrapper.unmount()
  })
  it('does not authorize a different client after asynchronous validation', async () => {
    const { props, workbench, wrapper } = setup()
    await workbench.openGrantModal()
    workbench.grantForm.capabilityId = '7'
    const validation = pending()
    workbench.grantFormRef.value = { validate: () => validation.promise }
    const request = workbench.submitGrant()
    props.client = { id: 'other' }
    await flushPromises()
    validation.resolve()
    await request
    expect(api.addCapabilityGrant).not.toHaveBeenCalled()
    expect(workbench.grantVisible.value).toBe(false)
    wrapper.unmount()
  })
  it('closing resets nested forms and rejects older list responses', async () => {
    const { props, workbench, wrapper } = setup()
    const response = pending()
    api.getCapabilityGrantPage.mockReturnValue(response.promise)
    const request = workbench.handleTabChange('grants')
    await flushPromises()
    expect(api.getCapabilityGrantPage).toHaveBeenCalledTimes(1)
    workbench.grantVisible.value = true
    props.show = false
    await flushPromises()
    response.resolve({ data: { records: [{ id: 'stale' }], total: 1 } })
    await request
    await flushPromises()
    expect(workbench.grantRows.value).toEqual([])
    expect(workbench.grantVisible.value).toBe(false)
    expect(workbench.grantLoading.value).toBe(false)
    wrapper.unmount()
  })
})
