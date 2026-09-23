import { useClipboard } from '@vueuse/core'
import { storeToRefs } from 'pinia'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  downloadCapabilityMarkdown,
  downloadCapabilityOpenApi,
  getCapabilityCallGuide,
  getCapabilityCallGuideClients,
  useCurrentCapabilityGrantVersion,
} from '@/api/ai/capability'
import { useCapabilityCallGuideStore } from '@/stores/capability/callGuideStore'

export function useCapabilityCallGuide(props, _emit) {
  const { copy } = useClipboard({ legacy: true })
  const router = useRouter()
  const store = useCapabilityCallGuideStore()
  const { clients, clientsLoading, clientsError, selectedClientId, guide, guideLoading, guideError, activeGuideTab, activeExample, exampleAuthMode, busy } = storeToRefs(store)
  let clientsGeneration = 0
  let guideGeneration = 0
  let grantConfirmation = null

  const markdownDownloading = ref(false)

  const openApiDownloading = ref(false)

  const versionSwitching = ref(false)

  const clientOptions = computed(() => clients.value.map((client) => {
    return {
      label: `${client.clientName}（${client.clientCode}）${client.status !== 'ENABLED' || isExpired(client.expiresAt) ? ' · 当前不可用' : ''}`,
      value: client.id,
    }
  }))

  const actorTypeLabel = computed(() => ({
    USER: '实际委托用户',
    SERVICE: '客户端服务账号',
    BOTH: '用户或服务账号',
  }[guide.value?.requiredActorType] || guide.value?.requiredActorType || '-'))

  const authModeLabel = computed(() => {
    const modes = guide.value?.availableAuthModes || []
    return modes.length ? modes.join(' / ') : '无匹配认证方式'
  })

  const grantVersionStrategyLabel = computed(() => {
    const fixedVersion = guide.value?.grantFixedVersion
      ? ` v${guide.value.grantFixedVersion}`
      : ''
    if (guide.value?.grantVersionStrategy === 'PINNED')
      return `固定版本${fixedVersion}`
    if (guide.value?.grantVersionStrategy === 'FOLLOW_MAJOR')
      return `跟随主版本（基准${fixedVersion || '未设置'}）`
    return guide.value?.grantVersionStrategy || '尚未授权'
  })

  const fieldColumns = [
    { title: '中文名称', key: 'fieldLabel', width: 150 },
    { title: '字段编码', key: 'fieldCode', width: 150 },
    { title: '类型', key: 'type', width: 105 },
    { title: '必填', key: 'required', width: 70, render: row => row.required ? '是' : '否' },
    { title: '字段含义与约束', key: 'description', minWidth: 260 },
    { title: '示例', key: 'example', width: 150, render: row => formatExample(row.example) },
  ]

  const exampleAuthOptions = computed(() => (guide.value?.availableAuthModes || []).map(mode => ({
    label: mode === 'OAUTH' ? 'OAuth 2.1' : 'AppId + HMAC',
    value: mode,
  })))

  const curlExample = computed(() => exampleAuthMode.value === 'HMAC'
    ? guide.value?.hmacExample || '当前客户端未启用 HMAC 认证'
    : guide.value?.oauthExample || '当前客户端未启用 OAuth 认证')

  const javaExample = computed(() => {
    if (exampleAuthMode.value === 'HMAC')
      return guide.value?.hmacJavaExample || '当前客户端未启用 HMAC 认证'
    if (guide.value?.userAssertionEnabled && guide.value?.userAssertionJavaExample)
      return guide.value.userAssertionJavaExample
    return guide.value?.oauthJavaExample || '当前客户端未启用 OAuth 认证'
  })

  const exampleCredentialNotice = computed(() => {
    if (exampleAuthMode.value === 'HMAC')
      return '需要 AppId 和 Signing Key；Signing Key 只在创建或轮换时展示一次。'
    if (guide.value?.requiredActorType === 'USER' && guide.value?.userAssertionEnabled)
      return '需要 Client Secret、外围用户标识和用户断言 RSA 私钥，再通过 Token Exchange 获取短期令牌。'
    if (guide.value?.requiredActorType === 'USER')
      return '需要 Client Secret 和受信 OIDC subject_token，通过 Token Exchange 获取短期令牌。'
    return '需要 Client ID / AppId 和 Client Secret，通过 client_credentials 获取短期令牌。'
  })

  const currentExample = computed(() => {
    return activeExample.value === 'JAVA' ? javaExample.value : curlExample.value
  })

  function reset() {
    grantConfirmation?.destroy()
    grantConfirmation = null
    clientsGeneration++
    guideGeneration++
    clients.value = []
    clientsLoading.value = false
    clientsError.value = ''
    guide.value = null
    guideLoading.value = false
    guideError.value = ''
    selectedClientId.value = null
    activeGuideTab.value = 'overview'
    activeExample.value = 'CURL'
    busy.value = false
  }

  watch(() => [props.show, props.capability?.id], ([visible, id]) => {
    reset()
    if (visible && id)
      loadClients()
  }, { immediate: true })
  onBeforeUnmount(reset)

  async function loadClients() {
    const generation = ++clientsGeneration
    clientsLoading.value = true
    clientsError.value = ''
    try {
      const res = await getCapabilityCallGuideClients()
      if (generation !== clientsGeneration || !props.show)
        return
      clients.value = res.data || []
      const available = clients.value.filter(client => client.status === 'ENABLED' && !isExpired(client.expiresAt))
      selectedClientId.value = available.length === 1 ? available[0].id : null
      if (selectedClientId.value)
        await loadGuide(selectedClientId.value)
    }
    catch (error) {
      if (generation !== clientsGeneration)
        return
      clients.value = []
      clientsError.value = error?.message || '客户端列表加载失败'
    }
    finally {
      if (generation === clientsGeneration)
        clientsLoading.value = false
    }
  }

  async function loadGuide(clientId) {
    if (busy.value)
      return
    const generation = ++guideGeneration
    guide.value = null
    guideError.value = ''
    guideLoading.value = false
    activeGuideTab.value = 'overview'
    if (!clientId || !props.capability?.id)
      return
    guideLoading.value = true
    try {
      const res = await getCapabilityCallGuide(props.capability.id, clientId)
      if (generation !== guideGeneration || !props.show)
        return
      if (!res.data)
        throw new Error('调用检查未返回内容，请重试')
      guide.value = res.data
      activeExample.value = 'CURL'
      exampleAuthMode.value = guide.value?.availableAuthModes?.[0] || 'OAUTH'
    }
    catch (error) {
      if (generation === guideGeneration)
        guideError.value = error?.message || '调用检查加载失败'
    }
    finally {
      if (generation === guideGeneration)
        guideLoading.value = false
    }
  }

  function confirmUseCurrentVersion() {
    if (!props.canUpdateGrant || !guide.value?.grantId || !guide.value?.currentVersion || versionSwitching.value || busy.value)
      return
    const current = guide.value
    grantConfirmation = window.$dialog.warning({
      title: '切换客户端授权版本',
      content: `确定把该客户端的授权基准切换到 v${guide.value.currentVersion} 吗？平台会保留原授权策略、允许操作和有效期，并重新校验新版契约。`,
      positiveText: '确认切换',
      negativeText: '取消',
      onPositiveClick: () => {
        if (guide.value !== current || !props.show)
          return
        return useCurrentVersion()
      },
    })
  }

  async function useCurrentVersion() {
    if (!props.canUpdateGrant || versionSwitching.value || busy.value || !guide.value?.grantId)
      return
    const current = guide.value
    versionSwitching.value = true
    try {
      const res = await useCurrentCapabilityGrantVersion(current.grantId)
      if (res.code !== 200 || guide.value !== current || !props.show)
        return
      window.$message.success(`客户端授权已切换到 v${guide.value.currentVersion}`)
      await loadGuide(selectedClientId.value)
    }
    catch (error) {
      window.$message.error(error?.message || '授权版本切换失败')
    }
    finally {
      versionSwitching.value = false
    }
  }

  function goClientWorkbench() {
    _emit('update:show', false)
    router.push({ path: '/open-platform/capability-client', query: selectedClientId.value ? { clientId: selectedClientId.value } : {} })
  }

  function isExpired(expiresAt) {
    if (!expiresAt)
      return false
    const timestamp = new Date(String(expiresAt).replace(' ', 'T')).getTime()
    return Number.isFinite(timestamp) && timestamp <= Date.now()
  }

  function checkIcon(status) {
    if (status === 'PASSED')
      return 'i-material-symbols:check-circle-rounded'
    if (status === 'FAILED')
      return 'i-material-symbols:error-rounded'
    return 'i-material-symbols:info-rounded'
  }

  function checkTagType(status) {
    if (status === 'PASSED')
      return 'success'
    if (status === 'FAILED')
      return 'error'
    return 'info'
  }

  function checkStatusLabel(status) {
    return {
      PASSED: '通过',
      FAILED: '阻断',
      RUNTIME: '运行时校验',
      INFO: '说明',
    }[status] || status
  }

  function formatExample(value) {
    if (value == null)
      return '-'
    return typeof value === 'string' ? value : JSON.stringify(value)
  }

  async function copyValue(value, successMessage) {
    if (!value)
      return
    try {
      await copy(String(value))
      window.$message.success(successMessage)
    }
    catch {
      window.$message.error('复制失败，请手动选择文本复制')
    }
  }

  async function downloadMarkdown() {
    if (!props.capability?.id)
      return
    markdownDownloading.value = true
    try {
      const response = await downloadCapabilityMarkdown(props.capability.id)
      saveBlob(response, `${fileStem()}.md`, 'text/markdown;charset=UTF-8')
      window.$message.success('Markdown 调用文档已下载')
    }
    catch (error) {
      window.$message.error(error?.message || 'Markdown 文档下载失败')
    }
    finally {
      markdownDownloading.value = false
    }
  }

  async function downloadOpenApi() {
    if (!props.capability?.id)
      return
    openApiDownloading.value = true
    try {
      const response = await downloadCapabilityOpenApi(props.capability.id)
      saveBlob(response, `${fileStem()}-openapi.json`, 'application/json')
      window.$message.success('OpenAPI 文档已下载')
    }
    catch (error) {
      window.$message.error(error?.message || 'OpenAPI 文档下载失败')
    }
    finally {
      openApiDownloading.value = false
    }
  }

  function fileStem() {
    return `${props.capability?.capabilityCode || 'capability'}-${props.capability?.currentVersion || 'latest'}`
  }

  function saveBlob(response, fallbackName, contentType) {
    const data = response?.data ?? response
    const blob = data instanceof Blob ? data : new Blob([data || ''], { type: contentType })
    const filename = responseFilename(response) || fallbackName
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  }

  function responseFilename(response) {
    const disposition = response?.headers?.['content-disposition']
      || response?.headers?.get?.('content-disposition')
    if (!disposition)
      return ''
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (utf8Match?.[1]) {
      try {
        return decodeURIComponent(utf8Match[1])
      }
      catch {
        return utf8Match[1]
      }
    }
    return disposition.match(/filename="?([^";]+)"?/i)?.[1] || ''
  }

  return {
    copy,
    clients,
    clientsLoading,
    clientsError,
    selectedClientId,
    guide,
    guideLoading,
    guideError,
    busy,
    goClientWorkbench,
    activeGuideTab,
    activeExample,
    exampleAuthMode,
    markdownDownloading,
    openApiDownloading,
    versionSwitching,
    clientOptions,
    actorTypeLabel,
    authModeLabel,
    grantVersionStrategyLabel,
    fieldColumns,
    exampleAuthOptions,
    curlExample,
    javaExample,
    exampleCredentialNotice,
    currentExample,
    loadClients,
    loadGuide,
    confirmUseCurrentVersion,
    useCurrentVersion,
    isExpired,
    checkIcon,
    checkTagType,
    checkStatusLabel,
    formatExample,
    copyValue,
    downloadMarkdown,
    downloadOpenApi,
    fileStem,
    saveBlob,
    responseFilename,
  }
}
