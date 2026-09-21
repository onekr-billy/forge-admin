import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '@/api/application-integration'
import { useApplicationIntegrationStore } from '../integrationStore'

const { user } = vi.hoisted(() => ({ user: { isAdmin: true, permissions: [] } }))
vi.mock('@/store', () => ({ useUserStore: () => user }))
vi.mock('@/api/application-integration', () => ({
  getApplicationCollaboration: vi.fn(),
  getApplicationConnections: vi.fn(),
  saveApplicationCollaboration: vi.fn(),
  getApplicationCapabilities: vi.fn(),
  getApplicationClients: vi.fn(),
  getApplicationGrants: vi.fn(),
  grantApplicationCapability: vi.fn(),
  revokeApplicationGrant: vi.fn(),
  getApplicationInvocations: vi.fn(),
}))
const app = { id: '9007199254740993', applicationCode: 'legal', lastPublishVersion: 1, status: 1 }
function deferred() {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}
beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  user.isAdmin = true
  user.permissions = []
})

describe('application integration scope', () => {
  it('never queries unscoped grants or logs', async () => {
    const store = useApplicationIntegrationStore()
    store.reset(app)
    await store.loadGrants()
    await store.loadLogs()
    expect(api.getApplicationGrants).not.toHaveBeenCalled()
    expect(api.getApplicationInvocations).not.toHaveBeenCalled()
  })
  it('passes long IDs without numeric coercion and paginates server-side', async () => {
    api.getApplicationCapabilities.mockResolvedValue({ data: { records: [], total: 0 } })
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.page = 3
    await store.loadCapabilities()
    expect(api.getApplicationCapabilities).toHaveBeenCalledWith(app.id, { pageNum: 3, pageSize: 12, keyword: '' })
  })
  it('does not load privileged surfaces without matching platform permissions', async () => {
    user.isAdmin = false
    user.permissions = ['ai:businessApplication:edit']
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.selectCapability({ id: '4' })
    await store.loadCollaboration()
    await store.loadCapabilities()
    await store.loadGrants()
    await store.loadLogs()
    expect(api.getApplicationConnections).not.toHaveBeenCalled()
    expect(api.getApplicationCapabilities).not.toHaveBeenCalled()
    expect(api.getApplicationGrants).not.toHaveBeenCalled()
    expect(store.registerTypes).toEqual([])
  })
  it('ignores late configuration from the previous application', async () => {
    const pending = deferred()
    api.getApplicationCollaboration.mockReturnValue(pending.promise)
    api.getApplicationConnections.mockResolvedValue({ data: [{ id: '1' }] })
    const store = useApplicationIntegrationStore()
    store.reset(app)
    const request = store.loadCollaboration()
    store.reset({ ...app, id: '2' })
    pending.resolve({ data: { connectionId: '1', revision: 5 } })
    await request
    expect(store.config.connectionId).toBeNull()
    expect(store.connections).toEqual([])
  })
  it('ignores late grants after selecting another capability', async () => {
    const pending = deferred()
    api.getApplicationGrants.mockReturnValue(pending.promise)
    api.getApplicationClients.mockResolvedValue({ data: [] })
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.selectCapability({ id: '4' })
    const request = store.loadGrants()
    store.selectCapability({ id: '5' })
    pending.resolve({ data: { records: [{ id: 'stale' }], total: 1 } })
    await request
    expect(store.grants).toEqual([])
    expect(store.grantTotal).toBe(0)
  })
  it('keeps the current page when refreshing grants and never widens capability scope', async () => {
    api.getApplicationGrants.mockResolvedValue({ data: { records: [], total: 30 } })
    api.getApplicationClients.mockResolvedValue({ data: [] })
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.selectCapability({ id: '4' })
    store.grantPage = 2
    await store.loadGrants()
    expect(api.getApplicationGrants).toHaveBeenCalledWith(app.id, { capabilityId: '4', pageNum: 2, pageSize: 10 })
  })
  it('saves once while pending and uses optimistic revision, no secrets', async () => {
    const pending = deferred()
    api.saveApplicationCollaboration.mockReturnValue(pending.promise)
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.connectionId = '4'
    store.config.revision = 7
    const request = store.save()
    expect(await store.save()).toBe(false)
    expect(api.saveApplicationCollaboration).toHaveBeenCalledTimes(1)
    expect(api.saveApplicationCollaboration).toHaveBeenCalledWith(app.id, { connectionId: '4', revision: 7 })
    pending.resolve({ data: { connectionId: '4', revision: 8 } })
    expect(await request).toBe(true)
    expect(store.config.revision).toBe(8)
  })
  it('reports conflict without clearing the users selection', async () => {
    api.saveApplicationCollaboration.mockRejectedValue(new Error('配置已被其他人修改'))
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.connectionId = '4'
    expect(await store.save()).toBe(false)
    expect(store.connectionId).toBe('4')
    expect(store.errors.collaboration).toContain('其他人修改')
  })
  it('does not allow an older refresh to overwrite a successful save', async () => {
    const pending = deferred()
    api.getApplicationCollaboration.mockReturnValue(pending.promise)
    api.getApplicationConnections.mockResolvedValue({ data: [] })
    api.saveApplicationCollaboration.mockResolvedValue({ data: { connectionId: '4', revision: 8 } })
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.config = { connectionId: '3', revision: 7 }
    store.connectionId = '3'
    const refresh = store.loadCollaboration()
    store.connectionId = '4'
    expect(await store.save()).toBe(true)
    pending.resolve({ data: { connectionId: '3', revision: 7 } })
    await refresh
    expect(store.config).toEqual({ connectionId: '4', revision: 8 })
    expect(store.connectionId).toBe('4')
    expect(store.loading.collaboration).toBe(false)
  })
  it('does not start a collaboration refresh during saving', async () => {
    const pending = deferred()
    api.saveApplicationCollaboration.mockReturnValue(pending.promise)
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.connectionId = '4'
    const save = store.save()
    await store.loadCollaboration()
    expect(api.getApplicationCollaboration).not.toHaveBeenCalled()
    pending.resolve({ data: { connectionId: '4', revision: 1 } })
    await save
  })
  it.each([true, false])('preserves an unsaved selection changed before refresh: %s', async (changedBefore) => {
    const pending = deferred()
    api.getApplicationCollaboration.mockReturnValue(pending.promise)
    api.getApplicationConnections.mockResolvedValue({ data: [{ id: '4' }] })
    const store = useApplicationIntegrationStore()
    store.reset(app)
    store.config = { connectionId: '3', revision: 7 }
    store.connectionId = changedBefore ? '4' : '3'
    const refresh = store.loadCollaboration()
    store.connectionId = '4'
    pending.resolve({ data: { connectionId: '3', revision: 7 } })
    await refresh
    expect(store.connectionId).toBe('4')
    expect(store.config.connectionId).toBe('3')
    expect(store.connections).toEqual([{ id: '4' }])
  })
})
