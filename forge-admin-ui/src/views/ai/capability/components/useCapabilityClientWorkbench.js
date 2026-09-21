import { NButton, NTag } from 'naive-ui'
import { computed, h, reactive, ref, watch } from 'vue'
import {
  addCapabilityGrant,
  getCapabilityGrantOptions,
  getCapabilityGrantPage,
  getCapabilityInvocationDetail,
  getCapabilityInvocationPage,
  revokeCapabilityGrant,
  updateCapabilityGrant,
  useCurrentCapabilityGrantVersion,
} from '@/api/ai/capability'
import { useDict } from '@/composables'
import { formatDateTime } from '@/utils'

export function useCapabilityClientWorkbench(props, _emit) {
  const { dict, reload: reloadWorkbenchDicts } = useDict(
    'ai_capability_client_actor_mode',
    'ai_capability_client_status',
    'ai_capability_auth_mode',
    'ai_capability_user_mapping_mode',
    'ai_capability_version_strategy',
    'ai_capability_grant_status',
    'ai_capability_flow_operation',
    'ai_capability_actor_type',
  )

  const actorModeOptions = computed(() => dict.value.ai_capability_client_actor_mode || [])

  const clientStatusOptions = computed(() => dict.value.ai_capability_client_status || [])

  const mappingModeOptions = computed(() => dict.value.ai_capability_user_mapping_mode || [])

  const versionStrategyOptions = computed(() => dict.value.ai_capability_version_strategy || [])

  const grantStatusOptions = computed(() => dict.value.ai_capability_grant_status || [])

  const actorTypeOptions = computed(() => dict.value.ai_capability_actor_type || [])

  const flowOperationOptions = computed(() => dict.value.ai_capability_flow_operation || [])

  const activeTab = ref('overview')

  const authModes = computed(() => String(props.client?.authModes || '').split(',').filter(Boolean))

  const signatureEnabled = computed(() => authModes.value.includes('SIGNATURE'))

  const userDelegationEnabled = computed(() => Number(props.client?.oauthEnabled) === 1
    && ['USER_DELEGATION', 'HYBRID'].includes(props.client?.actorMode))

  const authModeText = computed(() => authModes.value
    .map(mode => dictLabel(dict.value.ai_capability_auth_mode || [], mode))
    .join(' / ') || '-')

  const mappingModeLabel = computed(() => dictLabel(
    mappingModeOptions.value,
    props.client?.userAssertionMappingMode || 'PREBOUND',
  ))

  function handleTabChange(tab) {
    if (tab === 'grants')
      loadGrants()
    if (tab === 'logs')
      loadLogs()
  }

  function dictLabel(options, value) {
    return options.find(item => String(item.value) === String(value))?.label || value || '-'
  }

  const grantRows = ref([])

  const grantLoading = ref(false)

  const grantOptions = ref({ clients: [], capabilities: [] })

  const grantPagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
  })

  const capabilityMap = computed(() => new Map((grantOptions.value.capabilities || [])
    .map(item => [String(item.id), item])))

  const grantColumns = computed(() => [
    {
      title: '能力',
      key: 'capabilityId',
      minWidth: 220,
      render: row => capabilityName(row.capabilityId),
    },
    { title: '版本策略', key: 'versionStrategy', width: 120, render: row => dictLabel(versionStrategyOptions.value, row.versionStrategy) },
    { title: '基准版本', key: 'fixedVersion', width: 100, render: row => row.fixedVersion || '-' },
    { title: '状态', key: 'status', width: 90, render: row => dictLabel(grantStatusOptions.value, row.status) },
    { title: '过期时间', key: 'expiresAt', width: 160, render: row => row.expiresAt || '长期有效' },
    {
      title: '操作',
      key: 'action',
      width: 230,
      fixed: 'right',
      render: row => h('div', { class: 'table-actions' }, [
        row.status === 'ENABLED' && grantVersionUpgradeAvailable(row)
          ? h(NButton, { text: true, type: 'primary', onClick: () => switchGrantVersion(row) }, { default: () => '使用当前版本' })
          : null,
        row.status === 'ENABLED' && props.canGrant
          ? h(NButton, { text: true, type: 'primary', onClick: () => openGrantModal(row) }, { default: () => '调整' })
          : null,
        row.status === 'ENABLED' && props.canGrantRevoke
          ? h(NButton, { text: true, type: 'error', onClick: () => revokeGrant(row) }, { default: () => '撤销' })
          : null,
      ]),
    },
  ])

  async function ensureGrantOptions() {
    const res = await getCapabilityGrantOptions()
    grantOptions.value = res.data || { clients: [], capabilities: [] }
  }

  async function loadGrants() {
    if (!props.client?.id)
      return
    grantLoading.value = true
    try {
      await ensureGrantOptions()
      const res = await getCapabilityGrantPage({
        pageNum: grantPagination.page,
        pageSize: grantPagination.pageSize,
        clientId: props.client.id,
      })
      grantRows.value = res.data?.records || []
      grantPagination.itemCount = Number(res.data?.total || 0)
    }
    catch (error) {
      window.$message.error(error?.message || '客户端授权加载失败')
    }
    finally {
      grantLoading.value = false
    }
  }

  function changeGrantPage(page) {
    grantPagination.page = page
    loadGrants()
  }

  function changeGrantPageSize(pageSize) {
    grantPagination.pageSize = pageSize
    grantPagination.page = 1
    loadGrants()
  }

  function capabilityName(capabilityId) {
    const capability = capabilityMap.value.get(String(capabilityId))
    return capability ? `${capability.capabilityName}（${capability.capabilityCode}）` : `能力 #${capabilityId}`
  }

  function grantVersionUpgradeAvailable(row) {
    const capability = capabilityMap.value.get(String(row.capabilityId))
    return capability?.currentVersion
      && String(capability.currentVersion) !== String(row.fixedVersion || '')
  }

  function revokeGrant(row) {
    window.$dialog.warning({
      title: '撤销能力授权',
      content: `撤销后当前客户端将不能再调用「${capabilityName(row.capabilityId)}」。是否继续？`,
      positiveText: '确认撤销',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res = await revokeCapabilityGrant(row.id)
        if (res.code === 200) {
          window.$message.success('授权已撤销')
          await loadGrants()
        }
      },
    })
  }

  async function switchGrantVersion(row) {
    const capability = capabilityMap.value.get(String(row.capabilityId))
    if (!capability?.currentVersion)
      return
    const res = await useCurrentCapabilityGrantVersion(row.id)
    if (res.code === 200) {
      window.$message.success(`授权基准已切换到 v${capability.currentVersion}`)
      await loadGrants()
    }
  }

  const grantVisible = ref(false)

  const editingGrantId = ref(null)

  const grantSubmitting = ref(false)

  const grantOptionLoading = ref(false)

  const grantFormRef = ref(null)

  const grantForm = reactive({
    capabilityId: null,
    versionStrategy: null,
    fixedVersion: '',
    allowedFields: [],
    allowedOperations: [],
    expiresAt: null,
  })

  const selectedCapability = computed(() => (grantOptions.value.capabilities || [])
    .find(item => item.id === grantForm.capabilityId))

  const capabilityOptions = computed(() => (grantOptions.value.capabilities || [])
    .filter(item => item.publishStatus === 'PUBLISHED' && item.enabled !== 0)
    .map((item) => {
      const unavailableReason = capabilityUnavailableReason(item)
      return {
        label: `${item.capabilityName}（${item.capabilityCode}）· v${item.currentVersion || '-'}${unavailableReason ? ` · ${unavailableReason}` : ''}`,
        value: item.id,
        disabled: !!unavailableReason,
      }
    }))

  const grantFieldOptions = computed(() => {
    const metaMap = new Map((selectedCapability.value?.fields || []).map(item => [item.fieldCode, item]))
    return (selectedCapability.value?.allowedFields || []).map((fieldCode) => {
      const meta = metaMap.get(fieldCode)
      const required = meta?.required || selectedCapability.value?.requiredFields?.includes(fieldCode)
      return {
        label: `${meta?.fieldLabel || '未命名字段'}${required ? '（必填）' : ''}`,
        value: fieldCode,
        disabled: required,
      }
    })
  })

  const grantOperationOptions = computed(() => (selectedCapability.value?.allowedOperations || [])
    .map(operation => ({ label: dictLabel(flowOperationOptions.value, operation), value: operation })))

  const grantRules = {
    capabilityId: {
      trigger: 'change',
      validator: (_rule, value) => value != null && String(value).trim()
        ? true
        : new Error('请选择能力'),
    },
    versionStrategy: { required: true, message: '请选择版本策略', trigger: 'change' },
    fixedVersion: { required: true, message: '请输入基准版本', trigger: 'blur' },
  }

  async function openGrantModal(row = null) {
    grantVisible.value = true
    editingGrantId.value = row?.id || null
    grantOptionLoading.value = true
    try {
      await Promise.all([ensureGrantOptions(), reloadWorkbenchDicts()])
      if (row) {
        const fieldPolicy = parseFieldPolicy(row.fieldPolicy)
        const capability = (grantOptions.value.capabilities || [])
          .find(item => item.id === row.capabilityId)
        Object.assign(grantForm, {
          capabilityId: row.capabilityId,
          versionStrategy: row.versionStrategy,
          fixedVersion: row.fixedVersion || '',
          allowedFields: [...new Set([
            ...(Array.isArray(fieldPolicy.allowedFields) ? fieldPolicy.allowedFields : []),
            ...(capability?.requiredFields || []),
          ])],
          allowedOperations: Array.isArray(fieldPolicy.allowedOperations) ? [...fieldPolicy.allowedOperations] : [],
          expiresAt: parseDateTimeValue(row.expiresAt),
        })
      }
      else {
        Object.assign(grantForm, {
          capabilityId: null,
          versionStrategy: versionStrategyOptions.value.find(item => item.isDefault === 'Y')?.value
            || versionStrategyOptions.value[0]?.value
            || null,
          fixedVersion: '',
          allowedFields: [],
          allowedOperations: [],
          expiresAt: null,
        })
      }
    }
    catch (error) {
      grantVisible.value = false
      window.$message.error(error?.message || '授权候选能力加载失败')
    }
    finally {
      grantOptionLoading.value = false
    }
  }

  function capabilityUnavailableReason(capability) {
    if (capability.riskLevel === 'HIGH')
      return '高风险能力暂不可授权'
    if (capability.behavior === 'READ_ONLY' || capability.sourceType === 'SYSTEM_SERVICE')
      return ''
    if (capability.sourceType === 'BUSINESS_ACTION')
      return capability.allowedFields?.length ? '' : '缺少允许字段'
    if (capability.sourceType === 'FLOW_ACTION') {
      return capability.allowedOperations?.length
        && (!capability.allowedOperations.includes('SUBMIT') || capability.allowedFields?.length)
        ? ''
        : '缺少允许操作或申请字段'
    }
    return '当前类型不可授权'
  }

  function handleCapabilityChange(capabilityId) {
    const capability = (grantOptions.value.capabilities || []).find(item => item.id === capabilityId)
    grantForm.fixedVersion = capability?.currentVersion || ''
    grantForm.allowedFields = [...(capability?.allowedFields || [])]
    grantForm.allowedOperations = [...(capability?.allowedOperations || [])]
  }

  function parseFieldPolicy(value) {
    if (value && typeof value === 'object')
      return value
    if (typeof value !== 'string' || !value.trim())
      return {}
    try {
      const parsed = JSON.parse(value)
      return parsed && typeof parsed === 'object' ? parsed : {}
    }
    catch {
      return {}
    }
  }

  function parseDateTimeValue(value) {
    if (!value)
      return null
    const timestamp = new Date(String(value).replace(' ', 'T')).getTime()
    return Number.isFinite(timestamp) ? timestamp : null
  }

  async function submitGrant() {
    try {
      await grantFormRef.value?.validate()
    }
    catch {
      return
    }
    const capability = selectedCapability.value
    if (!capability)
      return
    if ((capability.allowedFields || []).length && grantForm.allowedFields.length === 0) {
      window.$message.error('请至少保留一个允许字段')
      return
    }
    if ((capability.allowedOperations || []).length && grantForm.allowedOperations.length === 0) {
      window.$message.error('请至少保留一个允许操作')
      return
    }
    grantSubmitting.value = true
    try {
      const fieldPolicy = capability.sourceType === 'BUSINESS_ACTION'
        ? { allowedFields: grantForm.allowedFields }
        : capability.sourceType === 'FLOW_ACTION'
          ? {
              allowedOperations: grantForm.allowedOperations,
              ...((capability.allowedFields || []).length ? { allowedFields: grantForm.allowedFields } : {}),
            }
          : null
      const payload = {
        versionStrategy: grantForm.versionStrategy,
        fixedVersion: grantForm.fixedVersion,
        fieldPolicy,
        expiresAt: grantForm.expiresAt ? formatDateTime(grantForm.expiresAt) : null,
      }
      const res = editingGrantId.value
        ? await updateCapabilityGrant(editingGrantId.value, payload)
        : await addCapabilityGrant({
            clientId: props.client.id,
            capabilityId: grantForm.capabilityId,
            ...payload,
          })
      if (res.code === 200) {
        window.$message.success(editingGrantId.value ? '授权已调整' : '授权成功')
        grantVisible.value = false
        editingGrantId.value = null
        grantPagination.page = 1
        await loadGrants()
      }
    }
    finally {
      grantSubmitting.value = false
    }
  }

  const logRows = ref([])

  const logLoading = ref(false)

  const logFilters = reactive({
    requestId: '',
    capabilityKeyword: '',
    actorKeyword: '',
  })

  const logPagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
  })

  const logColumns = [
    { title: '请求 ID', key: 'requestId', width: 210, fixed: 'left', ellipsis: { tooltip: true } },
    { title: '能力', key: 'capabilityCode', width: 250, ellipsis: { tooltip: true }, render: capabilityDisplay },
    { title: '调用用户', key: 'actorUserId', width: 190, ellipsis: { tooltip: true }, render: userLabel },
    {
      title: '结果',
      key: 'resultStatus',
      width: 95,
      render: row => h(NTag, { size: 'small', type: row.resultStatus === 'SUCCESS' ? 'success' : 'error' }, { default: () => resultStatusLabel(row.resultStatus) }),
    },
    { title: '失败阶段', key: 'failureStage', width: 150, render: row => failureStageLabel(row.failureStage, row.resultStatus) },
    { title: '错误摘要', key: 'errorMessage', width: 250, ellipsis: { tooltip: true }, render: row => row.errorMessage || row.resultCode || '-' },
    { title: '耗时', key: 'durationMs', width: 90, render: row => row.durationMs == null ? '-' : `${row.durationMs} ms` },
    { title: '调用时间', key: 'createTime', width: 170, fixed: 'right', render: row => formatInvocationTime(row.createTime) },
    {
      title: '操作',
      key: 'action',
      width: 70,
      fixed: 'right',
      render: row => h(NButton, { text: true, type: 'primary', onClick: () => openLogDetail(row) }, { default: () => '详情' }),
    },
  ]

  async function loadLogs() {
    if (!props.client?.id)
      return
    logLoading.value = true
    try {
      const res = await getCapabilityInvocationPage({
        pageNum: logPagination.page,
        pageSize: logPagination.pageSize,
        clientId: props.client.id,
        requestId: normalizeFilter(logFilters.requestId),
        capabilityKeyword: normalizeFilter(logFilters.capabilityKeyword),
        actorKeyword: normalizeFilter(logFilters.actorKeyword),
      })
      logRows.value = res.data?.records || []
      logPagination.itemCount = Number(res.data?.total || 0)
    }
    catch (error) {
      window.$message.error(error?.message || '客户端调用日志加载失败')
    }
    finally {
      logLoading.value = false
    }
  }

  function searchLogs() {
    logPagination.page = 1
    loadLogs()
  }

  function resetLogFilters() {
    Object.assign(logFilters, { requestId: '', capabilityKeyword: '', actorKeyword: '' })
    searchLogs()
  }

  function changeLogPage(page) {
    logPagination.page = page
    loadLogs()
  }

  function changeLogPageSize(pageSize) {
    logPagination.pageSize = pageSize
    logPagination.page = 1
    loadLogs()
  }

  const logDetailVisible = ref(false)

  const logDetailLoading = ref(false)

  const logDetail = ref(null)

  async function openLogDetail(row) {
    logDetailVisible.value = true
    logDetailLoading.value = true
    logDetail.value = null
    try {
      const res = await getCapabilityInvocationDetail(row.id)
      logDetail.value = res.data || null
    }
    catch (error) {
      window.$message.error(error?.message || '调用日志详情加载失败')
      logDetailVisible.value = false
    }
    finally {
      logDetailLoading.value = false
    }
  }

  function userLabel(row) {
    if (!row?.actorUserId)
      return '-'
    const name = row.actorRealName || row.actorUsername || `用户 #${row.actorUserId}`
    return `${name}${row.actorUsername && row.actorRealName ? `（${row.actorUsername}）` : ''} · ID ${row.actorUserId}`
  }

  function capabilityDisplay(row) {
    if (!row)
      return '-'
    if (row.capabilityName)
      return `${row.capabilityName}（${row.capabilityCode || '-'}）`
    return row.capabilityCode || '-'
  }

  function normalizeFilter(value) {
    const normalized = String(value || '').trim()
    return normalized || undefined
  }

  function formatInvocationTime(value) {
    return value ? String(value).replace('T', ' ') : '-'
  }

  function failureStageLabel(value, resultStatus) {
    if (!value)
      return ['ERROR', 'FAILED'].includes(resultStatus) ? '未记录' : '-'
    return {
      SCOPE_AUTHORIZATION: '调用范围校验',
      GRANT_RESOLUTION: '客户端授权解析',
      CAPABILITY_RESOLUTION: '能力版本解析',
      ACTOR_AUTHORIZATION: '主体类型校验',
      RBAC_AUTHORIZATION: '用户权限校验',
      RATE_LIMIT: '调用频率限制',
      AUTHENTICATION: '身份认证',
      AUTHORIZATION: '能力授权',
      INPUT_PREPARATION: '入参准备',
      INPUT_SCHEMA_VALIDATION: '入参校验',
      POLICY_VALIDATION: '能力策略校验',
      IDEMPOTENCY: '幂等校验',
      AUDIT_RESERVATION: '审计预留',
      ADAPTER_RESOLUTION: '适配器解析',
      ADAPTER_EXECUTION: '业务执行',
      OUTPUT_SCHEMA_VALIDATION: '返回校验',
      AUDIT_FINALIZATION: '审计完成',
      AUDIT: '审计记录',
    }[value] || value
  }

  function resultStatusLabel(value) {
    return {
      SUCCESS: '成功',
      ERROR: '失败',
      FAILED: '失败',
      PENDING_APPROVAL: '等待审批',
    }[value] || value || '-'
  }

  watch(() => props.show, (visible) => {
    if (!visible)
      return
    activeTab.value = props.initialTab === 'grants' && props.canGrantQuery ? 'grants' : 'overview'
    grantRows.value = []
    logRows.value = []
    Object.assign(logFilters, { requestId: '', capabilityKeyword: '', actorKeyword: '' })
    if (activeTab.value === 'grants')
      loadGrants()
  })

  return {
    dict,
    reloadWorkbenchDicts,
    actorModeOptions,
    clientStatusOptions,
    mappingModeOptions,
    versionStrategyOptions,
    grantStatusOptions,
    actorTypeOptions,
    flowOperationOptions,
    activeTab,
    signatureEnabled,
    userDelegationEnabled,
    authModes,
    authModeText,
    mappingModeLabel,
    handleTabChange,
    dictLabel,
    grantRows,
    grantLoading,
    grantOptions,
    grantPagination,
    capabilityMap,
    grantColumns,
    ensureGrantOptions,
    loadGrants,
    changeGrantPage,
    changeGrantPageSize,
    capabilityName,
    grantVersionUpgradeAvailable,
    revokeGrant,
    switchGrantVersion,
    grantVisible,
    editingGrantId,
    grantSubmitting,
    grantOptionLoading,
    grantFormRef,
    grantForm,
    selectedCapability,
    capabilityOptions,
    grantFieldOptions,
    grantOperationOptions,
    grantRules,
    openGrantModal,
    capabilityUnavailableReason,
    handleCapabilityChange,
    parseFieldPolicy,
    parseDateTimeValue,
    submitGrant,
    logRows,
    logLoading,
    logFilters,
    logPagination,
    logColumns,
    loadLogs,
    searchLogs,
    resetLogFilters,
    changeLogPage,
    changeLogPageSize,
    logDetailVisible,
    logDetailLoading,
    logDetail,
    openLogDetail,
    userLabel,
    capabilityDisplay,
    normalizeFilter,
    formatInvocationTime,
    failureStageLabel,
    resultStatusLabel,
  }
}
