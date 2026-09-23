import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 调用工作台的短期状态；不持久化凭据或业务输入。 */
export const useCapabilityCallGuideStore = defineStore('capability-call-guide', () => {
  const clients = ref([])
  const clientsLoading = ref(false)
  const clientsError = ref('')
  const selectedClientId = ref(null)
  const guide = ref(null)
  const guideLoading = ref(false)
  const guideError = ref('')
  const activeGuideTab = ref('overview')
  const activeExample = ref('CURL')
  const exampleAuthMode = ref('OAUTH')
  const busy = ref(false)
  return { clients, clientsLoading, clientsError, selectedClientId, guide, guideLoading, guideError, activeGuideTab, activeExample, exampleAuthMode, busy }
})
