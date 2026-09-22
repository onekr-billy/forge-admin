import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useCapabilityOnlineTestStore = defineStore('capability-online-test', () => {
  const guide = ref(null)
  const authMode = ref(null)
  const credential = ref('')
  const subjectTokenMode = ref('OIDC')
  const subjectToken = ref('')
  const userAssertionSubject = ref('')
  const userAssertionPhone = ref('')
  const userAssertionOrgId = ref('')
  const userAssertionPrivateKey = ref('')
  const requestBody = ref('{}')
  const testing = ref(false)
  const confirming = ref(false)
  const testReport = ref(null)
  const credentialAutoFilled = ref(false)
  const stage = ref('identity')
  const inputError = ref('')
  const requestMode = ref('fields')
  function clearSecrets() {
    credential.value = ''
    subjectToken.value = ''
    userAssertionSubject.value = ''
    userAssertionPhone.value = ''
    userAssertionOrgId.value = ''
    userAssertionPrivateKey.value = ''
    credentialAutoFilled.value = false
  }
  return { guide, authMode, credential, subjectTokenMode, subjectToken, userAssertionSubject, userAssertionPhone, userAssertionOrgId, userAssertionPrivateKey, requestBody, testing, confirming, testReport, credentialAutoFilled, stage, inputError, requestMode, clearSecrets }
})
