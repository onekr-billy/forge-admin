import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'
import { APPLICATION_PROCESS_SERVICE, useApplicationProcessRegistration } from '../components/useApplicationProcessRegistration'

const api = vi.hoisted(() => ({ getSystemServiceRegistrationSources: vi.fn() }))
vi.mock('@/api/ai/capability', () => api)
const response = (processes = [{ processCode: 'approval', name: '审批', version: 2, available: true }]) => ({ data: [{ serviceCode: APPLICATION_PROCESS_SERVICE, options: { processes } }] })
let store
let helper
beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  store = useCapabilityRegistrationStore()
  store.applicationId = '9007199254740999'
  helper = useApplicationProcessRegistration()
  api.getSystemServiceRegistrationSources.mockResolvedValue(response())
})
describe('published application process selection', () => {
  it('shows loading immediately and scopes the query with string IDs', async () => {
    let complete
    api.getSystemServiceRegistrationSources.mockImplementationOnce(() => new Promise(resolve => complete = resolve))
    const loading = helper.selectPage({ objectId: '9007199254741999' })
    expect(store.processSource.loading).toBe(true)
    expect(helper.disabled.value).toBe(true)
    expect(api.getSystemServiceRegistrationSources).toHaveBeenCalledWith({ serviceCode: APPLICATION_PROCESS_SERVICE, applicationId: '9007199254740999', objectId: '9007199254741999' })
    complete(response())
    await loading
    expect(helper.selected.value.processCode).toBe('approval')
    expect(helper.disabled.value).toBe(false)
  })
  it('requires explicit selection when multiple manual processes exist', async () => {
    api.getSystemServiceRegistrationSources.mockResolvedValue(response([{ processCode: 'a', available: true }, { processCode: 'b', available: true }]))
    await helper.selectPage({ objectId: '5' })
    expect(store.processSource.code).toBe(null)
    expect(helper.disabled.value).toBe(true)
    store.processSource.code = 'b'
    expect(helper.parameters().processCode).toBe('b')
  })
  it('shows an empty list instead of an obsolete main-flow error', async () => {
    api.getSystemServiceRegistrationSources.mockResolvedValue(response([]))
    await helper.selectPage({ objectId: '5' })
    expect(store.processSource.error).toBe('')
    expect(store.processSource.options).toEqual([])
    expect(helper.disabled.value).toBe(true)
  })
  it('allows retry after source loading fails', async () => {
    api.getSystemServiceRegistrationSources.mockRejectedValueOnce(new Error('加载失败'))
    await helper.selectPage({ objectId: '5' })
    expect(store.processSource.error).toBe('加载失败')
    await helper.load()
    expect(helper.disabled.value).toBe(false)
  })
  it('discards the previous page response and a response after closing', async () => {
    let complete
    api.getSystemServiceRegistrationSources.mockImplementationOnce(() => new Promise(resolve => complete = resolve))
    const old = helper.selectPage({ objectId: '5' })
    await helper.selectPage({ objectId: '6' })
    complete(response([{ processCode: 'old', available: true }]))
    await old
    expect(store.processSource.code).toBe('approval')
    api.getSystemServiceRegistrationSources.mockImplementationOnce(() => new Promise(resolve => complete = resolve))
    const closed = helper.selectPage({ objectId: '7' })
    helper.reset()
    complete(response())
    await closed
    expect(store.processSource.options).toEqual([])
    expect(store.processSource.loading).toBe(false)
  })
  it('never switches an upgraded capability to another source, including retry', async () => {
    await helper.restore({ applicationId: '11', objectId: '5', processCode: 'original' })
    expect(helper.disabled.value).toBe(true)
    expect(store.processSource.code).toBe(null)
    await helper.load()
    expect(helper.disabled.value).toBe(true)
    expect(store.processSource.code).toBe(null)
  })
})
