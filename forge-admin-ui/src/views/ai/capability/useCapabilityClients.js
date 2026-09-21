import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  addCapabilityClient,
  addClientUserAssertionMapping,
  disableClientUserAssertion,
  getCapabilityClient,
  getClientUserAssertionConfig,
  getClientUserAssertionMappingPage,
  removeClientUserAssertionMapping,
  revokeCapabilityClient,
  rotateCapabilityClientSecret,
  rotateCapabilityClientSigningKey,
  rotateClientUserAssertionKey,
  updateClientUserAssertionMappingRule,
} from '@/api/ai/capability'
import SystemTableCell from '@/components/common/SystemTableCell.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables'
import { useUserStore } from '@/store'
import { formatDateTime, request } from '@/utils'
import {
  forgetCapabilityCredential,
  rememberCapabilityCredential,
} from './capabilityCredentialSession'

export function useCapabilityClients() {
  const userStore = useUserStore()

  const route = useRoute()

  const { dict } = useDict(
    'ai_capability_client_status',
    'ai_capability_auth_mode',
    'ai_capability_client_actor_mode',
    'ai_capability_user_mapping_mode',
  )

  const clientStatusOptions = computed(() => dict.value.ai_capability_client_status || [])

  const authModeOptions = computed(() => dict.value.ai_capability_auth_mode || [])

  const actorModeOptions = computed(() => dict.value.ai_capability_client_actor_mode || [])

  const mappingModeOptions = computed(() => dict.value.ai_capability_user_mapping_mode || [])

  function hasPermission(permission) {
    if (userStore?.isAdmin)
      return true
    const permissions = Array.isArray(userStore?.permissions) ? userStore.permissions : []
    return permissions.includes(permission) || permissions.includes('*:*:*')
  }

  const canAdd = computed(() => hasPermission('ai:capability:client:add'))

  const canRotate = computed(() => hasPermission('ai:capability:client:rotate'))

  const canEdit = computed(() => hasPermission('ai:capability:client:edit'))

  const canRevoke = computed(() => hasPermission('ai:capability:client:revoke'))

  const canGrant = computed(() => hasPermission('ai:capability:grant:add'))

  const canGrantQuery = computed(() => hasPermission('ai:capability:grant:query'))

  const canGrantRevoke = computed(() => hasPermission('ai:capability:grant:revoke'))

  const canLogQuery = computed(() => hasPermission('ai:capability:invocation:query'))

  const crudRef = ref(null)

  const workbenchVisible = ref(false)
  const workbenchInitialTab = ref('overview')
  const newlyCreatedClientId = ref(null)

  const workbenchClient = ref(null)

  function openWorkbench(row, tab = 'overview') {
    workbenchInitialTab.value = tab
    workbenchClient.value = row
    workbenchVisible.value = true
  }

  async function openRequestedWorkbench() {
    const clientId = route.query.clientId
    if (!clientId)
      return
    try {
      const res = await getCapabilityClient(clientId)
      if (res.data)
        openWorkbench(res.data)
    }
    catch (error) {
      window.$message.error(error?.message || '指定接入工作台加载失败')
    }
  }

  onMounted(openRequestedWorkbench)

  const addVisible = ref(false)

  const addLoading = ref(false)

  const addFormRef = ref(null)

  const serviceUserLabel = ref('')

  const serviceOrgOptions = ref([])

  const serviceOrgLoading = ref(false)

  const addForm = reactive({
    clientCode: '',
    clientName: '',
    actorMode: 'USER_DELEGATION',
    serviceUserId: null,
    activeOrgId: null,
    authModes: [],
    expiresAt: null,
    remark: '',
  })

  const addRules = {
    clientCode: { required: true, message: '请输入客户端编码', trigger: 'blur' },
    clientName: { required: true, message: '请输入客户端名称', trigger: 'blur' },
    actorMode: { required: true, message: '请选择主体模式', trigger: 'change' },
    serviceUserId: conditionalSelectedIdRule('请选择服务账号'),
    activeOrgId: conditionalSelectedIdRule('请选择生效组织'),
    authModes: {
      trigger: 'change',
      validator: (_rule, value) => validateAuthModes(value),
    },
  }

  const requiresServiceIdentity = computed(() => ['SERVICE', 'HYBRID'].includes(addForm.actorMode))

  function conditionalSelectedIdRule(message) {
    return {
      trigger: 'change',
      validator: (_rule, value) => !requiresServiceIdentity.value || isPositiveId(value)
        ? true
        : new Error(message),
    }
  }

  function validateAuthModes(value) {
    if (!Array.isArray(value) || value.length === 0)
      return new Error('请至少选择一种认证模式')
    if (addForm.actorMode === 'USER_DELEGATION' && (value.length !== 1 || value[0] !== 'OAUTH'))
      return new Error('用户委托模式只支持 OAUTH')
    if (addForm.actorMode === 'HYBRID' && !value.includes('OAUTH'))
      return new Error('混合模式必须启用 OAUTH')
    return true
  }

  function isPositiveId(value) {
    if (typeof value === 'number')
      return Number.isInteger(value) && value > 0
    return typeof value === 'string' && /^[1-9]\d*$/.test(value)
  }

  watch(authModeOptions, (options) => {
    if (!addVisible.value || addForm.authModes.length > 0 || options.length === 0)
      return
    addForm.authModes = resolveDefaultAuthModes()
  })

  watch(() => addForm.actorMode, (actorMode) => {
    if (actorMode === 'USER_DELEGATION') {
      addForm.serviceUserId = null
      addForm.activeOrgId = null
      addForm.authModes = ['OAUTH']
      serviceUserLabel.value = ''
      serviceOrgOptions.value = []
    }
  })

  function openAddModal() {
    Object.assign(addForm, {
      clientCode: '',
      clientName: '',
      actorMode: 'USER_DELEGATION',
      serviceUserId: null,
      activeOrgId: null,
      authModes: [],
      expiresAt: null,
      remark: '',
    })
    serviceUserLabel.value = ''
    serviceOrgOptions.value = []
    addForm.authModes = ['OAUTH']
    addVisible.value = true
  }

  function resolveDefaultAuthModes() {
    const defaults = authModeOptions.value
      .filter(item => item.isDefault === 'Y')
      .map(item => item.value)
    return defaults.length ? defaults : authModeOptions.value.slice(0, 1).map(item => item.value)
  }

  async function handleServiceUserSelect(user) {
    addForm.activeOrgId = null
    serviceOrgOptions.value = []
    if (!user?.id)
      return

    serviceOrgLoading.value = true
    try {
      const res = await request.get(`/system/user/${user.id}/org-bindings`)
      serviceOrgOptions.value = uniqueOrganizationBindings(res.data || [])
        .map(item => ({
          label: item.orgName || `组织 ${item.orgId}`,
          value: item.orgId,
          isMain: item.isMain,
        }))
      const defaultOrg = serviceOrgOptions.value.find(item => item.isMain === 1)
        || serviceOrgOptions.value[0]
      addForm.activeOrgId = defaultOrg?.value || null
      if (!defaultOrg)
        window.$message.warning('所选账号未绑定可用组织')
    }
    catch (error) {
      window.$message.error(error?.message || '服务账号组织加载失败')
    }
    finally {
      serviceOrgLoading.value = false
    }
  }

  function uniqueOrganizationBindings(bindings) {
    const unique = new Map()
    bindings.forEach((item) => {
      if (item.orgId && !unique.has(item.orgId))
        unique.set(item.orgId, item)
    })
    return [...unique.values()]
  }

  async function handleAddSubmit() {
    if (addLoading.value)
      return
    addLoading.value = true
    try {
      await addFormRef.value?.validate()
    }
    catch {
      addLoading.value = false
      return
    }
    try {
      const res = await addCapabilityClient({
        clientCode: addForm.clientCode,
        clientName: addForm.clientName,
        actorMode: addForm.actorMode,
        serviceUserId: requiresServiceIdentity.value ? addForm.serviceUserId : null,
        activeOrgId: requiresServiceIdentity.value ? addForm.activeOrgId : null,
        authModes: addForm.authModes.length ? addForm.authModes.join(',') : null,
        expiresAt: addForm.expiresAt ? formatDateTime(addForm.expiresAt) : null,
        remark: addForm.remark || null,
      })
      if (res.code === 200) {
        addVisible.value = false
        showIssuedCredential('接入系统已创建', res.data)
        newlyCreatedClientId.value = res.data?.clientId || null
        crudRef.value?.refresh()
      }
    }
    finally {
      addLoading.value = false
    }
  }

  const issuedVisible = ref(false)

  const issuedCredential = ref(null)

  function showIssuedCredential(title, data) {
    newlyCreatedClientId.value = null
    if (!data)
      return
    rememberCapabilityCredential(data)
    issuedCredential.value = { ...data, title }
    issuedVisible.value = true
  }

  function clearIssuedCredential() {
    issuedCredential.value = null
    newlyCreatedClientId.value = null
  }

  async function continueToAuthorization() {
    const id = newlyCreatedClientId.value
    if (!id)
      return
    try {
      const response = await getCapabilityClient(id)
      if (response.data) {
        issuedVisible.value = false
        openWorkbench(response.data, 'grants')
      }
    }
    catch (error) { window.$message.error(error.message || '授权工作台加载失败，请重试') }
  }

  function downloadIssuedPrivateKey() {
    const privateKey = issuedCredential.value?.privateKeyPem
    if (!privateKey)
      return
    const clientCode = issuedCredential.value?.clientCode || 'forge-client'
    const url = URL.createObjectURL(new Blob([privateKey], { type: 'application/x-pem-file' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${clientCode}-user-assertion-private-key.pem`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  }

  const userAssertionVisible = ref(false)

  const userAssertionLoading = ref(false)

  const userAssertionRotating = ref(false)

  const userAssertionDisabling = ref(false)

  const currentUserAssertionClient = ref(null)

  const userAssertionConfig = ref(null)

  const mappingFormRef = ref(null)

  const mappingSubmitting = ref(false)

  const mappingPageLoading = ref(false)

  const mappingRuleUpdating = ref(false)

  const mappingRows = ref([])

  const mappingKeyword = ref('')

  const mappingPagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
  })

  const mappingUserLabel = ref('')

  const mappingForm = reactive({
    externalSubject: '',
    userId: null,
  })

  const mappingRules = {
    externalSubject: [
      { required: true, message: '请输入外围用户标识', trigger: ['blur', 'input'] },
      { max: 512, message: '外围用户标识长度不能超过512个字符', trigger: ['blur', 'input'] },
    ],
    userId: {
      trigger: 'change',
      validator: (_rule, value) => isPositiveId(value) ? true : new Error('请选择 Forge 普通用户'),
    },
  }

  const mappingRuleDescription = computed(() => userAssertionConfig.value?.mappingMode === 'VERIFIED_PHONE'
    ? '首次验签成功后，按 JWT phone_number 在当前租户唯一匹配普通启用用户并固化映射；无匹配或多匹配都会拒绝。'
    : '安全默认：管理员预先把外围 JWT sub 绑定到 Forge 普通用户，外围系统不能自行指定 Forge 用户。')

  const mappingColumns = computed(() => [
    {
      title: '外围用户标识',
      key: 'subjectHint',
      minWidth: 150,
      render: row => row.subjectHint || `指纹 ${row.subjectHashPrefix || '-'}`,
    },
    {
      title: 'Forge 用户',
      key: 'userId',
      minWidth: 180,
      render: row => `${row.realName || row.username || '-'}（${row.username || row.userId}）`,
    },
    {
      title: '最近认证',
      key: 'lastAuthenticatedAt',
      width: 170,
      render: row => row.lastAuthenticatedAt || '尚未认证',
    },
    {
      title: '绑定时间',
      key: 'createTime',
      width: 170,
      render: row => row.createTime || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right',
      render: row => h('a', {
        class: 'text-error cursor-pointer hover:text-error-hover',
        onClick: () => handleRemoveUserAssertionMapping(row),
      }, '解除'),
    },
  ])

  async function openUserAssertion(row) {
    currentUserAssertionClient.value = row
    userAssertionVisible.value = true
    await loadUserAssertionConfig()
  }

  function openIdentityFromWorkbench(row) {
    workbenchVisible.value = false
    openUserAssertion(row)
  }

  async function loadUserAssertionConfig() {
    const clientId = currentUserAssertionClient.value?.id
    if (!clientId)
      return
    userAssertionLoading.value = true
    try {
      const res = await getClientUserAssertionConfig(clientId)
      userAssertionConfig.value = res.data || null
      await loadMappingPage()
    }
    catch (error) {
      window.$message.error(error?.message || '用户断言配置加载失败')
      userAssertionVisible.value = false
    }
    finally {
      userAssertionLoading.value = false
    }
  }

  function clearUserAssertionState() {
    currentUserAssertionClient.value = null
    userAssertionConfig.value = null
    mappingRows.value = []
    mappingKeyword.value = ''
    mappingPagination.page = 1
    mappingPagination.itemCount = 0
    resetMappingForm()
  }

  async function loadMappingPage() {
    const clientId = currentUserAssertionClient.value?.id
    if (!clientId)
      return
    mappingPageLoading.value = true
    try {
      const res = await getClientUserAssertionMappingPage(clientId, {
        pageNum: mappingPagination.page,
        pageSize: mappingPagination.pageSize,
        keyword: mappingKeyword.value.trim() || undefined,
      })
      mappingRows.value = res.data?.records || []
      mappingPagination.itemCount = Number(res.data?.total || 0)
    }
    catch (error) {
      mappingRows.value = []
      mappingPagination.itemCount = 0
      window.$message.error(error?.message || '外围用户映射加载失败')
    }
    finally {
      mappingPageLoading.value = false
    }
  }

  function searchMappingPage() {
    mappingPagination.page = 1
    loadMappingPage()
  }

  function handleMappingPageChange(page) {
    mappingPagination.page = page
    loadMappingPage()
  }

  function handleMappingPageSizeChange(pageSize) {
    mappingPagination.pageSize = pageSize
    mappingPagination.page = 1
    loadMappingPage()
  }

  function handleMappingRuleChange(mappingMode) {
    if (!userAssertionConfig.value || mappingMode === userAssertionConfig.value.mappingMode)
      return
    const isAutomatic = mappingMode === 'VERIFIED_PHONE'
    window.$dialog.warning({
      title: isAutomatic ? '启用可信手机号自动映射' : '恢复管理员预绑定',
      content: isAutomatic
        ? '仅应在外围系统能保护客户端私钥、并保证 phone_number 已完成短信或实名校验时启用。Forge 会在验签成功后按租户内手机号唯一匹配普通用户；管理员、无匹配和多匹配都会拒绝。是否确认？'
        : '恢复后，未预绑定的外围用户将不能换取 Forge 用户令牌。已有固化映射不受影响。是否确认？',
      positiveText: '确认修改',
      negativeText: '取消',
      onPositiveClick: async () => {
        mappingRuleUpdating.value = true
        try {
          const res = await updateClientUserAssertionMappingRule(
            userAssertionConfig.value.clientId,
            { mappingMode },
          )
          if (res.code === 200) {
            userAssertionConfig.value = { ...userAssertionConfig.value, mappingMode }
            window.$message.success('外围用户映射规则已更新')
            crudRef.value?.refresh()
          }
        }
        finally {
          mappingRuleUpdating.value = false
        }
      },
    })
  }

  function resetMappingForm() {
    mappingForm.externalSubject = ''
    mappingForm.userId = null
    mappingUserLabel.value = ''
    mappingFormRef.value?.restoreValidation?.()
  }

  function handleRotateUserAssertionKey() {
    const config = userAssertionConfig.value
    if (!config)
      return
    window.$dialog.warning({
      title: config.keyVersion ? '轮换用户断言密钥' : '生成用户断言密钥',
      content: config.keyVersion
        ? '轮换后旧私钥签发的断言和当前客户端短期令牌将立即失效。新私钥只展示一次，是否继续？'
        : '系统将生成独立 RSA-2048 密钥对，私钥只展示一次。是否继续？',
      positiveText: config.keyVersion ? '确认轮换' : '确认生成',
      negativeText: '取消',
      onPositiveClick: async () => {
        userAssertionRotating.value = true
        try {
          const res = await rotateClientUserAssertionKey(config.clientId)
          if (res.code === 200) {
            showIssuedCredential('用户断言私钥已生成', res.data)
            await loadUserAssertionConfig()
            crudRef.value?.refresh()
          }
        }
        finally {
          userAssertionRotating.value = false
        }
      },
    })
  }

  function handleDisableUserAssertion() {
    const config = userAssertionConfig.value
    if (!config)
      return
    window.$dialog.warning({
      title: '停用用户断言',
      content: '停用后外围系统不能再用客户端签名 JWT 换取令牌，已签发的当前客户端短期令牌也会失效。用户映射会保留，便于以后重新启用。',
      positiveText: '确认停用',
      negativeText: '取消',
      onPositiveClick: async () => {
        userAssertionDisabling.value = true
        try {
          const res = await disableClientUserAssertion(config.clientId)
          if (res.code === 200) {
            window.$message.success('用户断言已停用')
            await loadUserAssertionConfig()
            crudRef.value?.refresh()
          }
        }
        finally {
          userAssertionDisabling.value = false
        }
      },
    })
  }

  async function handleAddUserAssertionMapping() {
    try {
      await mappingFormRef.value?.validate()
    }
    catch {
      return
    }
    const clientId = userAssertionConfig.value?.clientId
    if (!clientId)
      return
    mappingSubmitting.value = true
    try {
      const res = await addClientUserAssertionMapping(clientId, {
        externalSubject: mappingForm.externalSubject.trim(),
        userId: mappingForm.userId,
      })
      if (res.code === 200) {
        window.$message.success('外围用户映射已保存')
        resetMappingForm()
        mappingPagination.page = 1
        await loadMappingPage()
      }
    }
    finally {
      mappingSubmitting.value = false
    }
  }

  function handleRemoveUserAssertionMapping(row) {
    const clientId = userAssertionConfig.value?.clientId
    if (!clientId)
      return
    window.$dialog.warning({
      title: '解除用户映射',
      content: `确定解除「${row.subjectHint || row.subjectHashPrefix}」与 Forge 用户的映射吗？该外围用户之后将无法换取令牌。`,
      positiveText: '确认解除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res = await removeClientUserAssertionMapping(clientId, row.id)
        if (res.code === 200) {
          window.$message.success('用户映射已解除')
          await loadMappingPage()
        }
      },
    })
  }

  function handleRotateSecret(row) {
    window.$dialog.warning({
      title: '轮换密钥确认',
      content: `确定轮换客户端「${row.clientName}」的密钥吗？旧密钥将立即失效，新密钥仅展示一次。`,
      positiveText: '确定轮换',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res = await rotateCapabilityClientSecret(row.id)
        if (res.code === 200) {
          showIssuedCredential('客户端密钥已轮换', res.data)
          crudRef.value?.refresh()
        }
      },
    })
  }

  function handleRotateSigningKey(row) {
    window.$dialog.warning({
      title: '轮换签名密钥确认',
      content: `确定轮换客户端「${row.clientName}」的签名密钥吗？旧签名密钥将立即失效，新密钥仅展示一次。`,
      positiveText: '确定轮换',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res = await rotateCapabilityClientSigningKey(row.id)
        if (res.code === 200) {
          showIssuedCredential('签名密钥已轮换', res.data)
          crudRef.value?.refresh()
        }
      },
    })
  }

  function handleRevoke(row) {
    window.$dialog.warning({
      title: '吊销确认',
      content: `确定吊销客户端「${row.clientName}」吗？吊销后该客户端所有凭据立即失效且不可恢复。`,
      positiveText: '确定吊销',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res = await revokeCapabilityClient(row.id)
        if (res.code === 200) {
          window.$message.success('客户端已吊销')
          forgetCapabilityCredential(row.id)
          workbenchVisible.value = false
          crudRef.value?.refresh()
        }
      },
    })
  }

  const searchSchema = computed(() => [
    {
      field: 'keyword',
      label: '关键字',
      type: 'input',
      props: {
        placeholder: '客户端编码/名称',
      },
    },
    {
      field: 'status',
      label: '状态',
      type: 'select',
      props: {
        placeholder: '请选择状态',
        clearable: true,
        options: clientStatusOptions.value,
      },
    },
  ])

  const tableColumns = computed(() => [
    { prop: 'clientName', label: '接入系统', minWidth: 230, render: row => h(SystemTableCell, { title: row.clientName, subtitle: row.clientCode, interactive: true, onActivate: () => openWorkbench(row) }) },
    { prop: 'actorMode', label: '执行身份', width: 145, render: row => h(DictTag, { options: actorModeOptions.value, value: row.actorMode, size: 'small' }) },
    { prop: 'remark', label: '用途说明', minWidth: 180, ellipsis: { tooltip: true }, render: row => row.remark || '未填写' },
    {
      prop: 'status',
      label: '状态',
      width: 90,
      render: (row) => {
        return h(DictTag, {
          options: clientStatusOptions.value,
          value: row.status,
          size: 'small',
        })
      },
    },
    {
      prop: 'expiresAt',
      label: '过期时间',
      width: 160,
      render: row => row.expiresAt || '长期有效',
    },
    {
      prop: 'lastUsedAt',
      label: '最近调用',
      width: 160,
      render: row => row.lastUsedAt || '-',
    },
    {
      prop: 'action',
      label: '操作',
      width: 170,
      fixed: 'right',
      actions: [
        {
          label: '管理接入',
          key: 'workbench',
          type: 'primary',
          onClick: openWorkbench,
          visible: () => true,
        },
        {
          label: '授权能力',
          key: 'grant',
          type: 'primary',
          onClick: row => openWorkbench(row, 'grants'),
          visible: () => canGrantQuery.value,
        },
      ],
    },
  ])

  return {
    userStore,
    route,
    dict,
    clientStatusOptions,
    authModeOptions,
    actorModeOptions,
    mappingModeOptions,
    hasPermission,
    canAdd,
    canRotate,
    canEdit,
    canRevoke,
    canGrant,
    canGrantQuery,
    canGrantRevoke,
    canLogQuery,
    crudRef,
    workbenchVisible,
    workbenchInitialTab,
    newlyCreatedClientId,
    continueToAuthorization,
    workbenchClient,
    openWorkbench,
    openRequestedWorkbench,
    addVisible,
    addLoading,
    addFormRef,
    serviceUserLabel,
    serviceOrgOptions,
    serviceOrgLoading,
    addForm,
    addRules,
    requiresServiceIdentity,
    conditionalSelectedIdRule,
    validateAuthModes,
    isPositiveId,
    openAddModal,
    resolveDefaultAuthModes,
    handleServiceUserSelect,
    uniqueOrganizationBindings,
    handleAddSubmit,
    issuedVisible,
    issuedCredential,
    showIssuedCredential,
    clearIssuedCredential,
    downloadIssuedPrivateKey,
    userAssertionVisible,
    userAssertionLoading,
    userAssertionRotating,
    userAssertionDisabling,
    currentUserAssertionClient,
    userAssertionConfig,
    mappingFormRef,
    mappingSubmitting,
    mappingPageLoading,
    mappingRuleUpdating,
    mappingRows,
    mappingKeyword,
    mappingPagination,
    mappingUserLabel,
    mappingForm,
    mappingRules,
    mappingRuleDescription,
    mappingColumns,
    openUserAssertion,
    openIdentityFromWorkbench,
    loadUserAssertionConfig,
    clearUserAssertionState,
    loadMappingPage,
    searchMappingPage,
    handleMappingPageChange,
    handleMappingPageSizeChange,
    handleMappingRuleChange,
    resetMappingForm,
    handleRotateUserAssertionKey,
    handleDisableUserAssertion,
    handleAddUserAssertionMapping,
    handleRemoveUserAssertionMapping,
    handleRotateSecret,
    handleRotateSigningKey,
    handleRevoke,
    searchSchema,
    tableColumns,
  }
}
