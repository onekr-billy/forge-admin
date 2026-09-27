/** ExtensionEditorDrawer.vue setup part 1. */
import { useMessage } from 'naive-ui'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { businessObjectActions, businessObjectFields } from '@/api/business-app'
import {
  acquireBusinessExtensionLock,
  createBusinessExtension,
  renewBusinessExtensionLock,
  saveBusinessExtensionDraft,
  testBusinessExtension,
  updateBusinessExtension,
  updateBusinessExtensionStatus,
  validateBusinessExtension,
} from '@/api/business-extension'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import { processScopedCss } from '@/components/lowcode-extension/css/scoped-css'
import ScopedCssPreview from '@/components/lowcode-extension/css/ScopedCssPreview.vue'
import ExtensionCodeWorkbench from '@/components/lowcode-extension/ExtensionCodeWorkbench.vue'
import { validateClientScript } from '@/components/lowcode-extension/js/extension-context-api'
import ExtensionSandboxHost from '@/components/lowcode-extension/js/ExtensionSandboxHost.vue'
import {
  extensionFieldOptions,
  extensionFieldValueKind,
  extensionPageOptions,
  findExtensionField,
  operatorNeedsValue,
  preferredExtensionObjectId,
  validateExtensionVisualRule,
} from '../extension-visual-rule'
import ExtensionHookMatrix from '../ExtensionHookMatrix.vue'
export function applyExtensionEditorDrawerPart1(props, emit) {
  const __impl = {}
  const mut = {
    renewTimer: null,
    clientTestFieldSeed: 0,
    clientContextCatalogRequestId: 0,
  }
  const message = useMessage()
  const formRef = ref(null)
  const sandboxRef = ref(null)
  const saving = ref(false)
  const testing = ref(false)
  const enabling = ref(false)
  const showTypeGuide = ref(true)
  const governanceOpen = ref(false)
  const handlerCode = ref(null)
  const serverTestInput = ref('{}')
  const clientRecordId = ref('1')
  const clientTestFields = ref([])
  const clientAllowedActions = ref([])
  const clientFieldCatalog = ref([])
  const clientActionCatalog = ref([])
  const clientContextCatalogLoading = ref(false)
  const clientContextCatalogError = ref('')
  const testStage = ref('IDLE')
  const testFailedStage = ref('')
  const testSummary = ref('点击底部“保存并测试”开始')
  const completedTestStages = ref([])
  const visualRule = reactive({ match: 'ALL', conditions: [], actions: [] })
  const form = reactive(defaultForm())

  const extensionTypeChoices = [
    {
      value: 'VISUAL_RULE',
      title: '业务规则',
      description: '通过条件和动作完成校验、赋值和提示。',
      scene: '适合实施人员，无需写代码',
    },
    {
      value: 'CLIENT_JS',
      title: '页面 JS 增强',
      description: '处理字段联动、页面提示和受控页面动作。',
      scene: '在独立沙箱运行，不访问 DOM 和网络',
    },
    {
      value: 'SCOPED_CSS',
      title: '页面 CSS 增强',
      description: '调整指定应用页面内的局部样式。',
      scene: '选择器自动限制作用范围',
    },
    {
      value: 'SERVER_BINDING',
      title: 'Java 服务增强',
      description: '调用后端已开发、部署并注册的业务处理器。',
      scene: '适合复杂校验、计算和系统集成',
    },
  ]

  const clientValueTypeOptions = [
    { label: '文本', value: 'TEXT' },
    { label: '数字', value: 'NUMBER' },
    { label: '是 / 否', value: 'BOOLEAN' },
    { label: '空值', value: 'NULL' },
    { label: '对象 / 数组', value: 'JSON' },
  ]
  const clientBooleanOptions = [
    { label: '是（true）', value: 'true' },
    { label: '否（false）', value: 'false' },
  ]
  const booleanRuleOptions = [
    { label: '是', value: 'true' },
    { label: '否', value: 'false' },
  ]
  const clientSensitiveKeyPattern = /token|secret|password|cookie|authorization|api[_-]?key|session/i
  const clientReservedKeys = new Set(['__proto__', 'prototype', 'constructor'])
  const clientHookScenes = {
    PAGE_INIT: {
      title: '页面刚打开',
      description: '用下面这条业务记录模拟页面初始化，适合检查默认值和首次提示。',
    },
    FORM_CHANGE: {
      title: '用户修改了表单字段',
      description: '填写字段变化后的值，检查联动计算、自动赋值和即时提示。',
    },
    BEFORE_SUBMIT: {
      title: '用户点击提交',
      description: '填写即将提交的表单数据，检查业务校验是否按预期通过或阻断。',
    },
    AFTER_SUBMIT: {
      title: '表单已经保存成功',
      description: '填写保存后的记录数据，检查成功提示和后续页面动作。',
    },
    ROW_ACTION: {
      title: '用户操作了一条列表记录',
      description: '用下面这条业务记录模拟当前行，检查行操作后的字段和页面动作。',
    },
  }

  const isEdit = computed(() => Boolean(form.id))
  const statusLabel = computed(() => {
    if (form.status === 'ENABLED')
      return '运行中'
    if (form.status === 'DISABLED')
      return '已停用'
    if (form.status === 'TESTED' || testStage.value === 'PASSED')
      return '已测草稿'
    return '草稿'
  })
  const statusTone = computed(() => {
    if (form.status === 'ENABLED')
      return 'success'
    if (form.status === 'DISABLED')
      return 'warning'
    if (form.status === 'TESTED' || testStage.value === 'PASSED')
      return 'info'
    return 'default'
  })
  const objectOptions = computed(() => props.objects.map(item => ({
    label: `${item.objectName || item.objectCode} · ${item.objectCode}`,
    value: String(item.objectId ?? item.id),
  })))
  const entryOptions = computed(() => props.entries.map(item => ({
    label: entryDisplayName(item),
    value: item.id,
  })))
  const scopedCssPageOptions = computed(() => extensionPageOptions(props.pages, form.scopeKey))
  const scopedCssPageLabel = computed(() => {
    if (!form.scopeKey || form.scopeKey === 'default')
      return '全部页面'
    const page = props.pages.find(item => String(item.id) === String(form.scopeKey))
    return page?.title || page?.name || '当前页面'
  })
  const handlerOptions = computed(() => props.handlers.map(item => ({
    label: `${item.handlerName} · ${item.handlerCode}`,
    value: item.handlerCode,
  })))
  const selectedHandler = computed(() => props.handlers.find(item => item.handlerCode === handlerCode.value))
  const selectedExtensionType = computed(() => extensionTypeChoices.find(item => item.value === form.extensionType))
  const contentStageHint = computed(() => ({
    VISUAL_RULE: '用条件和动作完成校验、赋值与提示',
    CLIENT_JS: '在沙箱中编写页面脚本',
    SCOPED_CSS: '编写限定在页面内的样式',
    SERVER_BINDING: '绑定已注册的 Java 处理器',
  }[form.extensionType] || '选择类型后在此配置具体内容'))
  const contentStageReady = computed(() => {
    if (form.extensionType === 'VISUAL_RULE')
      return visualRule.actions.length > 0
    if (form.extensionType === 'SERVER_BINDING')
      return Boolean(handlerCode.value)
    return Boolean(String(form.content || '').trim())
  })
  const hookSummaryLabel = computed(() => ({
    PAGE_INIT: '页面打开',
    FORM_CHANGE: '字段变更',
    BEFORE_SUBMIT: '提交前',
    AFTER_SUBMIT: '提交后',
    ROW_ACTION: '行操作',
    BEFORE_CREATE: '新增前',
    AFTER_CREATE: '新增后',
    BEFORE_UPDATE: '修改前',
    AFTER_UPDATE: '修改后',
    BEFORE_DELETE: '删除前',
    AFTER_DELETE: '删除后',
  }[form.hookCode] || form.hookCode || '未选择'))
  const objectSummaryLabel = computed(() => {
    const object = props.objects.find(item => String(item.objectId ?? item.id) === String(form.objectId || ''))
    return object?.objectName || object?.objectCode || (form.objectId ? String(form.objectId) : '整个应用')
  })
  const scopeSummaryLabel = computed(() => {
    if (form.extensionType === 'SCOPED_CSS' && form.scopeKey) {
      const page = props.pages.find(item => String(item.id) === String(form.scopeKey))
      return page?.title || form.scopeKey
    }
    if (form.entryId) {
      const entry = props.entries.find(item => String(item.id) === String(form.entryId))
      return entry ? entryDisplayName(entry) : '指定入口'
    }
    if (form.objectId)
      return '业务对象'
    return '整个应用'
  })
  const selectedEntryLabel = computed(() => {
    const entry = props.entries.find(item => item.id === form.entryId)
    return entry ? entryDisplayName(entry) : '当前页面'
  })
  const clientFieldOptions = computed(() => clientFieldCatalog.value.map(field => ({
    label: `${field.fieldName || field.fieldCode}（${field.fieldCode}）`,
    value: field.fieldCode,
  })).filter(item => item.value))
  const clientActionOptions = computed(() => clientActionCatalog.value.map(action => ({
    label: `${action.actionName || action.actionCode}（${action.actionCode}）`,
    value: action.actionCode,
  })).filter(item => item.value))
  const clientContextScene = computed(() => clientHookScenes[form.hookCode] || {
    title: '当前业务触发场景',
    description: '填写脚本本次运行需要读取的业务数据。',
  })
  const clientDetectedSummary = computed(() => {
    const detected = detectClientScriptBindings(form.content)
    if (!detected.fields.length && !detected.actions.length)
      return '脚本当前不依赖业务字段或页面动作'
    const parts = []
    if (detected.fields.length)
      parts.push(`自动识别 ${detected.fields.length} 个字段`)
    if (detected.actions.length)
      parts.push(`自动识别 ${detected.actions.length} 个页面动作`)
    return parts.join('，')
  })
  const clientContextPreview = computed(() => {
    try {
      return JSON.stringify(buildClientSandboxContext().context, null, 2)
    }
    catch (error) {
      return `请先完善测试数据：${error instanceof Error ? error.message : '测试数据格式不正确'}`
    }
  })
  const testSteps = computed(() => {
    const steps = [
      { code: 'SAVE', label: '保存草稿' },
      { code: 'VALIDATE', label: '安全校验' },
    ]
    if (form.extensionType === 'CLIENT_JS')
      steps.push({ code: 'SANDBOX', label: '沙箱执行' })
    steps.push({ code: 'SERVER', label: '后端确认' })
    return steps.map((step, index) => ({ ...step, order: index + 1 }))
  })
  const allowedHooksForType = computed(() => {
    if (form.extensionType === 'SCOPED_CSS')
      return ['PAGE_INIT']
    if (form.extensionType === 'CLIENT_JS')
      return ['PAGE_INIT', 'FORM_CHANGE', 'BEFORE_SUBMIT', 'AFTER_SUBMIT', 'ROW_ACTION']
    if (form.extensionType === 'SERVER_BINDING' && selectedHandler.value)
      return [...(selectedHandler.value.allowedHooks || [])]
    return null
  })
  const cssResult = computed(() => {
    if (form.extensionType !== 'SCOPED_CSS' || !form.content.trim())
      return null
    try {
      return processScopedCss(form.content, {
        applicationCode: props.application?.applicationCode || '',
        pageCode: form.scopeKey || 'default',
      })
    }
    catch {
      return null
    }
  })

  function entryDisplayName(item = {}) {
    const appName = String(item.appName || '').trim()
    const appCode = String(item.appCode || '').trim()
    if (appName && appName !== appCode && !/^[A-Z][A-Z0-9_]*$/.test(appName))
      return appName
    return item.objectName ? `${item.objectName}入口` : '业务访问入口'
  }

  function riskLevelLabel(value) {
    return {
      LOW: '低风险',
      MEDIUM: '中风险',
      HIGH: '高风险',
    }[value] || '未声明'
  }
  const cssError = computed(() => {
    if (form.extensionType !== 'SCOPED_CSS' || !form.content.trim())
      return ''
    try {
      processScopedCss(form.content, {
        applicationCode: props.application?.applicationCode || '',
        pageCode: form.scopeKey || 'default',
      })
      return ''
    }
    catch (error) {
      return error.message
    }
  })

  const rules = {
    extensionName: { required: true, message: '请输入增强名称', trigger: ['blur', 'input'] },
    extensionCode: {
      required: true,
      pattern: /^[a-z]\w{1,63}$/i,
      message: '字母开头，仅含字母、数字和下划线，2-64字符',
      trigger: ['blur', 'input'],
    },
    extensionType: { required: true, message: '请选择增强类型', trigger: 'change' },
    hookCode: { required: true, message: '请选择执行钩子', trigger: 'change' },
    failurePolicy: { required: true, message: '请选择失败策略', trigger: 'change' },
    riskLevel: { required: true, message: '请选择风险级别', trigger: 'change' },
  }

  watch(() => props.show, async (visible) => {
    if (!visible) {
      mut.clientContextCatalogRequestId += 1
      clearRenewTimer()
      return
    }
    hydrateForm()
    showTypeGuide.value = !isEdit.value && !props.createDefaults?.extensionType
    governanceOpen.value = Boolean(props.extension?.changeSummary || props.extension?.remark)
    if (!form.objectId)
      form.objectId = preferredExtensionObjectId(props.objects)
    await loadClientContextCatalog(form.objectId || resolveClientContextObjectId())
    if (!props.show)
      return
    startRenewTimer()
    if (props.startWithTest) {
      await nextTick()
      message.info('请确认自动识别的测试字段值，然后执行“保存并测试”')
    }
  })

  watch(() => form.objectId, (objectId) => {
    loadClientContextCatalog(objectId || resolveClientContextObjectId())
    if (!objectId)
      return
    const allowedEntryIds = new Set(props.entries
      .filter(item => item.objectCode === props.objects.find(object => object.objectId === objectId)?.objectCode)
      .map(item => item.id))
    if (form.entryId && !allowedEntryIds.has(form.entryId))
      form.entryId = null
  })

  watch(() => form.entryId, () => {
    if (!form.objectId)
      loadClientContextCatalog(resolveClientContextObjectId())
  })

  watch(() => form.extensionType, (type) => {
    const allowedHooks = allowedHooksForType.value
    if (type === 'SERVER_BINDING' && !selectedHandler.value)
      return
    if (allowedHooks?.length && !allowedHooks.includes(form.hookCode))
      form.hookCode = allowedHooks[0]
  })

  watch(handlerCode, () => {
    if (form.extensionType !== 'SERVER_BINDING')
      return
    serverTestInput.value = buildHandlerTestInput(selectedHandler.value)
    const allowedHooks = allowedHooksForType.value
    if (allowedHooks?.length && !allowedHooks.includes(form.hookCode))
      form.hookCode = allowedHooks[0]
  })

  watch(() => [form.content, form.hookCode, handlerCode.value], () => {
    if (['PASSED', 'FAILED'].includes(testStage.value))
      resetTestStatus()
    if (form.extensionType === 'CLIENT_JS')
      syncClientContextFromScript(true)
  })

  watch([clientRecordId, clientTestFields, clientAllowedActions], () => {
    if (['PASSED', 'FAILED'].includes(testStage.value))
      resetTestStatus()
  }, { deep: true })

  watch(visualRule, () => {
    if (['PASSED', 'FAILED'].includes(testStage.value))
      resetTestStatus()
  }, { deep: true })

  onBeforeUnmount(clearRenewTimer)

  function selectExtensionType(type) {
    form.extensionType = type
    showTypeGuide.value = false
  }

  function defaultForm() {
    return {
      id: null,
      applicationId: null,
      objectId: null,
      entryId: null,
      extensionName: '',
      extensionCode: '',
      extensionType: 'VISUAL_RULE',
      hookCode: 'BEFORE_SUBMIT',
      scopeType: 'APPLICATION',
      scopeKey: '',
      sortOrder: 0,
      failurePolicy: 'BLOCK',
      riskLevel: 'MEDIUM',
      status: 'DRAFT',
      content: '',
      processedContent: '',
      configJson: '{}',
      changeSummary: '',
      lockToken: '',
      lockExpireTime: '',
      remark: '',
    }
  }

  function hydrateForm() {
    resetTestStatus()
    const defaults = (!props.extension && props.createDefaults && typeof props.createDefaults === 'object')
      ? { ...props.createDefaults }
      : {}
    Object.assign(form, defaultForm(), defaults, props.extension || {}, {
      applicationId: props.application?.id,
      objectId: props.extension
        ? (props.extension.objectId == null ? null : String(props.extension.objectId))
        : (defaults.objectId == null ? null : String(defaults.objectId)),
      entryId: props.extension
        ? (props.extension.entryId == null ? null : String(props.extension.entryId))
        : (defaults.entryId == null ? null : String(defaults.entryId)),
    })
    handlerCode.value = null
    serverTestInput.value = '{}'
    clientRecordId.value = '1'
    clientTestFields.value = []
    clientAllowedActions.value = []
    clientFieldCatalog.value = []
    clientActionCatalog.value = []
    clientContextCatalogError.value = ''
    visualRule.match = 'ALL'
    visualRule.conditions.splice(0)
    visualRule.actions.splice(0)

    if (form.extensionType === 'VISUAL_RULE' && form.content) {
      try {
        const parsed = JSON.parse(form.content)
        visualRule.match = parsed.match === 'ANY' ? 'ANY' : 'ALL'
        visualRule.conditions.push(...normalizeVisualRuleRows(parsed.conditions, {
          field: '',
          operator: 'EQ',
          value: '',
        }))
        visualRule.actions.push(...normalizeVisualRuleRows(parsed.actions, {
          actionType: 'SHOW_MESSAGE',
          message: '',
        }))
      }
      catch {
        // 后端校验会保留错误内容；编辑器以空结构让用户修复。
      }
    }
    if (form.extensionType === 'SERVER_BINDING') {
      try {
        handlerCode.value = JSON.parse(form.configJson || '{}').handlerCode || null
      }
      catch {
        handlerCode.value = null
      }
    }
    if (form.extensionType === 'CLIENT_JS')
      syncClientContextFromScript(true)
  }

  function normalizeVisualRuleRows(rows, fallback) {
    if (!Array.isArray(rows))
      return []
    return rows
      .filter(item => item && typeof item === 'object' && !Array.isArray(item))
      .map(item => ({ ...fallback, ...item }))
  }

  function addCondition() {
    visualRule.conditions.push({ field: '', operator: 'EQ', value: '' })
  }

  function addAction() {
    visualRule.actions.push({ actionType: 'SHOW_MESSAGE', message: '' })
  }

  function applyCodeExampleContext(example) {
    if (!example || form.extensionType !== 'CLIENT_JS')
      return
    const context = example.testContext || {}
    const record = context.record && typeof context.record === 'object' ? context.record : {}
    const fieldCodes = [...new Set([
      ...(example.testFields || []),
      ...Object.keys(record).filter(fieldCode => fieldCode !== 'id'),
    ])]
    clientRecordId.value = String(record.id ?? context.recordId ?? 1)
    clientTestFields.value = fieldCodes.map(fieldCode => createClientTestField(
      fieldCode,
      Object.prototype.hasOwnProperty.call(record, fieldCode) ? record[fieldCode] : sampleClientFieldValue(fieldCode),
    ))
    clientAllowedActions.value = [...new Set(context.allowedActions || [])]
  }

  function resolveClientContextObjectId() {
    const entry = props.entries.find(item => String(item.id) === String(form.entryId))
    if (!entry)
      return null
    if (entry.objectId)
      return entry.objectId
    const object = props.objects.find(item => item.objectCode === entry.objectCode)
    return object?.objectId || null
  }

  async function loadClientContextCatalog(objectId) {
    const requestId = ++mut.clientContextCatalogRequestId
    if (!objectId) {
      clientFieldCatalog.value = []
      clientActionCatalog.value = []
      clientContextCatalogLoading.value = false
      clientContextCatalogError.value = ''
      return
    }

    clientContextCatalogLoading.value = true
    clientContextCatalogError.value = ''
    try {
      const [fieldResult, actionResult] = await Promise.allSettled([
        businessObjectFields(objectId),
        businessObjectActions(objectId),
      ])
      if (requestId !== mut.clientContextCatalogRequestId)
        return
      clientFieldCatalog.value = fieldResult.status === 'fulfilled'
        ? (fieldResult.value.data || []).filter(field => field?.fieldCode)
        : []
      clientActionCatalog.value = actionResult.status === 'fulfilled'
        ? (actionResult.value.data || []).filter(action => action?.actionCode && String(action.status ?? '1') !== '0')
        : []
      if (fieldResult.status === 'rejected')
        clientContextCatalogError.value = fieldResult.reason?.message || '业务字段加载失败，请重试'
      refreshClientFieldSamples()
    }
    finally {
      if (requestId === mut.clientContextCatalogRequestId)
        clientContextCatalogLoading.value = false
    }
  }

  function visualRuleFieldOptions(currentValue) {
    return extensionFieldOptions(clientFieldCatalog.value, currentValue)
  }

  function visualRuleWritableFieldOptions(currentValue) {
    return extensionFieldOptions(clientFieldCatalog.value, currentValue, { writable: true })
  }

  function visualRuleActionOptions(currentValue) {
    const options = [...clientActionOptions.value]
    const value = String(currentValue || '').trim()
    if (value && !options.some(item => item.value === value)) {
      options.unshift({
        label: `${value}（动作已失效，请重新选择）`,
        value,
        invalid: true,
      })
    }
    return options
  }

  function conditionField(condition) {
    return findExtensionField(clientFieldCatalog.value, condition?.field)
  }

  function actionField(action) {
    return findExtensionField(clientFieldCatalog.value, action?.field)
  }

  function conditionValueKind(condition) {
    return extensionFieldValueKind(conditionField(condition))
  }

  function actionValueKind(action) {
    return extensionFieldValueKind(actionField(action))
  }

  function syncClientContextFromScript(silent = true) {
    if (form.extensionType !== 'CLIENT_JS')
      return
    const detected = detectClientScriptBindings(form.content)
    const existingFields = new Set(clientTestFields.value.map(item => item.fieldCode).filter(Boolean))
    detected.fields.forEach((fieldCode) => {
      if (!existingFields.has(fieldCode)) {
        clientTestFields.value.push(createClientTestField(fieldCode, sampleClientFieldValue(fieldCode)))
        existingFields.add(fieldCode)
      }
    })
    clientAllowedActions.value = [...detected.actions]

    if (!silent) {
      if (!detected.fields.length && !detected.actions.length)
        message.info('当前脚本没有读取业务字段或触发页面动作，可以直接测试')
      else
        message.success(`已识别 ${detected.fields.length} 个字段、${detected.actions.length} 个页面动作`)
    }
  }

  function detectClientScriptBindings(source) {
    const fields = new Set()
    const actions = new Set()
    const script = String(source || '')
    const fieldPattern = /\b(?:readField|setField)\s*\(\s*(['"])([a-z]\w{0,63})\1/g
    const actionPattern = /\btriggerAction\s*\(\s*(['"])([a-z]\w{0,63})\1/g
    let match = fieldPattern.exec(script)
    while (match) {
      fields.add(match[2])
      match = fieldPattern.exec(script)
    }
    match = actionPattern.exec(script)
    while (match) {
      actions.add(match[2])
      match = actionPattern.exec(script)
    }
    return { fields: [...fields], actions: [...actions] }
  }

  function addClientTestField(fieldCode = '', value = '') {
    clientTestFields.value.push(createClientTestField(fieldCode, value))
  }

  function handleClientTestFieldChange(field, fieldCode) {
    field.fieldCode = fieldCode
    Object.assign(field, normalizeClientTestValue(sampleClientFieldValue(fieldCode)))
  }

  function handleClientValueTypeChange(field, valueType) {
    field.valueType = valueType
    if (valueType === 'NULL')
      field.value = ''
    else if (valueType === 'BOOLEAN')
      field.value = 'true'
    else if (valueType === 'NUMBER')
      field.value = Number.isFinite(Number(field.value)) ? String(Number(field.value)) : '1'
    else if (valueType === 'JSON')
      field.value = '{}'
    else if (valueType === 'TEXT' && typeof field.value !== 'string')
      field.value = String(field.value ?? '')
  }

  function createClientTestField(fieldCode, value) {
    const normalized = normalizeClientTestValue(value)
    return {
      key: `test-field-${++mut.clientTestFieldSeed}`,
      fieldCode,
      valueType: normalized.valueType,
      value: normalized.value,
    }
  }

  function normalizeClientTestValue(value) {
    if (value === null)
      return { valueType: 'NULL', value: '' }
    if (typeof value === 'number')
      return { valueType: 'NUMBER', value: String(value) }
    if (typeof value === 'boolean')
      return { valueType: 'BOOLEAN', value: String(value) }
    if (value && typeof value === 'object')
      return { valueType: 'JSON', value: JSON.stringify(value) }
    return { valueType: 'TEXT', value: String(value ?? '') }
  }

  function sampleClientFieldValue(fieldCode) {
    const field = clientFieldCatalog.value.find(item => item.fieldCode === fieldCode)
    const fieldType = String(field?.fieldType || field?.dataType || '').toUpperCase()
    if (/BOOLEAN|BOOL/.test(fieldType))
      return true
    if (/INT|LONG|NUMBER|DECIMAL|MONEY|FLOAT|DOUBLE/.test(fieldType))
      return 1
    if (/ARRAY|LIST|MULTI/.test(fieldType))
      return []
    if (/JSON|OBJECT|MAP/.test(fieldType))
      return {}
    if (/DATETIME|DATE_TIME|TIMESTAMP/.test(fieldType))
      return `${new Date().toISOString().slice(0, 10)} 09:00:00`
    if (/DATE/.test(fieldType))
      return new Date().toISOString().slice(0, 10)
    return '示例值'
  }

  function refreshClientFieldSamples() {
    clientTestFields.value.forEach((field) => {
      if (field.valueType !== 'TEXT' || field.value !== '示例值')
        return
      Object.assign(field, normalizeClientTestValue(sampleClientFieldValue(field.fieldCode)))
    })
  }

  function applyClientValuePreset(preset) {
    if (!clientTestFields.value.length) {
      message.info('当前没有需要填写的业务字段')
      return
    }
    clientTestFields.value = clientTestFields.value.map((field) => {
      const value = preset === 'EMPTY' ? null : sampleClientFieldValue(field.fieldCode)
      return createClientTestField(field.fieldCode, value)
    })
  }

  function clientValuePlaceholder(valueType) {
    return {
      TEXT: '例如：草稿',
      NUMBER: '例如：68.5',
      JSON: '例如：{"name":"示例"} 或 [1,2]',
    }[valueType] || '请输入本次测试值'
  }

  function buildClientSandboxContext() {
    const record = {}
    const recordId = normalizeClientRecordId(clientRecordId.value)
    if (recordId !== null)
      record.id = recordId

    const allowedFields = []
    const fieldCodeSet = new Set()
    clientTestFields.value.forEach((field, index) => {
      const fieldCode = String(field.fieldCode || '').trim()
      if (!/^[a-z]\w{0,63}$/i.test(fieldCode) || clientReservedKeys.has(fieldCode))
        throw new Error(`第 ${index + 1} 行业务字段编码格式不正确`)
      if (clientSensitiveKeyPattern.test(fieldCode))
        throw new Error(`字段 ${fieldCode} 属于敏感字段，沙箱不会注入该数据`)
      if (fieldCodeSet.has(fieldCode))
        throw new Error(`业务字段 ${fieldCode} 重复，请只保留一行`)
      fieldCodeSet.add(fieldCode)
      allowedFields.push(fieldCode)
      record[fieldCode] = parseClientTestFieldValue(field)
    })

    const allowedActions = [...new Set(clientAllowedActions.value.map((actionCode) => {
      const normalized = String(actionCode || '').trim()
      if (!/^[a-z]\w{0,63}$/i.test(normalized) || clientReservedKeys.has(normalized))
        throw new Error(`页面动作编码 ${normalized || '为空'} 格式不正确`)
      return normalized
    }))]
    return {
      context: { record, allowedActions },
      allowedFields,
    }
  }

  function normalizeClientRecordId(value) {
    const normalized = String(value ?? '').trim()
    if (!normalized)
      return null
    if (/^-?\d+$/.test(normalized) && normalized.length < 16)
      return Number(normalized)
    return normalized
  }

  function parseClientTestFieldValue(field) {
    if (field.valueType === 'NULL')
      return null
    if (field.valueType === 'BOOLEAN')
      return field.value === true || String(field.value) === 'true'
    if (field.valueType === 'NUMBER') {
      if (String(field.value ?? '').trim() === '')
        throw new Error(`字段 ${field.fieldCode} 的测试值不能为空；如需测试空值请选择“空值”类型`)
      const value = Number(field.value)
      if (!Number.isFinite(value))
        throw new Error(`字段 ${field.fieldCode} 的测试值必须是数字`)
      return value
    }
    if (field.valueType === 'JSON') {
      try {
        const value = JSON.parse(String(field.value || ''))
        if (!value || typeof value !== 'object')
          throw new Error('JSON 值不是对象或数组')
        return value
      }
      catch {
        throw new Error(`字段 ${field.fieldCode} 的测试值必须是合法对象或数组 JSON`)
      }
    }
    return String(field.value ?? '')
  }

  async function saveCurrent(showSuccess = true) {
    await formRef.value?.validate()
    const versionPayload = buildVersionPayload()
    saving.value = true
    try {
      if (!form.id) {
        const response = await createBusinessExtension({
          ...metadataPayload(),
          ...versionPayload,
        })
        form.id = response.data
        const lockResponse = await acquireBusinessExtensionLock(form.id)
        form.lockToken = lockResponse.data?.lockToken || ''
        form.lockExpireTime = lockResponse.data?.expireTime || ''
        startRenewTimer()
        if (showSuccess)
          message.success('增强 v1 草稿已创建')
      }
      else {
        await updateBusinessExtension({
          ...metadataPayload(),
          id: form.id,
          lockToken: form.lockToken,
        })
        await saveBusinessExtensionDraft(form.id, {
          ...versionPayload,
          lockToken: form.lockToken,
        })
        if (showSuccess)
          message.success('已追加新的增强草稿版本')
      }
      form.status = 'DRAFT'
      emit('saved')
      return form.id
    }
    finally {
      saving.value = false
    }
  }

  async function saveAndTest() {
    testing.value = true
    completedTestStages.value = []
    testFailedStage.value = ''
    try {
      beginTestStage('SAVE', '正在保存当前扩展草稿')
      const id = await saveCurrent(false)
      completeTestStage('SAVE')

      beginTestStage('VALIDATE', '正在执行扩展内容与安全边界校验')
      const validationResponse = await validateBusinessExtension(id)
      if (!validationResponse.data?.passed) {
        const summary = validationResponse.data?.summary || '扩展校验未通过'
        failTestStage('VALIDATE', summary)
        message.warning(summary)
        return
      }
      completeTestStage('VALIDATE')

      const testPayload = { input: {} }
      if (form.extensionType === 'CLIENT_JS') {
        beginTestStage('SANDBOX', '正在独立 Worker 中执行客户端脚本')
        const { context, allowedFields } = buildClientSandboxContext()
        if (!sandboxRef.value)
          throw new Error('客户端脚本沙箱尚未初始化，请关闭抽屉后重新进入')
        await sandboxRef.value.execute(form.content, context, allowedFields)
        testPayload.clientSandboxResult = 'PASSED'
        completeTestStage('SANDBOX')
      }
      if (form.extensionType === 'SERVER_BINDING')
        testPayload.input = parseJson(serverTestInput.value, '服务端测试输入')

      beginTestStage('SERVER', '正在由后端确认测试结果和增强版本状态')
      const testResponse = await testBusinessExtension(id, testPayload)
      if (!testResponse.data?.passed) {
        const summary = testResponse.data?.summary || '增强测试未通过'
        failTestStage('SERVER', summary)
        message.warning(summary)
        return
      }
      completeTestStage('SERVER')
      testStage.value = 'PASSED'
      testSummary.value = '当前草稿测试通过'
      form.status = 'TESTED'
      message.success('增强测试通过，请点击“启用当前版本”使规则生效')
      emit('saved')
    }
    catch (error) {
      const summary = error instanceof Error ? error.message : '增强测试执行失败'
      failTestStage(testStage.value, summary)
      message.error(summary)
    }
    finally {
      testing.value = false
    }
  }

  async function enableCurrentVersion() {
    if (!form.id || testStage.value !== 'PASSED')
      return
    enabling.value = true
    try {
      await updateBusinessExtensionStatus(form.id, 'ENABLED')
      form.status = 'ENABLED'
      message.success('当前增强版本已启用，工作台预览刷新后生效；正式应用需重新发布')
      emit('saved')
    }
    catch (error) {
      message.error(error instanceof Error ? error.message : '启用增强失败')
    }
    finally {
      enabling.value = false
    }
  }

  function beginTestStage(stage, summary) {
    testStage.value = stage
    testSummary.value = summary
  }

  function completeTestStage(stage) {
    if (!completedTestStages.value.includes(stage))
      completedTestStages.value = [...completedTestStages.value, stage]
  }

  function failTestStage(stage, summary) {
    testFailedStage.value = stage
    testStage.value = 'FAILED'
    testSummary.value = summary
  }

  function resetTestStatus() {
    testStage.value = 'IDLE'
    testFailedStage.value = ''
    testSummary.value = '点击底部“保存并测试”开始'
    completedTestStages.value = []
  }

  function testStepState(stage) {
    if (testStage.value === 'PASSED' || completedTestStages.value.includes(stage))
      return 'completed'
    if (testStage.value === 'FAILED' && testFailedStage.value === stage)
      return 'failed'
    if (testStage.value === stage)
      return 'running'
    return 'pending'
  }

  function testStepClass(stage) {
    return ['is', testStepState(stage)].join('-')
  }

  function testStepIcon(step) {
    const state = testStepState(step.code)
    if (state === 'completed')
      return '✓'
    if (state === 'failed')
      return '!'
    return step.order
  }

  function buildHandlerTestInput(handler) {
    if (!handler?.inputSchema)
      return '{}'
    const input = {}
    Object.entries(handler.inputSchema).forEach(([field, definition]) => {
      input[field] = sampleValueForType(definition?.type)
    })
    return JSON.stringify(input, null, 2)
  }

  function sampleValueForType(type) {
    return {
      STRING: '示例值',
      LONG: 1,
      INTEGER: 1,
      NUMBER: 1,
      DECIMAL: 1,
      BOOLEAN: true,
      OBJECT: {},
      MAP: {},
      ARRAY: [],
      LIST: [],
    }[String(type || '').toUpperCase()] ?? null
  }

  function metadataPayload() {
    return {
      id: form.id,
      applicationId: props.application?.id,
      objectId: form.objectId,
      entryId: form.entryId,
      extensionName: form.extensionName,
      extensionCode: form.extensionCode,
      extensionType: form.extensionType,
      hookCode: form.hookCode,
      scopeType: resolveScopeType(),
      scopeKey: form.scopeKey || null,
      sortOrder: Number(form.sortOrder || 0),
      failurePolicy: form.failurePolicy,
      riskLevel: form.riskLevel,
      remark: form.remark || null,
    }
  }

  function buildVersionPayload() {
    let content = form.content || ''
    let processedContent = null
    let configJson = '{}'

    if (form.extensionType === 'VISUAL_RULE') {
      const issues = validateExtensionVisualRule(visualRule, clientFieldCatalog.value)
      if (issues.length)
        throw new Error(issues[0])
      content = JSON.stringify({
        match: visualRule.match,
        conditions: visualRule.conditions,
        actions: visualRule.actions,
      })
    }
    if (form.extensionType === 'CLIENT_JS')
      validateClientScript(content)
    if (form.extensionType === 'SCOPED_CSS') {
      const scoped = processScopedCss(content, {
        applicationCode: props.application?.applicationCode || '',
        pageCode: form.scopeKey || 'default',
      })
      processedContent = scoped.css
      configJson = JSON.stringify({
        scopeSelector: scoped.scopeSelector,
        selectorCount: scoped.selectorCount,
      })
    }
    if (form.extensionType === 'SERVER_BINDING') {
      if (!handlerCode.value)
        throw new Error('请选择平台注册处理器')
      content = '{}'
      configJson = JSON.stringify({ handlerCode: handlerCode.value })
    }

    return {
      content,
      processedContent,
      configJson,
      changeSummary: form.changeSummary || null,
    }
  }

  function resolveScopeType() {
    if (form.extensionType === 'SCOPED_CSS')
      return 'PAGE'
    if (form.entryId)
      return 'ENTRY'
    if (form.objectId)
      return 'OBJECT'
    return 'APPLICATION'
  }

  function parseJson(source, label) {
    try {
      const result = JSON.parse(source || '{}')
      if (!result || Array.isArray(result) || typeof result !== 'object')
        throw new Error('JSON 根节点不是对象')
      return result
    }
    catch {
      throw new Error(`${label}必须是合法 JSON 对象`)
    }
  }

  function startRenewTimer() {
    clearRenewTimer()
    if (!form.id || !form.lockToken)
      return
    mut.renewTimer = window.setInterval(async () => {
      try {
        const response = await renewBusinessExtensionLock(form.id, form.lockToken)
        form.lockExpireTime = response.data?.expireTime || form.lockExpireTime
      }
      catch {
        clearRenewTimer()
        message.warning('增强编辑锁已失效，请关闭后重新打开')
      }
    }, 4 * 60 * 1000)
  }

  function clearRenewTimer() {
    if (mut.renewTimer) {
      window.clearInterval(mut.renewTimer)
      mut.renewTimer = null
    }
  }

  function closeWorkspace() {
    emit('update:show', false)
    handleAfterLeave()
  }

  function handleAfterLeave() {
    mut.clientContextCatalogRequestId += 1
    clientContextCatalogLoading.value = false
    clearRenewTimer()
    emit('closed', { id: form.id, lockToken: form.lockToken })
  }
  __impl.entryDisplayName = entryDisplayName
  __impl.riskLevelLabel = riskLevelLabel
  __impl.selectExtensionType = selectExtensionType
  __impl.defaultForm = defaultForm
  __impl.hydrateForm = hydrateForm
  __impl.normalizeVisualRuleRows = normalizeVisualRuleRows
  __impl.addCondition = addCondition
  __impl.addAction = addAction
  __impl.applyCodeExampleContext = applyCodeExampleContext
  __impl.resolveClientContextObjectId = resolveClientContextObjectId
  __impl.loadClientContextCatalog = loadClientContextCatalog
  __impl.visualRuleFieldOptions = visualRuleFieldOptions
  __impl.visualRuleWritableFieldOptions = visualRuleWritableFieldOptions
  __impl.visualRuleActionOptions = visualRuleActionOptions
  __impl.conditionField = conditionField
  __impl.actionField = actionField
  __impl.conditionValueKind = conditionValueKind
  __impl.actionValueKind = actionValueKind
  __impl.syncClientContextFromScript = syncClientContextFromScript
  __impl.detectClientScriptBindings = detectClientScriptBindings
  __impl.addClientTestField = addClientTestField
  __impl.handleClientTestFieldChange = handleClientTestFieldChange
  __impl.handleClientValueTypeChange = handleClientValueTypeChange
  __impl.createClientTestField = createClientTestField
  __impl.normalizeClientTestValue = normalizeClientTestValue
  __impl.sampleClientFieldValue = sampleClientFieldValue
  __impl.refreshClientFieldSamples = refreshClientFieldSamples
  __impl.applyClientValuePreset = applyClientValuePreset
  __impl.clientValuePlaceholder = clientValuePlaceholder
  __impl.buildClientSandboxContext = buildClientSandboxContext
  __impl.normalizeClientRecordId = normalizeClientRecordId
  __impl.parseClientTestFieldValue = parseClientTestFieldValue
  __impl.saveCurrent = saveCurrent
  __impl.saveAndTest = saveAndTest
  __impl.enableCurrentVersion = enableCurrentVersion
  __impl.beginTestStage = beginTestStage
  __impl.completeTestStage = completeTestStage
  __impl.failTestStage = failTestStage
  __impl.resetTestStatus = resetTestStatus
  __impl.testStepState = testStepState
  __impl.testStepClass = testStepClass
  __impl.testStepIcon = testStepIcon
  __impl.buildHandlerTestInput = buildHandlerTestInput
  __impl.sampleValueForType = sampleValueForType
  __impl.metadataPayload = metadataPayload
  __impl.buildVersionPayload = buildVersionPayload
  __impl.resolveScopeType = resolveScopeType
  __impl.parseJson = parseJson
  __impl.startRenewTimer = startRenewTimer
  __impl.clearRenewTimer = clearRenewTimer
  __impl.closeWorkspace = closeWorkspace
  __impl.handleAfterLeave = handleAfterLeave

  return {
    props, emit, __impl, mut, actionField, actionValueKind, addAction, addClientTestField,
    addCondition, applyClientValuePreset, applyCodeExampleContext, beginTestStage, buildClientSandboxContext, buildHandlerTestInput, buildVersionPayload, clearRenewTimer,
    clientValuePlaceholder, closeWorkspace, completeTestStage, conditionField, conditionValueKind, createClientTestField, defaultForm, detectClientScriptBindings,
    enableCurrentVersion, entryDisplayName, failTestStage, handleAfterLeave, handleClientTestFieldChange, handleClientValueTypeChange, hydrateForm, loadClientContextCatalog,
    metadataPayload, normalizeClientRecordId, normalizeClientTestValue, normalizeVisualRuleRows, parseClientTestFieldValue, parseJson, refreshClientFieldSamples, resetTestStatus,
    resolveClientContextObjectId, resolveScopeType, riskLevelLabel, sampleClientFieldValue, sampleValueForType, saveAndTest, saveCurrent, selectExtensionType,
    startRenewTimer, syncClientContextFromScript, testStepClass, testStepIcon, testStepState, visualRuleActionOptions, visualRuleFieldOptions, visualRuleWritableFieldOptions,
    message, formRef, sandboxRef, saving, testing, enabling, showTypeGuide, governanceOpen,
    handlerCode, serverTestInput, clientRecordId, clientTestFields, clientAllowedActions, clientFieldCatalog, clientActionCatalog, clientContextCatalogLoading,
    clientContextCatalogError, testStage, testFailedStage, testSummary, completedTestStages, visualRule, form, extensionTypeChoices,
    clientValueTypeOptions, clientBooleanOptions, booleanRuleOptions, clientSensitiveKeyPattern, clientReservedKeys, clientHookScenes, isEdit, statusLabel,
    statusTone, objectOptions, entryOptions, scopedCssPageOptions, scopedCssPageLabel, handlerOptions, selectedHandler, selectedExtensionType,
    contentStageHint, contentStageReady, hookSummaryLabel, objectSummaryLabel, scopeSummaryLabel, selectedEntryLabel, clientFieldOptions, clientActionOptions,
    clientContextScene, clientDetectedSummary, clientContextPreview, testSteps, allowedHooksForType, cssResult, cssError, rules,
  }
}
