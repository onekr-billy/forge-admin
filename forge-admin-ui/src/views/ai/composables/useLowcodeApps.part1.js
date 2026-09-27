/** src/views/ai/lowcode-apps.vue setup part 1. */
import { EllipsisVertical, SaveOutline, SparklesOutline } from '@vicons/ionicons5'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { modelListByProvider, providerPage } from '@/api/ai'
import {
  lowcodeAiStreamGenerateApp,
  lowcodeAppDetail,
  lowcodeAppPage,
  lowcodeCreateDomain,
  lowcodeCreateModel,
  lowcodeDeleteApp,
  lowcodeDeleteDomain,
  lowcodeDomainDetail,
  lowcodeDomainTree,
  lowcodeDownloadAppCode,
  lowcodeModelList,
  lowcodeSaveDraft,
} from '@/api/lowcode-crud'
import LowcodeCodePreviewModal from '@/components/lowcode-builder/code/LowcodeCodePreviewModal.vue'
import DomainEditorDrawer from '@/components/lowcode-builder/domain/DomainEditorDrawer.vue'
import DomainTreePanel from '@/components/lowcode-builder/domain/DomainTreePanel.vue'
import MoveDomainModal from '@/components/lowcode-builder/domain/MoveDomainModal.vue'
import { cloneSchema, normalizeObjectCode } from '@/components/lowcode-builder/model/model-schema'
import { useDict } from '@/composables/useDict'
export function applyLowcodeAppsPart1() {
  const __impl = {}
  const mut = {
    appSearchTimer: null,
  }
  const appInitial = (...args) => __impl.appInitial(...args)
  const downloadBlob = (...args) => __impl.downloadBlob(...args)
  const downloadJson = (...args) => __impl.downloadJson(...args)
  const formatTime = (...args) => __impl.formatTime(...args)
  
  const { dict } = useDict('lowcode_app_publish_status')

  const router = useRouter()
  const domainLoading = ref(false)
  const loading = ref(false)
  const domains = ref([])
  const selectedDomainId = ref(null)
  const selectedDomain = ref(null)
  const apps = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(9)
  const keyword = ref('')
  const domainKeyword = ref('')
  const publishStatus = ref(null)
  const domainEditorVisible = ref(false)
  const editingDomain = ref(null)
  const moveVisible = ref(false)
  const movingApp = ref(null)
  const appImportInputRef = ref(null)
  const codePreviewVisible = ref(false)
  const codePreviewApp = ref(null)
  const downloadingCodeId = ref(null)
  const aiCreateVisible = ref(false)
  const aiCreateLoading = ref(false)
  const aiConfirmSaving = ref(false)
  const aiCreateDescription = ref('')
  const aiCreateResult = ref(null)
  const aiStreamController = ref(null)
  const aiSteps = ref(createInitialAiSteps())
  const aiTargetDomainId = ref(null)
  const aiRawContent = ref('')
  const aiReasoningContent = ref('')
  const aiIsReasoningPhase = ref(false)
  const aiReasoningStartTime = ref(null)
  const aiReasoningEndTime = ref(null)
  const aiProviderId = ref(null)
  const aiModelId = ref(null)
  const aiProviderOptions = ref([])
  const aiModelOptions = ref([])
  const aiProviderLoading = ref(false)
  const aiModelLoading = ref(false)
  const aiIncludeDomainModels = ref(true)
  const domainModelContext = ref([])
  const domainModelLoading = ref(false)
  const activePreviewModelCode = ref(null)
  const aiPreviewDetailVisible = ref(false)
  const aiPreviewDetailType = ref('er')
  const aiAppendInstruction = ref('')

  const statusOptions = computed(() => dict.value.lowcode_app_publish_status || [])
  const flatDomains = computed(() => flattenDomains(domains.value))
  const domainSelectOptions = computed(() => flatDomains.value.map(domain => ({
    label: `${'  '.repeat(domain.level || 0)}${domain.domainName} (${domain.domainCode})`,
    value: domain.id,
  })))
  const aiTargetDomain = computed(() => flatDomains.value.find(domain => domain.id === aiTargetDomainId.value) || null)
  const aiReasoningTime = computed(() => {
    if (!aiReasoningStartTime.value)
      return null
    const endTime = aiReasoningEndTime.value || (aiIsReasoningPhase.value ? Date.now() : null)
    return endTime ? Math.max(1, Math.round((endTime - aiReasoningStartTime.value) / 1000)) : null
  })
  const aiGeneratedDomains = computed(() => aiCreateResult.value?.domains || [])
  const aiGeneratedModels = computed(() => {
    const result = aiCreateResult.value
    if (!result)
      return []
    return result.models?.length ? result.models : result.modelDraft ? [result.modelDraft] : []
  })
  const aiGeneratedApps = computed(() => {
    const result = aiCreateResult.value
    if (!result)
      return []
    return result.apps?.length ? result.apps : result.appDraft ? [result.appDraft] : []
  })
  const aiDecisions = computed(() => aiCreateResult.value?.decisions || [])
  const aiNotes = computed(() => aiCreateResult.value?.generationNotes || [])
  const previewModelOptions = computed(() => aiGeneratedModels.value.map(model => ({
    label: `${model.modelName || model.modelCode} (${model.modelCode})`,
    value: model.modelCode,
  })))
  const activePreviewModel = computed(() => {
    if (!aiGeneratedModels.value.length)
      return null
    return aiGeneratedModels.value.find(model => model.modelCode === activePreviewModelCode.value) || aiGeneratedModels.value[0]
  })
  const activePreviewFields = computed(() => (activePreviewModel.value?.modelSchema?.fields || []).map((field, index) => ({
    ...field,
    index: index + 1,
  })))
  const aiPrimaryDomainLabel = computed(() => {
    const domain = aiGeneratedDomains.value[0] || aiTargetDomain.value || selectedDomain.value
    return domain?.domainName || '待识别'
  })
  const aiPrimaryModelName = computed(() => aiGeneratedModels.value[0]?.modelName || '核心实体')
  const aiRelationCount = computed(() => {
    const schemaRelationCount = aiGeneratedModels.value.reduce((count, model) => {
      const relations = model.modelSchema?.relations
      return count + (Array.isArray(relations) ? relations.length : 0)
    }, 0)
    if (schemaRelationCount)
      return schemaRelationCount
    return aiGeneratedApps.value.reduce((count, app) => {
      const refs = app.pageSchema?.modelRefs
      return count + (Array.isArray(refs) ? refs.filter(ref => !ref.primary).length : 0)
    }, 0)
  })
  const aiModelRelationRows = computed(() => {
    return aiGeneratedModels.value.flatMap((model, modelIndex) => {
      const relations = model.modelSchema?.relations
      if (!Array.isArray(relations))
        return []
      return relations.map((relation, relationIndex) => ({
        key: `${model.modelCode || modelIndex}-${relationIndex}`,
        source: model.modelName || model.modelCode || '-',
        type: relation.relationType || relation.type || relation.cardinality || '关联',
        target: relation.targetModelName || relation.targetModelCode || relation.targetModel || relation.target || '-',
      }))
    })
  })
  const aiApiDetailRows = computed(() => {
    const targets = aiGeneratedApps.value.length ? aiGeneratedApps.value : aiGeneratedModels.value
    return targets.flatMap(target => buildApiRowsForTarget(target))
  })
  const aiApiCount = computed(() => aiApiDetailRows.value.length)
  const aiCompletionPercent = computed(() => aiCreateResult.value ? '100%' : aiCreateLoading.value ? '...' : '0%')
  const aiValidationStatus = computed(() => aiCreateResult.value ? '待确认' : aiCreateLoading.value ? '生成中' : '未校验')
  const aiPageStructureRows = computed(() => {
    if (!aiGeneratedApps.value.length)
      return []
    return aiGeneratedApps.value.slice(0, 5).map((app, index) => ({
      key: app.configKey || app.appName || app.menuName || `app-${index}`,
      name: app.appName || app.menuName || app.configKey,
      layout: layoutName(app.pageSchema?.layoutType),
    }))
  })
  const aiPreviewFields = computed(() => {
    return activePreviewFields.value.slice(0, 4)
  })
  const aiApiPreviewRows = computed(() => {
    return aiApiDetailRows.value.slice(0, 4)
  })
  const aiPageDetailRows = computed(() => {
    return aiGeneratedApps.value.map((app, index) => {
      const appModel = resolveAppModel(app)
      const fields = appModel?.modelSchema?.fields || app.modelSchema?.fields || []
      const zones = app.pageSchema?.zones || []
      const modelRefs = app.pageSchema?.modelRefs || []
      return {
        index: index + 1,
        appName: app.appName || app.menuName || app.configKey,
        configKey: app.configKey || '-',
        layout: layoutName(app.pageSchema?.layoutType),
        modelCount: modelRefs.length || (appModel ? 1 : 0),
        fieldCount: fields.length,
        zoneCount: zones.length,
      }
    })
  })
  const aiPreviewDetailTitle = computed(() => {
    if (aiPreviewDetailType.value === 'er')
      return 'ER 图详情'
    if (aiPreviewDetailType.value === 'pages')
      return '页面结构详情'
    if (aiPreviewDetailType.value === 'fields')
      return '字段清单详情'
    return '接口建议详情'
  })
  const modelPreviewColumns = [
    { title: '#', key: 'index', width: 48 },
    { title: '字段', key: 'field', minWidth: 120 },
    { title: '列名', key: 'columnName', minWidth: 130 },
    { title: '名称', key: 'label', minWidth: 120 },
    { title: '类型', key: 'dataType', width: 90 },
    { title: '组件', key: 'componentType', width: 120 },
  ]
  const appPreviewColumns = [
    { title: '#', key: 'index', width: 48 },
    { title: '应用页面', key: 'appName', minWidth: 150 },
    { title: '配置标识', key: 'configKey', minWidth: 150 },
    { title: '布局', key: 'layout', width: 110 },
    { title: '模型数', key: 'modelCount', width: 90 },
    { title: '字段数', key: 'fieldCount', width: 90 },
    { title: '区域数', key: 'zoneCount', width: 90 },
  ]
  const apiPreviewColumns = [
    { title: '应用/模型', key: 'ownerName', minWidth: 150 },
    { title: '方法', key: 'method', width: 80 },
    { title: '路径', key: 'path', minWidth: 220 },
    { title: '说明', key: 'description', minWidth: 140 },
  ]

  const pageTitle = computed(() => '低代码应用')
  const pageSubtitle = computed(() => {
    if (!selectedDomain.value)
      return '按业务领域组织低代码应用，统一管理对象、规则和发布入口'
    return selectedDomain.value.domainDesc || `领域编码：${selectedDomain.value.domainCode}`
  })
  const appScopeText = computed(() => selectedDomain.value ? `当前领域及子目录：${selectedDomain.value.domainName}` : '当前展示全部业务领域应用')

  onMounted(async () => {
    await Promise.all([
      loadDomains(),
      loadApps(),
    ])
  })

  onBeforeUnmount(() => {
    clearPendingAppSearch()
  })

  async function loadDomains() {
    domainLoading.value = true
    try {
      const res = await lowcodeDomainTree({
        keyword: domainKeyword.value || undefined,
      })
      domains.value = res.data || []
    }
    finally {
      domainLoading.value = false
    }
  }

  async function refreshDomains() {
    await loadDomains()
  }

  async function selectDomain(domain) {
    pageNum.value = 1
    clearPendingAppSearch()
    if (!domain) {
      selectedDomainId.value = null
      selectedDomain.value = null
      await loadApps()
      return
    }
    selectedDomainId.value = domain.id
    selectedDomain.value = domain
    await loadApps()
  }

  async function loadApps() {
    loading.value = true
    try {
      const res = await lowcodeAppPage({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        publishStatus: publishStatus.value || undefined,
        domainId: selectedDomainId.value || undefined,
      })
      apps.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
    finally {
      loading.value = false
    }
  }

  function openAiPreviewDetail(type) {
    if (type === 'fields' && !activePreviewModelCode.value)
      activePreviewModelCode.value = aiGeneratedModels.value[0]?.modelCode || null
    aiPreviewDetailType.value = type
    aiPreviewDetailVisible.value = true
  }

  function resolveAppModel(app) {
    const primaryRef = app.pageSchema?.modelRefs?.find(ref => ref.primary) || app.pageSchema?.modelRefs?.[0]
    const modelCode = app.modelCode || app.objectCode || primaryRef?.modelCode
    return aiGeneratedModels.value.find((model) => {
      const schemaObjectCode = model.modelSchema?.object?.code
      return model.modelCode === modelCode || schemaObjectCode === modelCode || model.modelCode === app.objectCode
    }) || null
  }

  function buildApiRowsForTarget(target) {
    const objectCode = target.objectCode || target.modelCode || normalizeObjectCode(target.configKey || target.appName || target.modelName)
    if (!objectCode)
      return []
    const ownerName = target.appName || target.modelName || target.menuName || target.configKey || target.modelCode || objectCode
    const basePath = `/api/${objectCode}`
    return [
      { ownerName, method: 'GET', path: `${basePath}/page`, description: '分页查询' },
      { ownerName, method: 'GET', path: `${basePath}/{id}`, description: '详情查询' },
      { ownerName, method: 'POST', path: basePath, description: '新增' },
      { ownerName, method: 'PUT', path: basePath, description: '修改' },
      { ownerName, method: 'DELETE', path: `${basePath}/{id}`, description: '删除' },
    ]
  }

  async function refreshAll() {
    clearPendingAppSearch()
    await Promise.all([
      loadDomains(),
      loadApps(),
    ])
  }

  function clearPendingAppSearch() {
    if (!mut.appSearchTimer)
      return
    window.clearTimeout(mut.appSearchTimer)
    mut.appSearchTimer = null
  }

  function handleKeywordSearch() {
    pageNum.value = 1
    clearPendingAppSearch()
    mut.appSearchTimer = window.setTimeout(() => {
      mut.appSearchTimer = null
      loadApps()
    }, 300)
  }

  function handleSearch() {
    pageNum.value = 1
    clearPendingAppSearch()
    loadApps()
  }

  function handlePageChange() {
    clearPendingAppSearch()
    loadApps()
  }

  function handlePageSizeChange() {
    pageNum.value = 1
    clearPendingAppSearch()
    loadApps()
  }

  function createApp(domain) {
    if (!domain) {
      window.$message?.warning('请先选择业务领域，再新建应用')
      return
    }
    if (domain?.status === 'DISABLED') {
      window.$message?.warning('停用领域不能新建应用')
      return
    }
    router.push({
      path: '/ai/lowcode-builder',
      query: domain
        ? {
            domainId: domain.id,
            domainCode: domain.domainCode,
            domainName: domain.domainName,
          }
        : {},
    })
  }

  function openBuilder(id) {
    router.push(`/ai/lowcode-builder/${id}`)
  }

  function openRuntime(configKey) {
    const route = router.resolve(`/ai/crud-page/${configKey}`)
    window.open(route.href, '_blank')
  }

  function openCodePreview(app) {
    codePreviewApp.value = app
    codePreviewVisible.value = true
  }

  async function downloadAppCode(app) {
    if (!app?.id) {
      window.$message?.warning('请先保存应用草稿')
      return
    }
    downloadingCodeId.value = app.id
    try {
      const blob = await lowcodeDownloadAppCode(app.id, { sourceType: 'DRAFT' })
      downloadBlob(blob, `${app.configKey || 'lowcode-app'}-code.zip`)
      window.$message?.success('代码包下载成功')
    }
    catch (e) {
      window.$message?.error(e?.message || '下载代码失败')
    }
    finally {
      downloadingCodeId.value = null
    }
  }

  function getAppActionOptions(app) {
    return [
      { label: '代码预览', key: 'codePreview' },
      { label: downloadingCodeId.value === app.id ? '下载中...' : '下载代码', key: 'downloadCode', disabled: downloadingCodeId.value === app.id },
      { label: '迁移领域', key: 'moveDomain' },
      { label: '导出配置', key: 'export' },
      { type: 'divider', key: 'divider' },
      { label: '删除应用', key: 'delete' },
    ]
  }

  async function handleAppActionSelect(key, app) {
    if (key === 'codePreview') {
      openCodePreview(app)
      return
    }
    if (key === 'downloadCode') {
      await downloadAppCode(app)
      return
    }
    if (key === 'moveDomain') {
      openMoveDomain(app)
      return
    }
    if (key === 'export') {
      await exportApp(app)
      return
    }
    if (key === 'delete')
      deleteApp(app)
  }

  function openAiCreateApp() {
    aiCreateDescription.value = ''
    aiCreateResult.value = null
    aiAppendInstruction.value = ''
    activePreviewModelCode.value = null
    aiTargetDomainId.value = selectedDomainId.value || null
    resetAiStreamState()
    aiSteps.value = createInitialAiSteps()
    aiCreateVisible.value = true
    loadAiProviderOptions()
    loadDomainModelContext(aiTargetDomainId.value)
  }

  function handleAiCreateVisibleUpdate(show) {
    if (!show)
      abortAiGeneration()
  }

  function closeAiCreateModal() {
    abortAiGeneration()
    aiCreateVisible.value = false
  }

  function abortAiGeneration() {
    aiStreamController.value?.abort()
    aiStreamController.value = null
    aiCreateLoading.value = false
    aiIsReasoningPhase.value = false
  }

  function generateAiAppDraft(refine = false) {
    if (!aiCreateDescription.value.trim()) {
      window.$message?.warning('请输入需求描述')
      return
    }
    if (refine && !aiAppendInstruction.value.trim()) {
      window.$message?.warning('请输入追加优化要求')
      return
    }
    const previousDraftContext = refine ? buildDraftContext() : undefined
    abortAiGeneration()
    aiCreateLoading.value = true
    aiCreateResult.value = null
    activePreviewModelCode.value = null
    resetAiStreamState()
    aiSteps.value = createInitialAiSteps()
    const description = refine
      ? `${aiCreateDescription.value.trim()}\n\n追加优化要求：${aiAppendInstruction.value.trim()}`
      : aiCreateDescription.value.trim()

    aiStreamController.value = lowcodeAiStreamGenerateApp(
      {
        description,
        domainId: aiTargetDomainId.value || undefined,
        providerId: aiProviderId.value || undefined,
        modelId: aiModelId.value || undefined,
        autoCreateModel: true,
        includeDdl: false,
        existingModels: aiIncludeDomainModels.value ? buildExistingModelContext() : [],
        draftContext: previousDraftContext,
      },
      handleAiStreamEvent,
      () => {
        aiCreateLoading.value = false
        aiStreamController.value = null
        markPendingAiStepsCompleted()
      },
      (message) => {
        aiCreateLoading.value = false
        aiStreamController.value = null
        markAiStepError(message)
        window.$message?.error(message || 'AI 生成业务系统失败')
      },
    )
  }

  function handleAiStreamEvent(payload) {
    if (payload.event === 'progress') {
      updateAiStep(payload.data)
      return
    }
    if (payload.event === 'chunk') {
      handleAiStreamChunk(payload.data?.content || '')
      return
    }
    if (payload.event === 'result') {
      aiCreateResult.value = payload.data
      activePreviewModelCode.value = aiGeneratedModels.value[0]?.modelCode || null
      aiAppendInstruction.value = ''
      applyAiResultSteps(payload.data)
    }
  }

  async function loadAiProviderOptions() {
    aiProviderLoading.value = true
    try {
      const res = await providerPage({ pageNum: 1, pageSize: 100, status: '0' })
      const records = (res.data?.records || []).filter(item => item.status === undefined || item.status === '0')
      aiProviderOptions.value = records.map(item => ({
        label: item.providerName,
        value: item.id,
      }))
      if (!aiProviderId.value) {
        const defaultProvider = records.find(item => item.isDefault === '1') || records[0]
        aiProviderId.value = defaultProvider?.id || null
      }
      await loadAiModelOptions(aiProviderId.value)
    }
    catch (e) {
      aiProviderOptions.value = []
      console.warn('[lowcode-apps] 加载 AI 供应商失败:', e.message)
    }
    finally {
      aiProviderLoading.value = false
    }
  }

  async function handleAiProviderChange(providerId) {
    aiModelId.value = null
    await loadAiModelOptions(providerId)
  }

  async function loadAiModelOptions(providerId) {
    if (!providerId) {
      aiModelOptions.value = []
      aiModelId.value = null
      return
    }
    aiModelLoading.value = true
    try {
      const res = await modelListByProvider(providerId)
      const models = (res.data || []).filter(item => item.status === undefined || item.status === '0')
      aiModelOptions.value = models.map(item => ({
        label: item.modelName ? `${item.modelName} (${item.modelId})` : item.modelId,
        value: item.id,
        modelCode: item.modelId,
        maxTokens: item.maxTokens,
        isDefault: item.isDefault,
      }))
      const hasSelected = aiModelOptions.value.some(item => item.value === aiModelId.value)
      if (!hasSelected) {
        const defaultModel = aiModelOptions.value.find(item => item.isDefault === '1') || aiModelOptions.value[0]
        aiModelId.value = defaultModel?.value || null
      }
    }
    catch (e) {
      aiModelOptions.value = []
      aiModelId.value = null
      console.warn('[lowcode-apps] 加载 AI 模型失败:', e.message)
    }
    finally {
      aiModelLoading.value = false
    }
  }

  async function handleAiTargetDomainChange(domainId) {
    await loadDomainModelContext(domainId)
  }

  async function loadDomainModelContext(domainId) {
    domainModelContext.value = []
    if (!domainId)
      return
    domainModelLoading.value = true
    try {
      const res = await lowcodeModelList({ domainId, status: 'ENABLED' })
      domainModelContext.value = res.data || []
    }
    catch (e) {
      domainModelContext.value = []
      console.warn('[lowcode-apps] 加载领域模型失败:', e.message)
    }
    finally {
      domainModelLoading.value = false
    }
  }

  function buildExistingModelContext() {
    return domainModelContext.value.map(model => ({
      id: model.id,
      domainId: model.domainId,
      modelCode: model.modelCode,
      modelName: model.modelName,
      modelDesc: model.modelDesc,
      status: model.status,
      tenantEnabled: model.tenantEnabled,
      masterData: model.masterData,
      modelSchema: cloneSchema(model.modelSchema || {}),
    }))
  }

  function buildDraftContext() {
    return JSON.stringify({
      domains: aiGeneratedDomains.value,
      models: aiGeneratedModels.value,
      apps: aiGeneratedApps.value,
      decisions: aiDecisions.value,
    })
  }

  function resetAiStreamState() {
    aiRawContent.value = ''
    aiReasoningContent.value = ''
    aiIsReasoningPhase.value = false
    aiReasoningStartTime.value = null
    aiReasoningEndTime.value = null
  }

  function handleAiStreamChunk(chunkContent) {
    if (!chunkContent)
      return
    if (chunkContent.includes('==================== 思考过程 ====================')) {
      aiIsReasoningPhase.value = true
      aiReasoningStartTime.value = Date.now()
      aiReasoningEndTime.value = null
      aiReasoningContent.value = ''
      const afterDelimiter = chunkContent.split('==================== 思考过程 ====================')[1] || ''
      aiReasoningContent.value += afterDelimiter.replace(/^\n/, '')
      updateAiStep({
        stepKey: 'analyzing',
        title: '理解业务需求',
        status: 'running',
        message: '模型正在分析业务边界和对象关系',
      })
      return
    }
    if (chunkContent.includes('==================== 完整回复 ====================')) {
      aiIsReasoningPhase.value = false
      aiReasoningEndTime.value = Date.now()
      const afterDelimiter = chunkContent.split('==================== 完整回复 ====================')[1] || ''
      aiRawContent.value += afterDelimiter.replace(/^\n/, '')
      return
    }
    if (aiIsReasoningPhase.value) {
      aiReasoningContent.value += chunkContent
    }
    else {
      aiRawContent.value += chunkContent
    }
  }

  function createInitialAiSteps() {
    return [
      { orderNo: 1, stepKey: 'analyzing', title: '理解业务需求', status: 'pending', message: '等待开始' },
      { orderNo: 2, stepKey: 'domain-planning', title: '划分业务领域', status: 'pending', message: '等待开始' },
      { orderNo: 3, stepKey: 'model-generating', title: '生成数据模型', status: 'pending', message: '等待开始' },
      { orderNo: 4, stepKey: 'page-generating', title: '生成应用页面', status: 'pending', message: '等待开始' },
      { orderNo: 5, stepKey: 'validating', title: '校验低代码协议', status: 'pending', message: '等待开始' },
    ]
  }

  function stepStatusText(status) {
    if (status === 'running')
      return '进行中'
    if (status === 'completed')
      return '已完成'
    if (status === 'error')
      return '异常'
    return '待开始'
  }

  function updateAiStep(data = {}) {
    const stepKey = data.stepKey || data.stage
    const index = aiSteps.value.findIndex(step => step.stepKey === stepKey)
    if (index < 0)
      return
    aiSteps.value = aiSteps.value.map((step, currentIndex) => {
      if (currentIndex < index && step.status !== 'error') {
        return { ...step, status: 'completed' }
      }
      if (currentIndex === index) {
        return {
          ...step,
          title: data.title || step.title,
          status: data.status || 'running',
          message: data.message || step.message,
          summary: data.summary || step.summary,
        }
      }
      return step
    })
  }

  function applyAiResultSteps(result) {
    if (!result?.steps?.length)
      return
    aiSteps.value = result.steps
      .slice()
      .sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0))
      .map(step => ({
        ...step,
        status: step.status || 'completed',
        message: step.message || step.summary || '已完成',
      }))
  }

  function markPendingAiStepsCompleted() {
    aiSteps.value = aiSteps.value.map(step => ({
      ...step,
      status: step.status === 'pending' || step.status === 'running' ? 'completed' : step.status,
      message: step.status === 'pending' ? '已完成' : step.message,
    }))
  }

  function markAiStepError(message) {
    const runningIndex = aiSteps.value.findIndex(step => step.status === 'running')
    const index = runningIndex >= 0 ? runningIndex : aiSteps.value.findIndex(step => step.status === 'pending')
    if (index < 0)
      return
    aiSteps.value = aiSteps.value.map((step, currentIndex) => {
      if (currentIndex === index)
        return { ...step, status: 'error', message: message || '生成失败' }
      return step
    })
  }

  async function confirmAiAppDraft() {
    if (!aiCreateResult.value)
      return
    const domains = aiGeneratedDomains.value
    const models = aiGeneratedModels.value
    const appsToSave = aiGeneratedApps.value
    if (!models.length || !appsToSave.length) {
      window.$message?.warning('AI 结果缺少模型或应用草稿')
      return
    }
    aiConfirmSaving.value = true
    try {
      const domainContext = await ensureAiDomains(domains)
      await saveAiModels(models, domainContext, appsToSave)
      const savedAppIds = await saveAiApps(appsToSave, models, domainContext)
      closeAiCreateModal()
      window.$message?.success(`已保存 ${models.length} 个模型和 ${savedAppIds.length} 个应用草稿`)
      await refreshAll()
      if (savedAppIds[0])
        openBuilder(savedAppIds[0])
    }
    catch (e) {
      window.$message?.error(e?.message || '保存 AI 应用草稿失败')
    }
    finally {
      aiConfirmSaving.value = false
    }
  }

  async function ensureAiDomains(domains) {
    const domainMap = new Map()
    const domainList = domains?.length ? domains : aiTargetDomain.value ? [aiTargetDomain.value] : []
    if (!domainList.length)
      throw new Error('AI 结果缺少业务领域草稿')

    for (const domain of domainList) {
      const existingId = domain.existingDomainId || domain.id
      if (existingId) {
        domainMap.set(domain.domainCode, {
          id: existingId,
          domainCode: domain.domainCode,
          domainName: domain.domainName,
          menuParentId: domain.menuParentId ?? aiTargetDomain.value?.menuParentId ?? null,
        })
        continue
      }
      const payload = {
        id: null,
        parentId: domain.parentId || 0,
        domainCode: domain.domainCode,
        domainName: domain.domainName,
        domainDesc: domain.domainDesc || aiCreateDescription.value,
        icon: domain.icon || 'apps',
        sort: domain.sort || 0,
        status: domain.status || 'ENABLED',
        menuParentId: domain.menuParentId || null,
        tablePrefix: domain.tablePrefix || `biz_${domain.domainCode}_`,
        configKeyPrefix: domain.configKeyPrefix || `${domain.domainCode}_`,
        defaultAppType: domain.defaultAppType || 'SINGLE',
        defaultLayoutType: domain.defaultLayoutType || 'simple-crud',
        defaultTableMode: domain.defaultTableMode || 'CREATE',
        domainSchema: cloneSchema(domain.domainSchema || {}),
      }
      const res = await lowcodeCreateDomain(payload)
      domainMap.set(domain.domainCode, {
        id: res.data,
        domainCode: domain.domainCode,
        domainName: domain.domainName,
        menuParentId: domain.menuParentId || null,
      })
    }
    return {
      domainMap,
      fallbackDomain: domainMap.values().next().value,
    }
  }

  async function saveAiModels(models, domainContext, appsToSave) {
    for (const modelDraft of models) {
      const relatedApp = appsToSave.find(app => app.objectCode && app.objectCode === modelDraft.modelCode) || appsToSave[0]
      const modelSchema = cloneSchema(modelDraft.modelSchema || aiCreateResult.value.modelSchema || {})
      const domainInfo = resolveAiDomainInfo(modelSchema?.domain?.code || relatedApp?.domainCode, domainContext)
      applyDomainToModelSchema(modelSchema, domainInfo)
      const modelCode = modelDraft.modelCode || modelSchema.object?.code || modelSchema.tableName
      const modelName = modelDraft.modelName || modelSchema.object?.name || modelSchema.businessName || modelCode
      if (!modelSchema?.fields?.length)
        throw new Error(`模型 ${modelName} 缺少字段配置`)
      await lowcodeCreateModel({
        ...modelDraft,
        id: null,
        domainId: domainInfo.id,
        modelCode,
        modelName,
        modelDesc: modelDraft.modelDesc || aiCreateDescription.value,
        status: modelDraft.status || 'ENABLED',
        tenantEnabled: modelDraft.tenantEnabled !== false,
        masterData: Boolean(modelDraft.masterData),
        modelSchema,
        syncDdl: false,
        confirmSyncDdl: false,
      })
    }
  }

  async function saveAiApps(appsToSave, models, domainContext) {
    const savedIds = []
    for (const appDraft of appsToSave) {
      const relatedModel = models.find(model => model.modelCode && model.modelCode === appDraft.objectCode) || models[0]
      const modelSchema = cloneSchema(appDraft.modelSchema || relatedModel?.modelSchema || aiCreateResult.value.modelSchema || {})
      const pageSchema = cloneSchema(appDraft.pageSchema || aiCreateResult.value.pageSchema || {})
      const domainInfo = resolveAiDomainInfo(appDraft.domainCode || modelSchema?.domain?.code, domainContext)
      applyDomainToModelSchema(modelSchema, domainInfo)
      const objectCode = appDraft.objectCode || relatedModel?.modelCode || modelSchema.object?.code || ''
      const objectName = appDraft.objectName || relatedModel?.modelName || modelSchema.object?.name || modelSchema.businessName || ''
      const res = await lowcodeSaveDraft({
        ...appDraft,
        id: null,
        domainId: domainInfo.id,
        domainCode: domainInfo.domainCode,
        domainName: domainInfo.domainName,
        objectCode,
        objectName,
        configKey: appDraft.configKey || buildImportedConfigKey(objectCode || 'ai_app'),
        appName: appDraft.appName || `${objectName || 'AI应用'}管理`,
        menuName: appDraft.menuName || appDraft.appName || `${objectName || 'AI应用'}管理`,
        menuParentId: appDraft.menuParentId ?? domainInfo.menuParentId ?? null,
        menuSort: appDraft.menuSort || 0,
        modelSchema,
        pageSchema,
      })
      if (res.data)
        savedIds.push(res.data)
    }
    return savedIds
  }

  function resolveAiDomainInfo(domainCode, domainContext) {
    if (domainCode && domainContext.domainMap.has(domainCode))
      return domainContext.domainMap.get(domainCode)
    return domainContext.fallbackDomain
  }

  function applyDomainToModelSchema(modelSchema, domainInfo) {
    if (!modelSchema.domain)
      modelSchema.domain = {}
    modelSchema.domain.id = domainInfo.id
    modelSchema.domain.code = domainInfo.domainCode
    modelSchema.domain.name = domainInfo.domainName
  }

  function layoutName(layoutType) {
    if (layoutType === 'tree-crud')
      return '左树右表'
    if (layoutType === 'master-detail-crud')
      return '主子表'
    return '标准单表'
  }

  async function exportApp(app) {
    try {
      const res = await lowcodeAppDetail(app.id)
      const detail = res.data || {}
      downloadJson({
        type: 'LOWCODE_APP_CONFIG',
        version: 1,
        exportedAt: new Date().toISOString(),
        app: {
          id: null,
          configKey: detail.configKey,
          appName: detail.appName,
          domainCode: detail.domainCode,
          domainName: detail.domainName,
          objectCode: detail.objectCode,
          objectName: detail.objectName,
          menuName: detail.menuName,
          menuParentId: detail.menuParentId,
          menuSort: detail.menuSort,
          modelSchema: cloneSchema(detail.modelSchema || {}),
          pageSchema: cloneSchema(detail.pageSchema || {}),
        },
      }, `${detail.configKey || 'app'}-app-config.json`)
    }
    catch (e) {
      window.$message?.error(e?.message || '导出应用配置失败')
    }
  }

  function triggerAppImport() {
    appImportInputRef.value?.click()
  }

  async function handleAppImportFile(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file)
      return
    try {
      const payload = JSON.parse(await file.text())
      const app = payload.app || payload
      if (!app?.modelSchema || !app?.pageSchema) {
        window.$message?.warning('应用配置文件格式不正确')
        return
      }
      const domain = selectedDomain.value
      if (!domain && !app.domainId && !app.domainCode) {
        window.$message?.warning('请先选择业务领域，再导入应用配置')
        return
      }
      const configKey = buildImportedConfigKey(app.configKey || app.modelSchema?.object?.code || 'app')
      const res = await lowcodeSaveDraft({
        id: null,
        domainId: domain?.id || app.domainId || null,
        domainCode: domain?.domainCode || app.domainCode || '',
        domainName: domain?.domainName || app.domainName || '',
        objectCode: app.objectCode || app.modelSchema?.object?.code || '',
        objectName: app.objectName || app.modelSchema?.object?.name || app.modelSchema?.businessName || '',
        configKey,
        appName: app.appName || app.modelSchema?.businessName || configKey,
        menuName: app.menuName || app.appName || app.modelSchema?.businessName || configKey,
        menuParentId: domain?.menuParentId || app.menuParentId || null,
        menuSort: app.menuSort || 0,
        modelSchema: cloneSchema(app.modelSchema),
        pageSchema: cloneSchema(app.pageSchema),
      })
      window.$message?.success('应用配置已导入')
      await refreshAll()
      if (res.data)
        openBuilder(res.data)
    }
    catch (e) {
      window.$message?.error(e?.message || '导入应用配置失败')
    }
  }

  function buildImportedConfigKey(sourceKey) {
    const suffix = new Date().toISOString().slice(11, 19).replace(/\D/g, '')
    return normalizeObjectCode(`${sourceKey || 'app'}_import_${suffix}`).slice(0, 64)
  }

  function flattenDomains(nodes, level = 0) {
    const result = []
    for (const node of nodes || []) {
      result.push({ ...node, level })
      if (node.children?.length)
        result.push(...flattenDomains(node.children, level + 1))
    }
    return result
  }

  function openDomainEditor(domain) {
    editingDomain.value = domain ? { ...domain } : null
    domainEditorVisible.value = true
  }

  function openChildDomainEditor(domain) {
    if (!domain?.id)
      return
    editingDomain.value = {
      parentId: domain.id,
      status: 'ENABLED',
      sort: 0,
    }
    domainEditorVisible.value = true
  }

  async function editDomain(domain) {
    if (!domain?.id)
      return
    try {
      const res = await lowcodeDomainDetail(domain.id)
      editingDomain.value = res.data || { ...domain }
      domainEditorVisible.value = true
    }
    catch (error) {
      window.$message?.error(error?.message || '加载业务领域详情失败')
    }
  }

  async function handleDomainSaved() {
    await refreshDomains()
    if (selectedDomainId.value)
      selectedDomain.value = findDomainById(domains.value, selectedDomainId.value) || selectedDomain.value
  }

  function deleteDomain(domain) {
    if (!domain?.id)
      return
    window.$dialog.warning({
      title: '确认删除领域',
      content: `确定删除业务领域“${domain.domainName}”吗？存在下级领域或低代码应用时后端会阻止删除。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await lowcodeDeleteDomain(domain.id)
          window.$message?.success('业务领域已删除')
          if (selectedDomainId.value === domain.id) {
            selectedDomainId.value = null
            selectedDomain.value = null
            pageNum.value = 1
          }
          await refreshAll()
        }
        catch (error) {
          window.$message?.error(error?.message || '业务领域删除失败')
        }
      },
    })
  }

  function findDomainById(nodes, id) {
    for (const node of nodes || []) {
      if (node.id === id)
        return node
      const found = findDomainById(node.children, id)
      if (found)
        return found
    }
    return null
  }

  function openMoveDomain(app) {
    movingApp.value = app
    moveVisible.value = true
  }

  function deleteApp(app) {
    window.$dialog.warning({
      title: '确认删除应用',
      content: `确定删除低代码应用“${app.appName || app.configKey}”吗？已发布菜单会同步删除，若菜单已被角色授权将无法删除。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await lowcodeDeleteApp(app.id)
          window.$message?.success('应用已删除')
          if (apps.value.length === 1 && pageNum.value > 1)
            pageNum.value -= 1
          await refreshAll()
        }
        catch (error) {
          window.$message?.error(error?.message || '应用删除失败')
        }
      },
    })
  }

  async function handleMoved() {
    moveVisible.value = false
    movingApp.value = null
    await refreshAll()
  }

  function statusLabel(status) {
    const item = dict.value.lowcode_app_publish_status?.find(d => d.value === status)
    return item?.label || '草稿'
  }

  __impl.loadDomains = loadDomains
  __impl.refreshDomains = refreshDomains
  __impl.selectDomain = selectDomain
  __impl.loadApps = loadApps
  __impl.openAiPreviewDetail = openAiPreviewDetail
  __impl.resolveAppModel = resolveAppModel
  __impl.buildApiRowsForTarget = buildApiRowsForTarget
  __impl.refreshAll = refreshAll
  __impl.clearPendingAppSearch = clearPendingAppSearch
  __impl.handleKeywordSearch = handleKeywordSearch
  __impl.handleSearch = handleSearch
  __impl.handlePageChange = handlePageChange
  __impl.handlePageSizeChange = handlePageSizeChange
  __impl.createApp = createApp
  __impl.openBuilder = openBuilder
  __impl.openRuntime = openRuntime
  __impl.openCodePreview = openCodePreview
  __impl.downloadAppCode = downloadAppCode
  __impl.getAppActionOptions = getAppActionOptions
  __impl.handleAppActionSelect = handleAppActionSelect
  __impl.openAiCreateApp = openAiCreateApp
  __impl.handleAiCreateVisibleUpdate = handleAiCreateVisibleUpdate
  __impl.closeAiCreateModal = closeAiCreateModal
  __impl.abortAiGeneration = abortAiGeneration
  __impl.generateAiAppDraft = generateAiAppDraft
  __impl.handleAiStreamEvent = handleAiStreamEvent
  __impl.loadAiProviderOptions = loadAiProviderOptions
  __impl.handleAiProviderChange = handleAiProviderChange
  __impl.loadAiModelOptions = loadAiModelOptions
  __impl.handleAiTargetDomainChange = handleAiTargetDomainChange
  __impl.loadDomainModelContext = loadDomainModelContext
  __impl.buildExistingModelContext = buildExistingModelContext
  __impl.buildDraftContext = buildDraftContext
  __impl.resetAiStreamState = resetAiStreamState
  __impl.handleAiStreamChunk = handleAiStreamChunk
  __impl.createInitialAiSteps = createInitialAiSteps
  __impl.stepStatusText = stepStatusText
  __impl.updateAiStep = updateAiStep
  __impl.applyAiResultSteps = applyAiResultSteps
  __impl.markPendingAiStepsCompleted = markPendingAiStepsCompleted
  __impl.markAiStepError = markAiStepError
  __impl.confirmAiAppDraft = confirmAiAppDraft
  __impl.ensureAiDomains = ensureAiDomains
  __impl.saveAiModels = saveAiModels
  __impl.saveAiApps = saveAiApps
  __impl.resolveAiDomainInfo = resolveAiDomainInfo
  __impl.applyDomainToModelSchema = applyDomainToModelSchema
  __impl.layoutName = layoutName
  __impl.exportApp = exportApp
  __impl.triggerAppImport = triggerAppImport
  __impl.handleAppImportFile = handleAppImportFile
  __impl.buildImportedConfigKey = buildImportedConfigKey
  __impl.flattenDomains = flattenDomains
  __impl.openDomainEditor = openDomainEditor
  __impl.openChildDomainEditor = openChildDomainEditor
  __impl.editDomain = editDomain
  __impl.handleDomainSaved = handleDomainSaved
  __impl.deleteDomain = deleteDomain
  __impl.findDomainById = findDomainById
  __impl.openMoveDomain = openMoveDomain
  __impl.deleteApp = deleteApp
  __impl.handleMoved = handleMoved
  __impl.statusLabel = statusLabel

  return {
    __impl, mut, abortAiGeneration, appInitial, applyAiResultSteps, applyDomainToModelSchema, buildApiRowsForTarget, buildDraftContext,
    buildExistingModelContext, buildImportedConfigKey, clearPendingAppSearch, closeAiCreateModal, confirmAiAppDraft, createApp, createInitialAiSteps, deleteApp,
    deleteDomain, downloadAppCode, downloadBlob, downloadJson, editDomain, ensureAiDomains, exportApp, findDomainById,
    flattenDomains, formatTime, generateAiAppDraft, getAppActionOptions, handleAiCreateVisibleUpdate, handleAiProviderChange, handleAiStreamChunk, handleAiStreamEvent,
    handleAiTargetDomainChange, handleAppActionSelect, handleAppImportFile, handleDomainSaved, handleKeywordSearch, handleMoved, handlePageChange, handlePageSizeChange,
    handleSearch, layoutName, loadAiModelOptions, loadAiProviderOptions, loadApps, loadDomainModelContext, loadDomains, markAiStepError,
    markPendingAiStepsCompleted, openAiCreateApp, openAiPreviewDetail, openBuilder, openChildDomainEditor, openCodePreview, openDomainEditor, openMoveDomain,
    openRuntime, refreshAll, refreshDomains, resetAiStreamState, resolveAiDomainInfo, resolveAppModel, saveAiApps, saveAiModels,
    selectDomain, statusLabel, stepStatusText, triggerAppImport, updateAiStep, router, domainLoading, loading,
    domains, selectedDomainId, selectedDomain, apps, total, pageNum, pageSize, keyword,
    domainKeyword, publishStatus, domainEditorVisible, editingDomain, moveVisible, movingApp, appImportInputRef, codePreviewVisible,
    codePreviewApp, downloadingCodeId, aiCreateVisible, aiCreateLoading, aiConfirmSaving, aiCreateDescription, aiCreateResult, aiStreamController,
    aiSteps, aiTargetDomainId, aiRawContent, aiReasoningContent, aiIsReasoningPhase, aiReasoningStartTime, aiReasoningEndTime, aiProviderId,
    aiModelId, aiProviderOptions, aiModelOptions, aiProviderLoading, aiModelLoading, aiIncludeDomainModels, domainModelContext, domainModelLoading,
    activePreviewModelCode, aiPreviewDetailVisible, aiPreviewDetailType, aiAppendInstruction, statusOptions, flatDomains, domainSelectOptions, aiTargetDomain,
    aiReasoningTime, aiGeneratedDomains, aiGeneratedModels, aiGeneratedApps, aiDecisions, aiNotes, previewModelOptions, activePreviewModel,
    activePreviewFields, aiPrimaryDomainLabel, aiPrimaryModelName, aiRelationCount, aiModelRelationRows, aiApiDetailRows, aiApiCount, aiCompletionPercent,
    aiValidationStatus, aiPageStructureRows, aiPreviewFields, aiApiPreviewRows, aiPageDetailRows, aiPreviewDetailTitle, modelPreviewColumns, appPreviewColumns,
    apiPreviewColumns, pageTitle, pageSubtitle, appScopeText,
  }
}
