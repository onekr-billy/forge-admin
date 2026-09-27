import { computed } from 'vue'
import { getSystemServiceRegistrationSources } from '@/api/ai/capability'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'

export const APPLICATION_PROCESS_SERVICE = 'lowcode.business-process.start'

export function useApplicationProcessRegistration() {
  const store = useCapabilityRegistrationStore()
  const state = store.processSource
  let generation = 0
  let lockedCode = null
  const selected = computed(() => state.options.find(item => item.processCode === state.code))
  const disabled = computed(() => state.loading || !!state.error || !selected.value?.available)

  function reset() {
    generation++
    lockedCode = null
    Object.assign(state, { applicationId: null, objectId: null, code: null, options: [], loading: false, error: '' })
  }

  async function load(preferredCode = lockedCode || state.code, exact = !!lockedCode) {
    const request = ++generation
    state.options = []
    state.code = null
    state.error = ''
    state.loading = false
    if (!state.applicationId || !state.objectId)
      return
    state.loading = true
    try {
      const response = await getSystemServiceRegistrationSources({
        serviceCode: APPLICATION_PROCESS_SERVICE,
        applicationId: state.applicationId,
        objectId: state.objectId,
      })
      if (request !== generation)
        return
      const source = response.data?.find(item => item.serviceCode === APPLICATION_PROCESS_SERVICE)
      if (!source)
        throw new Error('当前后端尚未提供应用业务流程能力，请更新并重启 Admin')
      state.options = source.options?.processes || []
      const available = state.options.filter(item => item.available)
      if (available.some(item => item.processCode === preferredCode))
        state.code = preferredCode
      else if (exact)
        state.error = '原业务流程已停用或不在当前应用发布版本中，不能自动更换来源'
      else if (available.length === 1)
        state.code = available[0].processCode
    }
    catch (cause) {
      if (request === generation)
        state.error = cause.message || '加载应用业务流程失败'
    }
    finally {
      if (request === generation)
        state.loading = false
    }
  }

  async function selectPage(page) {
    reset()
    if (!page)
      return
    state.applicationId = String(store.applicationId)
    state.objectId = String(page.objectId)
    await load()
  }

  async function restore(parameters) {
    reset()
    if (!parameters?.applicationId || !parameters?.objectId || !parameters?.processCode) {
      state.error = '原业务流程来源信息不完整'
      return
    }
    state.applicationId = String(parameters.applicationId)
    state.objectId = String(parameters.objectId)
    lockedCode = parameters.processCode
    await load(parameters.processCode, true)
  }

  return { selected, disabled, reset, load, selectPage, restore, parameters: () => ({
    applicationId: state.applicationId,
    objectId: state.objectId,
    processCode: state.code,
  }) }
}
