import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'

/** 可从开放平台或应用内入口复用；不持久化凭据与业务输入。 */
export const useCapabilityRegistrationStore = defineStore('capability-registration', () => {
  const step = ref(1)
  const scenario = ref('flow')
  const applicationId = ref(null)
  const pageId = ref(null)
  const form = reactive({})
  const sourceContext = reactive({ applicationCode: '', pageId: '', objectId: null })

  function initialize(context = {}) {
    step.value = 1
    scenario.value = context.scenario || 'flow'
    applicationId.value = null
    pageId.value = null
    Object.assign(sourceContext, { applicationCode: '', pageId: '', objectId: null }, context)
  }

  return { step, scenario, applicationId, pageId, form, sourceContext, initialize }
})
