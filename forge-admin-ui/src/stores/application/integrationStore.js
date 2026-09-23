import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import * as api from '@/api/application-integration'
import { useUserStore } from '@/store'

/** Application scope shared by the four integration panels; never persisted to localStorage. */
export const useApplicationIntegrationStore = defineStore('application-integration', () => {
  const user = useUserStore()
  const application = ref(null)
  const tab = ref('collaboration')
  const config = ref({ connectionId: null, revision: 0 })
  const connectionId = ref(null)
  const connections = ref([])
  const capabilities = ref([])
  const keyword = ref('')
  const page = ref(1)
  const total = ref(0)
  const selected = ref(null)
  const clients = ref([])
  const grants = ref([])
  const grantPage = ref(1)
  const grantTotal = ref(0)
  const logs = ref([])
  const logPage = ref(1)
  const logTotal = ref(0)
  const requestId = ref('')
  const loading = reactive({})
  const errors = reactive({})
  const writing = ref(false)
  let epoch = 0
  let capabilitySyncDone = false
  const sequences = {}
  const has = permission => user.isAdmin || user.permissions?.includes('*:*:*') || user.permissions?.includes(permission)
  const canEdit = computed(() => has('ai:businessApplication:edit'))
  const published = computed(() => Number(application.value?.lastPublishVersion) > 0 && application.value?.status === 1)
  const registerTypes = computed(() => canEdit.value
    ? [
        ['FLOW_ACTION', 'flow-action'],
        ['BUSINESS_ACTION', 'business-action'],
        ['SYSTEM_SERVICE', 'system-service'],
      ].filter(([, permission]) => has(`ai:capability:${permission}:publish`)).map(([type]) => type)
    : [])

  function reset(app) {
    epoch++
    application.value = app
    config.value = { connectionId: null, revision: 0 }
    connectionId.value = null
    connections.value = []
    capabilities.value = []
    clients.value = []
    grants.value = []
    logs.value = []
    selected.value = null
    total.value = grantTotal.value = logTotal.value = 0
    page.value = grantPage.value = logPage.value = 1
    keyword.value = requestId.value = ''
    writing.value = false
    capabilitySyncDone = false
    Object.keys(errors).forEach(key => delete errors[key])
    Object.keys(loading).forEach(key => delete loading[key])
  }

  async function read(key, fetcher, accept) {
    if (!application.value?.id)
      return
    const generation = epoch
    const sequence = sequences[key] = (sequences[key] || 0) + 1
    loading[key] = true
    errors[key] = ''
    const current = () => generation === epoch && sequences[key] === sequence
    try {
      const result = await fetcher(application.value.id)
      if (current())
        accept(result)
    }
    catch (error) {
      if (current())
        errors[key] = error?.message || '加载失败，请重试'
    }
    finally {
      if (current())
        loading[key] = false
    }
  }

  async function loadCollaboration() {
    if (writing.value || !has('system:collaboration:connection:list'))
      return
    const selection = connectionId.value
    const dirty = String(selection || '') !== String(config.value.connectionId || '')
    return read('collaboration', id => Promise.all([api.getApplicationCollaboration(id), api.getApplicationConnections(id)]), ([saved, options]) => {
      config.value = saved.data
      // Refresh readiness without discarding edits made before or during the request.
      if (!dirty && connectionId.value === selection)
        connectionId.value = saved.data.connectionId == null ? null : String(saved.data.connectionId)
      connections.value = options.data || []
    })
  }
  async function syncCapabilityOwnership(force = false) {
    if (!canEdit.value || (capabilitySyncDone && !force) || !application.value?.id)
      return
    const generation = epoch
    loading.capabilitySync = true
    errors.capabilitySync = ''
    try {
      await api.syncApplicationCapabilities(application.value.id)
      if (generation === epoch)
        capabilitySyncDone = true
    }
    catch (error) {
      if (generation === epoch)
        errors.capabilitySync = error?.message || '能力归属同步失败，请重试'
    }
    finally {
      if (generation === epoch)
        loading.capabilitySync = false
    }
  }
  async function loadCapabilities(forceSync = false) {
    if (!has('ai:capability:query'))
      return
    await syncCapabilityOwnership(forceSync)
    return read('capabilities', id => api.getApplicationCapabilities(id, { pageNum: page.value, pageSize: 12, keyword: keyword.value.trim() }), (res) => {
      capabilities.value = res.data?.records || []
      total.value = Number(res.data?.total || 0)
      // Do not silently widen a missing selection into an unfiltered grant/log request.
      if (selected.value && capabilities.value.some(item => String(item.id) === String(selected.value.id)))
        selected.value = capabilities.value.find(item => String(item.id) === String(selected.value.id))
    })
  }
  function selectCapability(capability, target) {
    sequences.grants = (sequences.grants || 0) + 1
    sequences.logs = (sequences.logs || 0) + 1
    loading.grants = loading.logs = false
    grants.value = logs.value = []
    grantTotal.value = logTotal.value = 0
    grantPage.value = logPage.value = 1
    selected.value = capability
    if (target)
      tab.value = target
  }
  async function loadGrants() {
    if (!selected.value || !has('ai:capability:grant:query'))
      return
    const capabilityId = selected.value.id
    return read('grants', id => Promise.all([
      api.getApplicationGrants(id, { capabilityId, pageNum: grantPage.value, pageSize: 10 }),
      api.getApplicationClients(id),
    ]), ([result, options]) => {
      grants.value = result.data?.records || []
      grantTotal.value = Number(result.data?.total || 0)
      clients.value = options.data || []
    })
  }
  async function loadLogs() {
    if (!selected.value || !has('ai:capability:invocation:query'))
      return
    const capabilityId = selected.value.id
    return read('logs', id => api.getApplicationInvocations(id, { capabilityId, requestId: requestId.value.trim() || undefined, pageNum: logPage.value, pageSize: 10 }), (res) => {
      logs.value = res.data?.records || []
      logTotal.value = Number(res.data?.total || 0)
    })
  }
  async function mutate(key, operation, accepted) {
    if (writing.value || !application.value?.id)
      return false
    const generation = epoch
    writing.value = true
    // A read started before this write must never restore an older server revision.
    sequences[key] = (sequences[key] || 0) + 1
    loading[key] = false
    errors[key] = ''
    try {
      const result = await operation(application.value.id)
      if (generation !== epoch)
        return false
      await accepted?.(result)
      return true
    }
    catch (error) {
      if (generation === epoch)
        errors[key] = error?.message || '操作失败，请重试'
      return false
    }
    finally {
      if (generation === epoch)
        writing.value = false
    }
  }
  const save = () => mutate('collaboration', id => api.saveApplicationCollaboration(id, { connectionId: connectionId.value, revision: config.value.revision }), (res) => {
    config.value = res.data
  })
  const grant = data => mutate('grants', id => api.grantApplicationCapability(id, { ...data, capabilityId: selected.value?.id }), loadGrants)
  const revoke = grantId => mutate('grants', id => api.revokeApplicationGrant(id, grantId), loadGrants)

  return { application, tab, config, connectionId, connections, capabilities, keyword, page, total, selected, clients, grants, grantPage, grantTotal, logs, logPage, logTotal, requestId, loading, errors, writing, has, canEdit, published, registerTypes, reset, loadCollaboration, loadCapabilities, selectCapability, loadGrants, loadLogs, save, grant, revoke }
})
