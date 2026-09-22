import { storeToRefs } from 'pinia'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getBusinessActionRegistrationSource,
  getCapabilityVersionDraft,
  getFlowActionRegistrationSource,
  getSystemServiceRegistrationSources,
  publishBusinessActionCapability,
  publishFlowActionCapability,
  publishSystemServiceCapability,
} from '@/api/ai/capability'
import { publishApplicationCapability } from '@/api/application-integration'
import {
  businessObjectList,
} from '@/api/business-app'
import { businessApplicationRuntimeByCode } from '@/api/business-application'
import { useDict } from '@/composables'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'
import { registrationScenario } from '../registrationSources'

export function useCapabilityRegistration(props, emit) {
  const registration = useCapabilityRegistrationStore()
  const { step, scenario } = storeToRefs(registration)
  const form = registration.form
  const advancedSource = ref(false)
  const router = useRouter()

  const {
    dict,
    loading: dictLoading,
    errors: dictErrors,
    reload: reloadCapabilityDicts,
  } = useDict(
    'ai_capability_flow_operation',
    'ai_capability_actor_type',
    'ai_capability_risk_level',
  )

  const formRef = ref(null)

  const objectLoading = ref(false)

  const detailLoading = ref(false)

  const systemSourceLoading = ref(false)

  const submitting = ref(false)

  const draftLoading = ref(false)

  const sourceError = ref('')

  const flowSourceError = ref('')

  const flowSource = ref(null)

  const businessActionSource = ref(null)

  const objects = ref([])

  const actions = ref([])

  const fields = ref([])

  const systemServices = ref([])

  const lastGeneratedCode = ref('')

  let variableKeySequence = 0
  let sourceGeneration = 0
  let objectGeneration = 0
  let openGeneration = 0
  onBeforeUnmount(() => {
    sourceGeneration++
    objectGeneration++
    openGeneration++
  })
  const isUpgrade = computed(() => !!props.capability?.id)
  const sourceLoading = computed(() => draftLoading.value || objectLoading.value || systemSourceLoading.value || detailLoading.value)
  const selectedBusinessAction = computed(() => actions.value.find(item => item.actionCode === form.actionCode))

  Object.assign(form, {
    sourceType: 'BUSINESS_ACTION',
    objectId: null,
    suiteCode: '',
    objectCode: '',
    actionCode: null,
    operation: null,
    capabilityCode: '',
    version: '1.0.0',
    description: '',
    allowedFields: [],
    requiredFields: [],
    systemServiceCode: null,
    systemModelId: null,
    systemEndpointId: null,
    systemFormId: null,
    systemVariables: [],
  })

  const flowOperationOptions = computed(() => (dict.value.ai_capability_flow_operation || [])
    .map(option => ({
      ...option,
      disabled: (option.value === 'START' && flowSource.value && !flowSource.value.startSupported)
        || (option.value === 'SUBMIT' && flowSource.value && !flowSource.value.submissionSupported),
    })))

  const flowOperationDictLoaded = computed(() => Object.prototype.hasOwnProperty.call(
    dict.value,
    'ai_capability_flow_operation',
  ))

  const flowOperationDictError = computed(() => dictErrors.value.ai_capability_flow_operation || '')

  const flowSubmitOptionMissing = computed(() => !isUpgrade.value
    && !dictLoading.value
    && (flowOperationDictLoaded.value || !!flowOperationDictError.value)
    && !flowOperationOptions.value.some(option => option.value === 'SUBMIT'))

  const flowSubmissionFieldOptions = computed(() => (flowSource.value?.submissionFields || [])
    .map(field => ({
      label: `${field.label || '未命名字段'}（${field.dataType || 'string'}${field.required ? ' · 必填' : ''}）`,
      value: field.field,
      disabled: field.required,
    })))

  const flowRequiredSourceFields = computed(() => (flowSource.value?.submissionFields || [])
    .filter(field => field.required)
    .map(field => field.field))

  const flowRequiredFieldOptions = computed(() => flowSubmissionFieldOptions.value
    .filter(item => form.allowedFields.includes(item.value)))

  const selectedSystemService = computed(() => systemServices.value
    .find(item => item.serviceCode === form.systemServiceCode))

  const selectedSystemServiceActorLabel = computed(() => resolveDictLabel(
    'ai_capability_actor_type',
    selectedSystemService.value?.requiredActorType,
  ))

  const selectedSystemServiceRiskLabel = computed(() => resolveDictLabel(
    'ai_capability_risk_level',
    selectedSystemService.value?.riskLevel,
  ))

  const systemKind = computed(() => selectedSystemService.value
    ? selectedSystemService.value.options?.registrationKind || 'FLOW'
    : { application: 'FORM', rest: 'REST', flow: 'FLOW' }[scenario.value])
  const selectedEndpoint = computed(() => selectedSystemService.value?.options?.endpoints?.find(item => item.id === form.systemEndpointId))
  const selectedForm = computed(() => selectedSystemService.value?.options?.forms?.find(item => item.id === form.systemFormId))
  const reviewFields = computed(() => {
    const source = form.sourceType === 'SYSTEM_SERVICE'
      ? selectedForm.value?.fields || []
      : form.sourceType === 'FLOW_ACTION'
        ? flowSource.value?.submissionFields || []
        : fields.value.map(item => ({ field: item.fieldCode, label: item.fieldName }))
    return source.filter(item => form.allowedFields.includes(item.field))
  })
  const systemServiceOptions = computed(() => systemServices.value.filter((item) => {
    if (isUpgrade.value)
      return item.serviceCode === props.capability.sourceKey
    const kind = item.options?.registrationKind || 'FLOW'
    return kind === (scenario.value === 'rest' ? 'REST' : scenario.value === 'application' ? 'FORM' : 'FLOW')
  }).map(item => ({
    label: `${item.serviceName}（${item.serviceCode}）`,
    value: item.serviceCode,
  })))

  const systemModelOptions = computed(() => (selectedSystemService.value?.options?.models || [])
    .map(model => ({
      label: `${model.modelName}（${model.modelKey} · v${model.modelVersion}）`,
      value: model.modelId,
    })))

  const systemVariableTypeOptions = computed(() => (selectedSystemService.value?.options?.variableTypes || [])
    .map(type => ({
      label: variableTypeLabel(type),
      value: type,
    })))

  const reviewSource = computed(() => form.sourceType === 'SYSTEM_SERVICE'
    ? selectedForm.value?.name || selectedEndpoint.value?.name || systemModelOptions.value.find(item => item.value === form.systemModelId)?.label || selectedSystemService.value?.serviceName
    : objects.value.find(item => String(item.id) === String(form.objectId))?.objectName || form.objectCode)

  const modalTitle = computed(() => isUpgrade.value ? '发布能力新版本' : '注册开放能力')

  const submitText = computed(() => isUpgrade.value ? '发布新版本' : '注册并发布')

  const submitDisabled = computed(() => {
    if (sourceLoading.value || !!sourceError.value)
      return true
    if (form.sourceType === 'FLOW_ACTION') {
      return !flowSource.value || detailLoading.value
        || (form.operation === 'SUBMIT' && !flowSource.value.submissionSupported)
    }
    if (form.sourceType === 'SYSTEM_SERVICE') {
      if (systemSourceLoading.value || !selectedSystemService.value)
        return true
      if (systemKind.value === 'REST')
        return !selectedEndpoint.value?.available
      if (systemKind.value === 'FORM')
        return !selectedForm.value?.available || !form.allowedFields.length
      return !form.systemModelId
    }
    return detailLoading.value || !selectedBusinessAction.value?.publishable
  })

  const rules = {
    objectId: {
      trigger: 'change',
      validator: (_rule, value) => form.sourceType === 'SYSTEM_SERVICE' || isPositiveId(value)
        ? true
        : new Error('请选择已发布业务对象'),
    },
    actionCode: {
      trigger: 'change',
      validator: () => {
        if (form.sourceType !== 'BUSINESS_ACTION')
          return true
        if (!form.actionCode)
          return new Error('请选择业务动作')
        if (!selectedBusinessAction.value?.publishable) {
          return new Error(selectedBusinessAction.value?.unavailableReason
            || '该业务动作的执行步骤不符合开放平台安全规则')
        }
        return true
      },
    },
    operation: {
      trigger: 'change',
      validator: () => {
        if (form.sourceType !== 'FLOW_ACTION')
          return true
        if (!flowSource.value)
          return new Error('所选对象未匹配到可发布的主流程')
        if (!form.operation)
          return new Error('请选择流程动作')
        if (form.operation === 'START' && !flowSource.value.startSupported)
          return new Error('该对象不是平台托管运行对象，不能注册发起流程能力')
        if (form.operation === 'SUBMIT' && !flowSource.value.submissionSupported) {
          return new Error(flowSource.value.submissionUnavailableReason
            || '该对象暂不能注册提交业务申请能力')
        }
        return true
      },
    },
    allowedFields: {
      trigger: 'change',
      validator: () => (form.sourceType !== 'BUSINESS_ACTION'
        && !(form.sourceType === 'FLOW_ACTION' && form.operation === 'SUBMIT'))
      || form.allowedFields.length > 0
        ? true
        : new Error('请至少选择一个允许字段'),
    },
    systemServiceCode: {
      trigger: 'change',
      validator: () => form.sourceType !== 'SYSTEM_SERVICE' || form.systemServiceCode
        ? true
        : new Error('请选择系统服务'),
    },
    systemModelId: {
      trigger: 'change',
      validator: () => form.sourceType !== 'SYSTEM_SERVICE' || systemKind.value !== 'FLOW' || form.systemModelId
        ? true
        : new Error('请选择已发布流程模型'),
    },
    capabilityCode: [
      { required: true, message: '请输入能力编码', trigger: 'blur' },
      {
        pattern: /^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)*$/,
        message: '使用小写点分编码，每段以字母开头',
        trigger: 'blur',
      },
    ],
    version: [
      { required: true, message: '请输入能力版本', trigger: 'blur' },
      {
        pattern: /^(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$/,
        message: '版本必须使用三段语义版本，如 1.0.0',
        trigger: 'blur',
      },
    ],
  }

  function isPositiveId(value) {
    if (typeof value === 'number')
      return Number.isInteger(value) && value > 0
    return typeof value === 'string' && /^[1-9]\d*$/.test(value)
  }

  function actionAvailabilitySuffix(action) {
    if (action.publishable)
      return ''
    if (action.status === 0)
      return ' · 已停用'
    if (String(action.actionType || '').toUpperCase() === 'OPEN_PAGE')
      return ' · 页面操作，不能直接开放'
    if (['START_FLOW', 'START_APPROVAL'].includes(String(action.actionType || '').toUpperCase()))
      return ' · 请使用流程动作'
    if (!Array.isArray(action.stepTypes) || action.stepTypes.length === 0)
      return ' · 未配置执行步骤'
    return ` · 步骤暂不支持（${action.stepTypes.join(' / ')}）`
  }

  const objectOptions = computed(() => objects.value.map(item => ({
    label: `${item.objectName || item.objectCode}（${item.objectCode}）`,
    value: item.id,
  })))

  const actionOptions = computed(() => actions.value.map(item => ({
    label: `${item.actionName || item.actionCode}（${item.actionCode}）${actionAvailabilitySuffix(item)}`,
    value: item.actionCode,
    disabled: !item.publishable,
  })))

  const recommendFlowSubmission = computed(() => !isUpgrade.value
    && props.allowedTypes.includes('FLOW_ACTION')
    && actions.value.some(item => isCreatePageAction(item)))

  const businessActionNotice = computed(() => {
    if (!businessActionSource.value)
      return null
    const unavailable = actions.value.filter(item => !item.publishable)
    const availableCount = actions.value.length - unavailable.length
    if (actions.value.length === 0) {
      return {
        type: 'error',
        title: '当前发布版本没有业务动作',
        summary: '请先在业务对象设计器中新增自动化动作、配置执行步骤，然后重新发布业务对象。',
        items: [],
        remaining: 0,
      }
    }
    if (unavailable.length === 0)
      return null
    const containsCreatePageAction = unavailable.some(item => isCreatePageAction(item))
    return {
      type: availableCount > 0 ? 'warning' : 'error',
      title: availableCount > 0
        ? `${unavailable.length} 个动作已从可发布候选中禁用`
        : '当前发布版本没有可开放的业务动作',
      summary: availableCount > 0
        ? `仍有 ${availableCount} 个动作可选；禁用项会保留在下拉列表中并标明原因。`
        : containsCreatePageAction
          ? '你看到的“新增”只是打开新增表单的页面按钮，不会在服务端创建记录。申请类对象请改用“提交业务申请”，一次完成创建记录和发起流程。'
          : '业务动作必须包含开放平台支持的执行步骤，启用状态不代表它已经可执行。',
      items: unavailable.slice(0, 3),
      remaining: Math.max(unavailable.length - 3, 0),
    }
  })

  function isCreatePageAction(action = {}) {
    if (String(action.actionType || '').toUpperCase() !== 'OPEN_PAGE')
      return false
    const code = String(action.actionCode || '').trim().toLowerCase()
    const name = String(action.actionName || '').trim()
    return ['add', 'create', 'new', 'insert'].includes(code)
      || ['新增', '创建', '新建'].some(keyword => name.includes(keyword))
  }

  const fieldOptions = computed(() => fields.value
    .filter(item => String(item.fieldStatus || '').toUpperCase() !== 'DISABLED')
    .map(item => ({
      label: item.fieldName || '未命名字段',
      value: item.fieldCode,
    })))

  const requiredFieldOptions = computed(() => fieldOptions.value
    .filter(item => form.allowedFields.includes(item.value)))

  function resolveDictLabel(dictType, value) {
    if (!value)
      return '-'
    const option = (dict.value[dictType] || [])
      .find(item => String(item.value) === String(value))
    return option?.label || value
  }

  watch(() => props.show, async (visible) => {
    const opening = ++openGeneration
    sourceGeneration++
    objectGeneration++
    if (!visible)
      return
    registration.initialize(props.initialContext)
    advancedSource.value = false
    resetForm()
    // 字典独立加载，不能阻塞场景选择和来源 loading。
    void reloadFlowOperationOptions(false)
    if (isUpgrade.value) {
      step.value = 2
      await initializeUpgrade()
      if (opening !== openGeneration)
        return
      scenario.value = registrationScenario(form.sourceType, props.capability?.sourceKey)
      step.value = 2
    }
    else if (props.initialContext?.lockApplication) {
      await chooseScenario(props.allowedTypes.some(type => ['SYSTEM_SERVICE', 'BUSINESS_ACTION'].includes(type)) ? 'application' : 'flow')
    }
  }, { immediate: true })

  watch(flowOperationOptions, (options) => {
    if (form.sourceType !== 'FLOW_ACTION' || form.operation || options.length === 0)
      return
    const defaultOption = preferredFlowOperation(options)
    form.operation = defaultOption.value
    updateGeneratedCode()
  }, { immediate: true })

  watch(() => form.allowedFields, (allowedFields) => {
    if (form.sourceType === 'FLOW_ACTION' && form.operation === 'SUBMIT') {
      const required = flowRequiredSourceFields.value
      const nextAllowed = [...new Set([...allowedFields, ...required])]
      const nextRequired = [...new Set([
        ...form.requiredFields.filter(field => form.allowedFields.includes(field)),
        ...required,
      ])]
      if (!sameStringArray(form.allowedFields, nextAllowed))
        form.allowedFields = nextAllowed
      if (!sameStringArray(form.requiredFields, nextRequired))
        form.requiredFields = nextRequired
      return
    }
    const nextRequired = form.requiredFields.filter(field => allowedFields.includes(field))
    if (!sameStringArray(form.requiredFields, nextRequired))
      form.requiredFields = nextRequired
  }, { deep: true })

  function sameStringArray(left, right) {
    return left.length === right.length && left.every((item, index) => item === right[index])
  }

  function resetForm() {
    draftLoading.value = false
    objectLoading.value = false
    detailLoading.value = false
    systemSourceLoading.value = false
    const sourceType = props.allowedTypes.includes('FLOW_ACTION')
      ? 'FLOW_ACTION'
      : props.allowedTypes[0] || 'BUSINESS_ACTION'
    Object.assign(form, {
      sourceType,
      objectId: null,
      suiteCode: '',
      objectCode: '',
      actionCode: null,
      operation: resolveDefaultOperation(),
      capabilityCode: '',
      version: '1.0.0',
      description: '',
      allowedFields: [],
      requiredFields: [],
      systemServiceCode: null,
      systemModelId: null,
      systemEndpointId: null,
      systemFormId: null,
      systemVariables: [],
    })
    actions.value = []
    fields.value = []
    sourceError.value = ''
    flowSourceError.value = ''
    flowSource.value = null
    businessActionSource.value = null
    objects.value = []
    systemServices.value = []
    lastGeneratedCode.value = ''
  }

  async function initializeUpgrade() {
    const opening = openGeneration
    draftLoading.value = true
    sourceError.value = ''
    try {
      const res = await getCapabilityVersionDraft(props.capability.id)
      if (opening !== openGeneration)
        return
      const draft = res.data
      if (!draft || !props.allowedTypes.includes(draft.sourceType)) {
        throw new Error('当前账号没有发布该类型能力新版本的权限')
      }
      Object.assign(form, {
        sourceType: draft.sourceType,
        capabilityCode: draft.capabilityCode,
        version: draft.suggestedVersion,
        description: draft.description || '',
      })
      if (draft.sourceType === 'SYSTEM_SERVICE') {
        await initializeSystemServiceUpgrade(draft)
      }
      else {
        await initializeObjectCapabilityUpgrade(draft)
      }
    }
    catch (error) {
      if (opening !== openGeneration)
        return
      sourceError.value = error?.message || '能力新版本草稿加载失败'
    }
    finally {
      if (opening === openGeneration)
        draftLoading.value = false
    }
  }

  async function initializeObjectCapabilityUpgrade(draft) {
    const opening = openGeneration
    await loadObjects()
    if (opening !== openGeneration)
      return
    const [suiteCode, objectCode, sourceAction] = String(draft.sourceKey || '').split('/')
    if (!suiteCode || !objectCode || !sourceAction)
      throw new Error('当前能力来源标识不完整，无法自动创建新版本')
    const object = objects.value.find((item) => {
      const itemSuiteCode = item.suiteCode || 'default'
      return itemSuiteCode === suiteCode && item.objectCode === objectCode
    })
    if (!object)
      throw new Error(`原业务对象 ${suiteCode}/${objectCode} 已不存在或尚未发布`)

    form.objectId = object.id
    if (draft.sourceType === 'FLOW_ACTION') {
      const operation = draft.policySnapshot?.operation || sourceAction
      if (operation !== sourceAction)
        throw new Error('当前能力流程动作快照不一致，无法自动创建新版本')
      form.operation = operation
    }
    await handleObjectChange(object.id)
    if (opening !== openGeneration)
      return
    if (draft.sourceType === 'BUSINESS_ACTION') {
      const sourceOption = actionOptions.value.find(item => item.value === sourceAction)
      if (!sourceOption)
        throw new Error(`原业务动作 ${sourceAction} 已停用或不存在，无法创建新版本`)
      if (sourceOption.disabled) {
        const sourceActionDefinition = actions.value.find(item => item.actionCode === sourceAction)
        throw new Error(sourceActionDefinition?.unavailableReason
          || `原业务动作 ${sourceAction} 的执行步骤已不符合开放平台安全规则`)
      }
      form.actionCode = sourceAction
      const allowedFields = Array.isArray(draft.policySnapshot?.allowedFields)
        ? draft.policySnapshot.allowedFields
        : []
      const requiredFields = Array.isArray(draft.policySnapshot?.requiredFields)
        ? draft.policySnapshot.requiredFields
        : []
      const availableFields = new Set(fieldOptions.value.map(item => item.value))
      form.allowedFields = allowedFields.filter(field => availableFields.has(field))
      form.requiredFields = requiredFields.filter(field => form.allowedFields.includes(field))
    }
    else {
      if (!flowSource.value)
        throw new Error(flowSourceError.value || '当前业务对象未匹配到可发布的主流程')
      if (form.operation !== sourceAction)
        throw new Error(`原流程动作 ${sourceAction} 当前不可用，无法创建新版本`)
      if (form.operation === 'SUBMIT') {
        const availableFields = new Set(flowSubmissionFieldOptions.value.map(item => item.value))
        const allowedFields = Array.isArray(draft.policySnapshot?.allowedFields)
          ? draft.policySnapshot.allowedFields.filter(field => availableFields.has(field))
          : []
        const requiredFields = Array.isArray(draft.policySnapshot?.requiredFields)
          ? draft.policySnapshot.requiredFields.filter(field => availableFields.has(field))
          : []
        form.allowedFields = [...new Set([...allowedFields, ...flowRequiredSourceFields.value])]
        form.requiredFields = [...new Set([...requiredFields, ...flowRequiredSourceFields.value])]
      }
    }
  }

  async function initializeSystemServiceUpgrade(draft) {
    const opening = openGeneration
    await loadSystemServices()
    if (opening !== openGeneration)
      return
    const service = systemServices.value.find(item => item.serviceCode === draft.sourceKey)
    if (!service)
      throw new Error(`原系统服务 ${draft.sourceKey} 当前未注册，无法创建新版本`)
    form.systemServiceCode = service.serviceCode
    const parameters = draft.policySnapshot?.registrationParameters
    if (service.options?.registrationKind === 'REST') {
      form.systemEndpointId = parameters?.endpointId || null
      return
    }
    if (service.options?.registrationKind === 'FORM') {
      form.systemFormId = [parameters?.suiteCode, parameters?.objectCode].join('/')
      form.allowedFields = [...(parameters?.allowedFields || [])]
      form.requiredFields = [...(parameters?.requiredFields || [])]
      return
    }
    const modelId = draft.policySnapshot?.modelId || null
    const modelOption = systemModelOptions.value.find(item => String(item.value) === String(modelId))
    if (!modelOption)
      throw new Error('原流程模型已停用或未发布，无法创建新版本')
    form.systemModelId = modelOption.value

    const variableSchemas = draft.inputSchema?.properties?.variables?.properties || {}
    const allowedVariables = Array.isArray(draft.policySnapshot?.allowedVariables)
      ? draft.policySnapshot.allowedVariables
      : Object.keys(variableSchemas)
    const requiredVariables = new Set(Array.isArray(draft.policySnapshot?.requiredVariables)
      ? draft.policySnapshot.requiredVariables
      : [])
    form.systemVariables = allowedVariables.map((name) => {
      variableKeySequence += 1
      const schema = variableSchemas[name] || {}
      return {
        key: `variable-${variableKeySequence}`,
        name,
        type: schema.type || 'string',
        description: schema.description || '',
        required: requiredVariables.has(name),
      }
    })
  }

  function resolveDefaultOperation() {
    const options = flowOperationOptions.value
    return preferredFlowOperation(options)?.value || null
  }

  function preferredFlowOperation(options) {
    return options.find(item => item.value === 'SUBMIT' && !item.disabled)
      || options.find(item => item.isDefault === 'Y' && !item.disabled)
      || options.find(item => !item.disabled)
      || options[0]
  }

  async function loadObjects() {
    const request = ++objectGeneration
    const opening = openGeneration
    objectLoading.value = true
    sourceError.value = ''
    try {
      const res = await businessObjectList({})
      if (opening !== openGeneration || request !== objectGeneration)
        return
      let availableObjects = (res.data || []).filter(item => item.status === 1
        && Number(item.lastPublishVersion || 0) > 0)
      if (props.initialContext?.lockApplication) {
        const published = await businessApplicationRuntimeByCode(props.initialContext.applicationCode)
        if (opening !== openGeneration || request !== objectGeneration)
          return
        const ids = new Set((published.data?.objects || []).map(item => String(item.objectId)))
        availableObjects = availableObjects.filter(item => ids.has(String(item.id)))
      }
      objects.value = availableObjects
    }
    catch (error) {
      if (opening !== openGeneration || request !== objectGeneration)
        return
      objects.value = []
      sourceError.value = error?.message || '已发布业务对象加载失败'
    }
    finally {
      if (opening === openGeneration && request === objectGeneration)
        objectLoading.value = false
    }
  }

  async function loadSystemServices() {
    const request = ++sourceGeneration
    const opening = openGeneration
    systemSourceLoading.value = true
    sourceError.value = ''
    systemServices.value = []
    try {
      const serviceCode = isUpgrade.value ? props.capability.sourceKey : { application: 'lowcode.form.create', rest: 'system.rest.invoke', flow: 'flow.process.start' }[scenario.value]
      const res = await getSystemServiceRegistrationSources({ serviceCode })
      if (request !== sourceGeneration || opening !== openGeneration)
        return
      systemServices.value = res.data || []
      if (systemServiceOptions.value.length === 1) {
        form.systemServiceCode = systemServiceOptions.value[0].value
        handleSystemServiceChange(form.systemServiceCode)
      }
    }
    catch (error) {
      if (request !== sourceGeneration || opening !== openGeneration)
        return
      sourceError.value = error?.message || '系统服务注册来源加载失败'
    }
    finally {
      if (request === sourceGeneration && opening === openGeneration)
        systemSourceLoading.value = false
    }
  }

  async function handleSourceTypeChange() {
    sourceGeneration++
    objectGeneration++
    detailLoading.value = false
    systemSourceLoading.value = false
    objectLoading.value = false
    sourceError.value = ''
    form.objectId = null
    form.suiteCode = ''
    form.objectCode = ''
    form.actionCode = null
    form.operation = resolveDefaultOperation()
    form.allowedFields = []
    form.requiredFields = []
    form.systemServiceCode = null
    form.systemModelId = null
    form.systemEndpointId = null
    form.systemFormId = null
    form.systemVariables = []
    actions.value = []
    fields.value = []
    businessActionSource.value = null
    flowSourceError.value = ''
    flowSource.value = null
    updateGeneratedCode(true)
    if (form.sourceType === 'SYSTEM_SERVICE')
      await loadSystemServices()
    else if (objects.value.length === 0)
      await loadObjects()
  }

  async function handleObjectChange(objectId) {
    const request = ++sourceGeneration
    detailLoading.value = false
    const selected = objects.value.find(item => String(item.id) === String(objectId))
    form.suiteCode = selected?.suiteCode || ''
    form.objectCode = selected?.objectCode || ''
    form.actionCode = null
    form.allowedFields = []
    form.requiredFields = []
    actions.value = []
    fields.value = []
    businessActionSource.value = null
    flowSourceError.value = ''
    flowSource.value = null
    updateGeneratedCode()
    if (!selected)
      return

    detailLoading.value = true
    sourceError.value = ''
    try {
      if (form.sourceType === 'BUSINESS_ACTION') {
        const res = await getBusinessActionRegistrationSource({
          suiteCode: selected.suiteCode,
          objectCode: selected.objectCode,
        })
        if (request !== sourceGeneration)
          return
        businessActionSource.value = res.data || null
        actions.value = businessActionSource.value?.actions || []
        fields.value = businessActionSource.value?.writableFields || []
        const publishableActions = actions.value.filter(item => item.publishable)
        if (publishableActions.length === 1) {
          form.actionCode = publishableActions[0].actionCode
          updateGeneratedCode()
        }
      }
      else {
        const res = await getFlowActionRegistrationSource({
          suiteCode: selected.suiteCode,
          objectCode: selected.objectCode,
        })
        if (request !== sourceGeneration)
          return
        flowSource.value = res.data
        const selectedOperation = flowOperationOptions.value
          .find(option => option.value === form.operation && !option.disabled)
        if (!selectedOperation)
          form.operation = preferredFlowOperation(flowOperationOptions.value)?.value || null
        applyFlowSubmissionDefaults()
        updateGeneratedCode()
      }
    }
    catch (error) {
      if (request !== sourceGeneration)
        return
      if (form.sourceType === 'FLOW_ACTION') {
        flowSourceError.value = Number(error?.code) === 404
          ? '流程能力注册接口未装配，请更新并重启 Admin 服务'
          : error?.message || '该对象未配置已启用的主流程，暂不能注册流程能力'
      }
      else {
        sourceError.value = error?.message || '业务动作和字段加载失败'
      }
    }
    finally {
      if (request === sourceGeneration)
        detailLoading.value = false
    }
  }

  function handleActionChange() {
    updateGeneratedCode()
  }

  function openBusinessActionDesigner() {
    if (!form.objectCode)
      return
    const target = router.resolve({
      name: 'BusinessObjectDesigner',
      params: { objectCode: form.objectCode },
      query: {
        panel: 'actions',
        ...(form.suiteCode ? { suiteCode: form.suiteCode } : {}),
      },
    })
    window.open(target.href, '_blank', 'noopener,noreferrer')
  }

  async function switchToFlowSubmission() {
    const objectId = form.objectId
    await reloadFlowOperationOptions(false)
    form.sourceType = 'FLOW_ACTION'
    await handleSourceTypeChange()
    form.operation = 'SUBMIT'
    form.objectId = objectId
    await handleObjectChange(objectId)
  }

  async function reloadFlowOperationOptions(selectSubmit = true) {
    await reloadCapabilityDicts('ai_capability_flow_operation')
    const submitOption = flowOperationOptions.value
      .find(option => option.value === 'SUBMIT' && !option.disabled)
    if (!selectSubmit || isUpgrade.value || form.sourceType !== 'FLOW_ACTION' || !submitOption)
      return
    form.operation = submitOption.value
    applyFlowSubmissionDefaults()
    updateGeneratedCode()
  }

  function handleOperationChange() {
    applyFlowSubmissionDefaults()
    updateGeneratedCode()
  }

  function applyFlowSubmissionDefaults() {
    if (form.operation !== 'SUBMIT') {
      if (form.sourceType === 'FLOW_ACTION') {
        form.allowedFields = []
        form.requiredFields = []
      }
      return
    }
    const available = (flowSource.value?.submissionFields || []).map(field => field.field)
    form.allowedFields = [...available]
    form.requiredFields = [...flowRequiredSourceFields.value]
  }

  function handleSystemServiceChange() {
    form.systemEndpointId = null
    form.systemFormId = null
    form.allowedFields = []
    form.requiredFields = []
    form.systemModelId = null
    form.systemVariables = []
    updateGeneratedCode()
  }

  function addSystemVariable() {
    if (form.systemVariables.length >= 50)
      return
    const defaultType = selectedSystemService.value?.options?.variableTypes?.includes('string')
      ? 'string'
      : selectedSystemService.value?.options?.variableTypes?.[0] || 'string'
    variableKeySequence += 1
    form.systemVariables.push({
      key: `variable-${variableKeySequence}`,
      name: '',
      type: defaultType,
      description: '',
      required: false,
    })
  }

  function removeSystemVariable(index) {
    form.systemVariables.splice(index, 1)
  }

  function variableTypeLabel(type) {
    return {
      string: '文本（string）',
      integer: '整数（integer）',
      number: '数值（number）',
      boolean: '布尔（boolean）',
      object: '对象（object）',
      array: '数组（array）',
    }[type] || type
  }

  function updateGeneratedCode(force = false) {
    if (isUpgrade.value)
      return
    if (form.sourceType === 'SYSTEM_SERVICE') {
      const model = selectedSystemService.value?.options?.models
        ?.find(item => item.modelId === form.systemModelId)
      const parts = [
        'system',
        ...String(form.systemServiceCode || '').split('.'),
        model?.modelKey,
      ].map(normalizeCodeSegment).filter(Boolean)
      const sourceCode = systemKind.value === 'REST'
        ? (selectedEndpoint.value ? `rest.${normalizeCodeSegment(selectedEndpoint.value.method)}.${normalizeCodeSegment(selectedEndpoint.value.path)}.x_${selectedEndpoint.value.id.slice(0, 8)}` : '')
        : systemKind.value === 'FORM'
          ? (selectedForm.value ? ['form', selectedForm.value.suiteCode, selectedForm.value.objectCode, 'create'].map(normalizeCodeSegment).join('.') : '')
          : form.systemServiceCode && model ? parts.join('.') : ''
      const nextCode = contextualCode(sourceCode)
      if (force || !form.capabilityCode || form.capabilityCode === lastGeneratedCode.value)
        form.capabilityCode = nextCode
      lastGeneratedCode.value = nextCode
      return
    }
    const actionSegment = form.sourceType === 'BUSINESS_ACTION' ? form.actionCode : form.operation
    const parts = [
      form.sourceType === 'BUSINESS_ACTION' || form.operation === 'SUBMIT' ? 'business' : 'flow',
      form.suiteCode,
      form.objectCode,
      actionSegment,
    ].map(normalizeCodeSegment).filter(Boolean)
    const nextCode = contextualCode(parts.length === 4 ? parts.join('.') : '')
    if (force || !form.capabilityCode || form.capabilityCode === lastGeneratedCode.value)
      form.capabilityCode = nextCode
    lastGeneratedCode.value = nextCode
  }

  function normalizeCodeSegment(value) {
    let segment = String(value || '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_]+/g, '_')
      .replace(/^_+|_+$/g, '')
    if (segment && !/^[a-z]/.test(segment))
      segment = `x_${segment}`
    return segment
  }

  function contextualCode(code) {
    return code && props.initialContext?.lockApplication
      ? `app_${props.initialContext.applicationId}.${code}`
      : code
  }

  function publishFromContext(type, data, fallback) {
    return props.initialContext?.lockApplication
      ? publishApplicationCapability(props.initialContext.applicationId, type, data)
      : fallback(data)
  }

  async function handleSubmit() {
    if (submitting.value || submitDisabled.value || !validateTechnicalFields())
      return
    submitting.value = true
    try {
      await formRef.value?.validate()
    }
    catch {
      submitting.value = false
      return
    }
    if (form.sourceType === 'SYSTEM_SERVICE' && systemKind.value === 'FLOW' && !validateSystemVariables()) {
      submitting.value = false
      return
    }

    submitting.value = true
    try {
      const common = {
        capabilityCode: form.capabilityCode,
        version: form.version,
        suiteCode: form.suiteCode,
        objectCode: form.objectCode,
        description: form.description || null,
      }
      let res
      if (form.sourceType === 'BUSINESS_ACTION') {
        res = await publishFromContext('action', {
          ...common,
          actionCode: form.actionCode,
          allowedFields: form.allowedFields,
          requiredFields: form.requiredFields,
        }, publishBusinessActionCapability)
      }
      else if (form.sourceType === 'FLOW_ACTION') {
        res = await publishFromContext('flow', {
          ...common,
          operation: form.operation,
          allowedFields: form.operation === 'SUBMIT' ? form.allowedFields : [],
          requiredFields: form.operation === 'SUBMIT' ? form.requiredFields : [],
        }, publishFlowActionCapability)
      }
      else {
        res = await publishFromContext('system', {
          serviceCode: form.systemServiceCode,
          capabilityCode: form.capabilityCode,
          version: form.version,
          description: form.description || null,
          parameters: systemKind.value === 'REST'
            ? { endpointId: form.systemEndpointId }
            : systemKind.value === 'FORM'
              ? {
                  suiteCode: selectedForm.value.suiteCode,
                  objectCode: selectedForm.value.objectCode,
                  allowedFields: form.allowedFields,
                  requiredFields: form.requiredFields,
                }
              : {
                  modelId: form.systemModelId,
                  variables: form.systemVariables.map(variable => ({
                    name: variable.name.trim(),
                    type: variable.type,
                    description: variable.description.trim(),
                    required: variable.required,
                  })),
                },
        }, publishSystemServiceCapability)
      }
      if (res.code === 200) {
        window.$message.success(isUpgrade.value
          ? `能力新版本 ${form.version} 已发布`
          : '能力已注册并发布')
        if (isUpgrade.value) {
          window.$message.warning('固定版本授权不会自动切换，请到授权管理修改版本，或改用“跟随主版本”策略')
        }
        emit('update:show', false)
        emit('success', { id: res.data, version: form.version, upgrade: isUpgrade.value })
      }
    }
    finally {
      submitting.value = false
    }
  }

  function validateSystemVariables() {
    const names = new Set()
    for (const [index, variable] of form.systemVariables.entries()) {
      const name = variable.name.trim()
      if (!/^[a-z]\w{0,63}$/i.test(name)) {
        window.$message.error(`第 ${index + 1} 个流程变量名称无效，只能以字母开头并包含字母、数字、下划线`)
        return false
      }
      if (names.has(name)) {
        window.$message.error(`流程变量名称重复：${name}`)
        return false
      }
      names.add(name)
      if (!variable.description.trim()) {
        window.$message.error(`请填写流程变量 ${name} 的业务含义和取值说明`)
        return false
      }
    }
    return true
  }

  return {
    step,
    scenario,
    advancedSource,
    chooseScenario,
    nextStep,
    selectApplicationPage,
    handleSystemFormChange,
    systemKind,
    selectedEndpoint,
    selectedForm,
    reviewSource,
    reviewFields,
    router,
    dict,
    dictLoading,
    dictErrors,
    reloadCapabilityDicts,
    formRef,
    objectLoading,
    detailLoading,
    systemSourceLoading,
    sourceLoading,
    submitting,
    draftLoading,
    sourceError,
    flowSourceError,
    flowSource,
    businessActionSource,
    objects,
    actions,
    fields,
    systemServices,
    lastGeneratedCode,
    variableKeySequence,
    form,
    flowOperationOptions,
    flowOperationDictLoaded,
    flowOperationDictError,
    flowSubmitOptionMissing,
    flowSubmissionFieldOptions,
    flowRequiredSourceFields,
    flowRequiredFieldOptions,
    selectedSystemService,
    selectedSystemServiceActorLabel,
    selectedSystemServiceRiskLabel,
    systemServiceOptions,
    systemModelOptions,
    systemVariableTypeOptions,
    isUpgrade,
    modalTitle,
    submitText,
    submitDisabled,
    rules,
    isPositiveId,
    actionAvailabilitySuffix,
    objectOptions,
    selectedBusinessAction,
    actionOptions,
    recommendFlowSubmission,
    businessActionNotice,
    isCreatePageAction,
    fieldOptions,
    requiredFieldOptions,
    resolveDictLabel,
    sameStringArray,
    resetForm,
    initializeUpgrade,
    initializeObjectCapabilityUpgrade,
    initializeSystemServiceUpgrade,
    resolveDefaultOperation,
    preferredFlowOperation,
    loadObjects,
    loadSystemServices,
    handleSourceTypeChange,
    handleObjectChange,
    handleActionChange,
    openBusinessActionDesigner,
    switchToFlowSubmission,
    reloadFlowOperationOptions,
    handleOperationChange,
    applyFlowSubmissionDefaults,
    handleSystemServiceChange,
    addSystemVariable,
    removeSystemVariable,
    variableTypeLabel,
    updateGeneratedCode,
    normalizeCodeSegment,
    handleSubmit,
    validateSystemVariables,
  }

  async function chooseScenario(value) {
    if (!props.show || submitting.value)
      return
    scenario.value = value
    step.value = 2
    form.sourceType = value === 'application' ? (props.allowedTypes.includes('SYSTEM_SERVICE') ? 'SYSTEM_SERVICE' : 'BUSINESS_ACTION') : value === 'rest' ? 'SYSTEM_SERVICE' : 'FLOW_ACTION'
    if (!props.allowedTypes.includes(form.sourceType))
      form.sourceType = props.allowedTypes[0]
    await handleSourceTypeChange()
  }

  async function nextStep() {
    if (step.value === 1) {
      await chooseScenario(scenario.value)
      return
    }
    try {
      await formRef.value?.validate()
    }
    catch { return }
    if (form.sourceType === 'SYSTEM_SERVICE' && systemKind.value === 'FLOW' && !validateSystemVariables())
      return
    if (!submitDisabled.value && validateTechnicalFields())
      step.value = 3
  }

  function validateTechnicalFields() {
    if (!/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)*$/.test(form.capabilityCode) || form.capabilityCode.length > 128) {
      window.$message.error('请检查“接口标识与版本”中的能力编码，须为不超过128字符的小写点分编码')
      return false
    }
    if (!/^(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$/.test(form.version)) {
      window.$message.error('请检查版本号，例如 1.0.0')
      return false
    }
    return true
  }

  function handleSystemFormChange() {
    form.allowedFields = (selectedForm.value?.fields || []).map(field => field.field)
    form.requiredFields = (selectedForm.value?.fields || []).filter(field => field.required).map(field => field.field)
    updateGeneratedCode()
  }

  async function selectApplicationPage(page) {
    if (page && objects.value.length === 0)
      await loadObjects()
    form.objectId = page?.objectId || null
    await handleObjectChange(form.objectId)
  }
}
