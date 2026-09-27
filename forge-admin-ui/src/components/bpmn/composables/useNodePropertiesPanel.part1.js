/** NodePropertiesPanel.vue setup part 1. */
import { computed, nextTick, onMounted, reactive, ref, toRaw, watch } from 'vue'
import flowApi from '@/api/flow'
import UserSelectModal from '@/components/common/UserSelectModal.vue'
import FlowFormCreateDesigner from '@/components/form-create/FlowFormCreateDesigner.vue'
import FlowFormCreateRenderer from '@/components/form-create/FlowFormCreateRenderer.vue'
import { cloneValue, normalizeFormCreateRules } from '@/components/form-create/formCreateBridge'
import { request } from '@/utils/http'
export function applyNodePropertiesPanelPart1(props, emit) {
  const __impl = {}
  const mut = {
    spelSaveTimer: null,
  }
  const addExecutionListener = (...args) => __impl.addExecutionListener(...args)
  const addTaskListener = (...args) => __impl.addTaskListener(...args)
  const applyCandidateVariable = (...args) => __impl.applyCandidateVariable(...args)
  const applyConditionBuilder = (...args) => __impl.applyConditionBuilder(...args)
  const applySpelTemplate = (...args) => __impl.applySpelTemplate(...args)
  const buildNodeFormKey = (...args) => __impl.buildNodeFormKey(...args)
  const clearNodeInlineForm = (...args) => __impl.clearNodeInlineForm(...args)
  const countFormRules = (...args) => __impl.countFormRules(...args)
  const extractLegacyFixedAssigneeUserId = (...args) => __impl.extractLegacyFixedAssigneeUserId(...args)
  const getImplementationPlaceholder = (...args) => __impl.getImplementationPlaceholder(...args)
  const handleFormKeyChange = (...args) => __impl.handleFormKeyChange(...args)
  const handleSaveNodeFormSchema = (...args) => __impl.handleSaveNodeFormSchema(...args)
  const insertSpelVariable = (...args) => __impl.insertSpelVariable(...args)
  const loadFormSchemaByKey = (...args) => __impl.loadFormSchemaByKey(...args)
  const loadSequenceFlowProperties = (...args) => __impl.loadSequenceFlowProperties(...args)
  const loadServiceTaskProperties = (...args) => __impl.loadServiceTaskProperties(...args)
  const loadStartEventProperties = (...args) => __impl.loadStartEventProperties(...args)
  const loadUserTaskProperties = (...args) => __impl.loadUserTaskProperties(...args)
  const normalizeFixedAssigneeUserId = (...args) => __impl.normalizeFixedAssigneeUserId(...args)
  const openNodeFormDesigner = (...args) => __impl.openNodeFormDesigner(...args)
  const openNodeFormPreview = (...args) => __impl.openNodeFormPreview(...args)
  const readBooleanAttr = (...args) => __impl.readBooleanAttr(...args)
  const removeExecutionListener = (...args) => __impl.removeExecutionListener(...args)
  const removeTaskListener = (...args) => __impl.removeTaskListener(...args)
  const resolveNodeFormRules = (...args) => __impl.resolveNodeFormRules(...args)
  const toggleCarbonCopyService = (...args) => __impl.toggleCarbonCopyService(...args)
  const toggleCondition = (...args) => __impl.toggleCondition(...args)
  const toggleDefault = (...args) => __impl.toggleDefault(...args)
  const updateApprovalControl = (...args) => __impl.updateApprovalControl(...args)
  const updateAsync = (...args) => __impl.updateAsync(...args)
  const updateCandidateGroups = (...args) => __impl.updateCandidateGroups(...args)
  const updateCandidateUsers = (...args) => __impl.updateCandidateUsers(...args)
  const updateCarbonCopyConfig = (...args) => __impl.updateCarbonCopyConfig(...args)
  const updateCondition = (...args) => __impl.updateCondition(...args)
  const updateConditionType = (...args) => __impl.updateConditionType(...args)
  const updateDueDate = (...args) => __impl.updateDueDate(...args)
  const updateExecutionListeners = (...args) => __impl.updateExecutionListeners(...args)
  const updateExtensionProperty = (...args) => __impl.updateExtensionProperty(...args)
  const updateFormType = (...args) => __impl.updateFormType(...args)
  const updateMultiInstance = (...args) => __impl.updateMultiInstance(...args)
  const updateProperty = (...args) => __impl.updateProperty(...args)
  const updateServiceImplementation = (...args) => __impl.updateServiceImplementation(...args)
  const updateTaskListeners = (...args) => __impl.updateTaskListeners(...args)
  const updateTaskType = (...args) => __impl.updateTaskType(...args)
  const updateUserTaskAssignee = (...args) => __impl.updateUserTaskAssignee(...args)
  const validateSpelExpression = (...args) => __impl.validateSpelExpression(...args)
  const activeTab = ref('basic')
  const tabsWrapperRef = ref(null)

  // 保存状态
  const isDirty = ref(false)
  const saving = ref(false)

  // SPEL模板（从API加载）
  const spelTemplatesFromApi = ref([])

  // 会签比例预设值
  const passRatePresets = [
    { label: '过半', value: 50 },
    { label: '2/3', value: 67 },
    { label: '3/4', value: 75 },
    { label: '全部', value: 100 },
  ]

  // 会签比例滑块刻度
  const passRateMarks = { 50: '50%', 67: '2/3', 75: '75%', 100: '100%' }

  // 使用全局 message 实例（保留供未来使用）
  // const _message = window.$message

  // 获取原始元素（避免 Vue 代理与 bpmn-js 冲突）
  const rawElement = computed(() => toRaw(props.element))

  // 元素类型
  const elementType = computed(() => rawElement.value?.type || '')

  // 是否显示执行监听器
  const showExecutionListener = computed(() => {
    return ['bpmn:UserTask', 'bpmn:ServiceTask', 'bpmn:ScriptTask', 'bpmn:StartEvent', 'bpmn:EndEvent'].includes(elementType.value)
  })

  const visibleTabs = computed(() => {
    const tabs = [{ name: 'basic', label: '基础属性' }]
    if (elementType.value === 'bpmn:StartEvent')
      tabs.push({ name: 'startConfig', label: '开始配置' })
    if (elementType.value === 'bpmn:UserTask') {
      tabs.push(
        { name: 'approval', label: '审批设置' },
        { name: 'actionControl', label: '办理控制' },
        { name: 'multiInstance', label: '会签配置' },
        { name: 'listener', label: '监听器' },
      )
    }
    if (elementType.value === 'bpmn:ServiceTask')
      tabs.push({ name: 'service', label: '服务配置' })
    if (elementType.value === 'bpmn:ExclusiveGateway')
      tabs.push({ name: 'gateway', label: '网关配置' })
    if (elementType.value === 'bpmn:SequenceFlow')
      tabs.push({ name: 'sequence', label: '流转条件' })
    if (elementType.value === 'bpmn:EndEvent')
      tabs.push({ name: 'end', label: '结束配置' })
    if (showExecutionListener.value)
      tabs.push({ name: 'executionListener', label: '执行监听器' })
    return tabs
  })

  const activeTabIndex = computed(() => Math.max(0, visibleTabs.value.findIndex(tab => tab.name === activeTab.value)))
  const canGoPrevTab = computed(() => activeTabIndex.value > 0)
  const canGoNextTab = computed(() => activeTabIndex.value < visibleTabs.value.length - 1)
  const activeTabPositionText = computed(() => {
    const total = visibleTabs.value.length
    if (!total)
      return '0/0'
    return `${activeTabIndex.value + 1}/${total} ${visibleTabs.value[activeTabIndex.value]?.label || ''}`
  })

  // 属性对象
  const properties = reactive({
    id: '',
    name: '',
    documentation: '',
    // 用户任务
    taskType: 'assignee',
    assignee: '',
    assigneeUserId: '',
    assigneeExpr: '',
    assigneeUserName: '',
    candidateUsers: [],
    candidateUserNames: [],
    candidateGroups: [],
    candidateGroupNames: [],
    formType: 'dynamic',
    formKey: '',
    formJson: '',
    formUrl: '',
    priority: 50,
    dueDate: 0,
    allowApprove: true,
    allowReject: true,
    allowDelegate: true,
    allowReturn: false,
    allowTerminate: false,
    requireSignature: false,
    requireComment: true,
    // 多实例
    multiInstanceType: 'none',
    completionCondition: 'all',
    passRate: 100,
    // 任务监听器
    taskListeners: [],
    // 执行监听器
    executionListeners: [],
    // 服务任务
    flowableType: '',
    ccReceiverType: 'users',
    ccExpression: '',
    ccExpressionTarget: 'users',
    implementationType: 'class',
    implementation: '',
    async: false,
    // 序列流
    hasCondition: false,
    conditionType: 'expression',
    conditionPreset: 'custom',
    condition: '',
    script: '',
    scriptFormat: 'javascript',
    isDefault: false,
    // 开始节点
    initiator: 'initiator',
    // 结束节点
    endType: 'normal',
  })

  // 选项配置
  const taskTypeOptions = [
    { label: '指定审批人', value: 'assignee' },
    { label: '候选用户', value: 'candidateUsers' },
    { label: '候选组(角色)', value: 'candidateGroups' },
  ]

  const staticAssigneeOptions = [
    { label: '发起人', value: '$' + '{initiator}' },
    { label: '发起人上级', value: '$' + '{initiatorLeader}' },
    { label: '部门经理', value: '$' + '{deptManager}' },
    { label: 'HR', value: '$' + '{hr}' },
    { label: '指定用户', value: 'custom' },
    { label: 'SPEL 表达式', value: 'spel' },
  ]

  const systemVariableOptions = [
    { label: '发起人', value: 'initiator', source: 'system' },
    { label: '发起人ID', value: 'startUserId', source: 'system' },
    { label: '发起部门ID', value: 'startDeptId', source: 'system' },
    { label: '业务键', value: 'businessKey', source: 'system' },
    { label: '流程实例ID', value: 'processInstanceId', source: 'system' },
    { label: '流程入口编码', value: 'flowEntryCode', source: 'system' },
    { label: '表单实例ID', value: 'flowFormInstanceId', source: 'system' },
  ]

  const formVariableOptions = computed(() => {
    return (props.fieldCatalog || [])
      .filter(item => item?.field)
      .map(item => ({
        label: item.label ? `${item.label}（${item.field}）` : item.field,
        value: item.field,
        source: item.source || 'form',
        componentType: item.componentType || '',
        dataType: item.dataType || '',
      }))
  })

  const variableCatalogOptions = computed(() => [
    {
      type: 'group',
      label: '表单字段',
      key: 'form-fields',
      children: formVariableOptions.value,
    },
    {
      type: 'group',
      label: '系统变量',
      key: 'system-vars',
      children: systemVariableOptions,
    },
  ])

  const assigneeOptions = computed(() => {
    const formAssignees = formVariableOptions.value.map(item => ({
      label: `表单字段：${item.label}`,
      value: toExpression(item.value),
    }))
    return [
      ...staticAssigneeOptions,
      ...(formAssignees.length
        ? [{
            type: 'group',
            label: '表单字段',
            key: 'form-assignee-fields',
            children: formAssignees,
          }]
        : []),
    ]
  })

  const conditionOperatorOptions = [
    { label: '等于', value: '==' },
    { label: '不等于', value: '!=' },
    { label: '大于', value: '>' },
    { label: '大于等于', value: '>=' },
    { label: '小于', value: '<' },
    { label: '小于等于', value: '<=' },
    { label: '包含', value: 'contains' },
  ]

  const approvalResultConditionOptions = [
    {
      label: '同意通过',
      value: 'approve',
      expression: '$' + '{approvalResult == \'approve\'}',
      icon: 'i-material-symbols:check-circle',
      desc: '审批人点击同意后走这条线',
    },
    {
      label: '驳回修改',
      value: 'reject',
      expression: '$' + '{approvalResult == \'reject\'}',
      icon: 'i-material-symbols:edit-note',
      desc: '审批人点击驳回修改后走这条线',
    },
    {
      label: '退回上一步',
      value: 'return',
      expression: '$' + '{approvalResult == \'return\'}',
      icon: 'i-material-symbols:keyboard-return',
      desc: '审批人点击退回时走这条线',
    },
    {
      label: '终止流程',
      value: 'terminate',
      expression: '$' + '{approvalResult == \'terminate\'}',
      icon: 'i-material-symbols:stop-circle',
      desc: '审批人点击终止时走这条线',
    },
    {
      label: '按业务字段判断',
      value: 'custom',
      expression: '',
      icon: 'i-material-symbols:tune',
      desc: '按金额、部门、类型等字段配置条件',
    },
  ]

  const formTypeOptions = [
    { label: '动态表单', value: 'dynamic' },
    { label: '外部表单', value: 'external' },
    { label: '无表单', value: 'none' },
  ]

  const nodeInlineFormRules = computed(() => normalizeFormCreateRules(properties.formJson))
  const nodeFormFieldCount = computed(() => countFormRules(nodeInlineFormRules.value))
  const canPreviewNodeForm = computed(() => Boolean(properties.formJson?.trim() || properties.formKey?.trim()))
  const nodeFormDesignerTitle = computed(() => `节点表单设计 - ${properties.name || properties.id || '未命名节点'}`)
  const nodeFormPreviewTitle = computed(() => `节点表单预览 - ${properties.name || properties.id || '未命名节点'}`)
  const nodeFormBuilderTitle = computed(() => {
    if (nodeFormFieldCount.value > 0)
      return `已内嵌 ${nodeFormFieldCount.value} 个字段`
    if (properties.formKey)
      return '引用已有动态表单'
    return '未配置节点表单'
  })
  const nodeFormBuilderDesc = computed(() => {
    if (nodeFormFieldCount.value > 0)
      return '办理该节点时会优先渲染这里设计的表单，并把填写内容提交为流程变量'
    if (properties.formKey)
      return `当前引用 ${properties.formKey}，也可以点击在线设计生成节点专属表单`
    return '点击在线设计为当前审批节点配置专属字段、校验和布局'
  })

  const approvalActionOptions = [
    { key: 'allowApprove', label: '通过', icon: 'i-material-symbols:check-circle' },
    { key: 'allowReject', label: '拒绝', icon: 'i-material-symbols:cancel' },
    { key: 'allowReturn', label: '退回', icon: 'i-material-symbols:keyboard-return' },
    { key: 'allowTerminate', label: '终结流程', icon: 'i-material-symbols:stop-circle' },
    { key: 'allowDelegate', label: '转办', icon: 'i-material-symbols:person-add' },
  ]

  // 用户选择相关
  const showUserSelect = ref(false)
  const userSelectTitle = ref('选择用户')
  const userSelectMultiple = ref(false)
  const userSelectType = ref('')
  const currentSelectedUsers = ref([])

  // 角色选择相关
  const showRoleSelect = ref(false)
  const roleList = ref([])
  const roleLoading = ref(false)
  const checkedRoleKeys = ref([])

  // 角色表格列
  const roleColumns = [
    { type: 'selection' },
    { title: '角色名称', key: 'roleName' },
    { title: '角色编码', key: 'roleKey' },
  ]

  // 节点动态表单
  const formDefinitionOptions = ref([])
  const formDefinitionLoading = ref(false)
  const showNodeFormDesigner = ref(false)
  const showNodeFormPreview = ref(false)
  const nodeFormDesignerRef = ref(null)
  const nodeFormDesignerSchema = ref([])
  const nodeFormPreviewSchema = ref([])

  // SPEL表达式相关
  const selectedSpelTemplate = ref('')
  const spelValidationError = ref('')
  const selectedSpelVariable = ref(null)
  const conditionBuilder = reactive({
    field: '',
    operator: '==',
    value: '',
  })

  function toExpression(field) {
    return '$' + `{${field}}`
  }

  function unwrapExpression(value) {
    const text = String(value || '').trim()
    const prefix = '$' + '{'
    if (text.startsWith(prefix) && text.endsWith('}'))
      return text.slice(2, -1).trim()
    return ''
  }

  function isSimpleVariableExpression(value) {
    const inner = unwrapExpression(value)
    return /^[A-Z_][\w.]*$/i.test(inner)
  }

  function getCatalogField(field) {
    return formVariableOptions.value.find(item => item.value === field)
      || systemVariableOptions.find(item => item.value === field)
  }

  function quoteConditionValue(value) {
    const text = String(value ?? '').trim()
    if (text === '')
      return 'null'
    if (['true', 'false', 'null'].includes(text))
      return text
    if (/^-?\d+(?:\.\d+)?$/.test(text))
      return text
    if ((text.startsWith('\'') && text.endsWith('\'')) || (text.startsWith('"') && text.endsWith('"')))
      return text
    return `'${text.replaceAll('\'', '\\\'')}'`
  }

  function normalizeConditionExpression(value) {
    return String(value || '')
      .trim()
      .replaceAll('"', '\'')
      .replace(/\s+/g, '')
  }

  function findApprovalResultPresetByExpression(expression) {
    const normalized = normalizeConditionExpression(expression)
    return approvalResultConditionOptions.find(item =>
      item.expression && normalizeConditionExpression(item.expression) === normalized,
    )
  }

  function syncConditionPresetFromCondition() {
    if (!properties.condition) {
      properties.conditionPreset = 'custom'
      return
    }
    properties.conditionPreset = findApprovalResultPresetByExpression(properties.condition)?.value || 'custom'
  }

  function updateConditionPreset(value) {
    const preset = approvalResultConditionOptions.find(item => item.value === value)
    properties.hasCondition = true
    properties.conditionType = 'expression'
    properties.script = ''
    if (preset?.expression) {
      properties.condition = preset.expression
    }
    else if (value === 'custom' && findApprovalResultPresetByExpression(properties.condition)) {
      properties.condition = ''
    }
    updateCondition()
  }

  // 标记为未保存状态
  function markDirty() {
    isDirty.value = true
  }

  function switchRelativeTab(offset) {
    const tabs = visibleTabs.value
    const nextIndex = Math.min(Math.max(activeTabIndex.value + offset, 0), tabs.length - 1)
    activeTab.value = tabs[nextIndex]?.name || 'basic'
  }

  function scrollActiveTabIntoView() {
    const activeNode = tabsWrapperRef.value?.querySelector?.('.n-tabs-tab--active')
    activeNode?.scrollIntoView?.({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'center',
    })
  }

  // 从API加载SPEL模板
  async function loadSpelTemplates() {
    try {
      const res = await request.get('/api/flow/spelTemplate/list')
      if (res.code === 200) {
        spelTemplatesFromApi.value = (res.data || []).map(t => ({
          label: t.templateName,
          value: t.expression,
          description: t.description || '',
        }))
      }
    }
    catch (e) {
      console.error('加载SPEL模板失败', e)
    }
  }

  async function loadFormDefinitions() {
    formDefinitionLoading.value = true
    try {
      const res = await flowApi.getEnabledForms()
      if (res.code === 200) {
        formDefinitionOptions.value = (res.data || [])
          .filter(item => item.formKey)
          .map(item => ({
            label: item.formName ? `${item.formName}（${item.formKey}）` : item.formKey,
            value: item.formKey,
          }))
      }
    }
    catch (error) {
      console.error('加载流程表单失败:', error)
    }
    finally {
      formDefinitionLoading.value = false
    }
  }

  // 手动保存配置
  async function handleSaveConfig() {
    saving.value = true
    try {
      // 执行所有必要的update方法
      updateProperty('id')
      updateProperty('name')
      updateProperty('documentation')

      // 根据元素类型执行特定更新
      if (elementType.value === 'bpmn:StartEvent') {
        updateExtensionProperty('initiator')
        updateExtensionProperty('formKey')
      }

      if (elementType.value === 'bpmn:UserTask') {
        updateUserTaskAssignee()
        updateFormType()
        updateExtensionProperty('priority')
        updateDueDate()
        updateApprovalControl()
        updateMultiInstance()
        updateTaskListeners()
      }

      if (elementType.value === 'bpmn:ServiceTask') {
        updateServiceImplementation()
        updateCarbonCopyConfig()
        updateAsync()
      }

      if (elementType.value === 'bpmn:SequenceFlow') {
        updateCondition()
      }

      // 更新执行监听器
      if (showExecutionListener.value) {
        updateExecutionListeners()
      }

      // 触发父组件更新
      emit('update')

      isDirty.value = false
      window.$message?.success('配置已保存')
    }
    catch (error) {
      console.error('保存配置失败:', error)
      window.$message?.error('保存失败')
    }
    finally {
      saving.value = false
    }
  }

  // 组件挂载时加载SPEL模板
  onMounted(() => {
    loadSpelTemplates()
    loadFormDefinitions()
  })

  const completionConditionOptions = [
    { label: '全部通过', value: 'all' },
    { label: '任一通过', value: 'any' },
    { label: '按比例通过', value: 'rate' },
  ]

  // 通过比例描述文字
  const passRateDesc = computed(() => {
    const rate = properties.passRate
    if (rate >= 100)
      return '需要所有审批人全部同意才能通过'
    if (rate === 50)
      return `需要超过一半的审批人同意才能通过`
    if (rate === 67)
      return '需要 2/3 以上的审批人同意才能通过'
    if (rate === 75)
      return '需要 3/4 以上的审批人同意才能通过'
    return `需要至少 ${rate}% 的审批人同意才能通过`
  })

  // 设置预设比例并触发更新
  function setPassRate(value) {
    properties.passRate = value
    updateMultiInstance()
  }

  const taskEventOptions = [
    { label: '创建(create)', value: 'create' },
    { label: '分配(assignment)', value: 'assignment' },
    { label: '完成(complete)', value: 'complete' },
    { label: '删除(delete)', value: 'delete' },
  ]

  const executionEventOptions = [
    { label: '开始(start)', value: 'start' },
    { label: '结束(end)', value: 'end' },
    { label: '执行(take)', value: 'take' },
  ]

  const implementationTypeOptions = [
    { label: 'Java类', value: 'class' },
    { label: '表达式', value: 'expression' },
    { label: '委托表达式', value: 'delegateExpression' },
  ]

  const carbonCopyImplementationTypeOptions = [
    { label: '平台默认/委托表达式', value: 'delegateExpression' },
    { label: '表达式', value: 'expression' },
    { label: 'Java类', value: 'class' },
  ]

  const carbonCopyReceiverTypeOptions = [
    { label: '指定人员', value: 'users' },
    { label: '指定角色', value: 'roles' },
    { label: '表达式', value: 'expression' },
  ]

  const carbonCopyExpressionTargetOptions = [
    { label: '表达式返回人员', value: 'users' },
    { label: '表达式返回角色', value: 'roles' },
  ]

  const carbonCopyRoleOptions = computed(() => {
    const selected = properties.candidateGroups.map((value, index) => ({
      label: properties.candidateGroupNames[index] || value,
      value,
      roleName: properties.candidateGroupNames[index] || value,
      roleKey: value,
    }))
    return mergeSelectOptions(roleList.value.map(normalizeRoleOption).filter(Boolean), selected)
  })

  const scriptFormatOptions = [
    { label: 'JavaScript', value: 'javascript' },
    { label: 'Groovy', value: 'groovy' },
    { label: 'JUEL', value: 'juel' },
  ]

  // 监听元素变化，加载属性
  watch(() => props.element, (newElement) => {
    if (newElement) {
      loadElementProperties(toRaw(newElement))
      // 自动修复：老模型可能缺少 loopCardinality，打开时自动补全
      nextTick(() => {
        if (properties.multiInstanceType !== 'none' && rawElement.value?.type === 'bpmn:UserTask') {
          const bo = rawElement.value.businessObject
          if (bo?.loopCharacteristics && !bo.loopCharacteristics.loopCardinality) {
            updateMultiInstance()
          }
        }
      })
      // 切换节点时回到第一个Tab
      activeTab.value = 'basic'
      nextTick(scrollActiveTabIntoView)
    }
  }, { immediate: true })

  watch(activeTab, () => {
    nextTick(scrollActiveTabIntoView)
  })

  watch(() => properties.assigneeExpr, (newVal) => {
    if (properties.assignee !== 'spel' || !newVal)
      return
    clearTimeout(mut.spelSaveTimer)
    mut.spelSaveTimer = setTimeout(() => {
      updateUserTaskAssignee()
    }, 500)
  })

  watch(formVariableOptions, () => {
    if (properties.assignee === 'spel' && isSimpleVariableExpression(properties.assigneeExpr)) {
      const field = unwrapExpression(properties.assigneeExpr)
      if (getCatalogField(field)) {
        properties.assignee = properties.assigneeExpr
        properties.assigneeUserId = ''
        properties.assigneeExpr = ''
        properties.assigneeUserName = ''
      }
    }
  })

  // 打开用户选择弹窗
  function openUserSelect(type) {
    userSelectType.value = type
    if (type === 'assignee') {
      userSelectTitle.value = '选择审批人'
      userSelectMultiple.value = false
      // 如果已有选中的用户，回显
      const userId = normalizeFixedAssigneeUserId(properties.assigneeUserId)
        || extractLegacyFixedAssigneeUserId(properties.assigneeExpr)
      currentSelectedUsers.value = userId
        ? [{ id: userId, nickName: properties.assigneeUserName }]
        : []
    }
    else if (type === 'candidateUsers') {
      userSelectTitle.value = '选择候选用户'
      userSelectMultiple.value = true
      // 回显已选候选用户
      if (properties.candidateUsers.length > 0) {
        currentSelectedUsers.value = properties.candidateUsers.map((id, index) => ({
          id: String(id),
          nickName: properties.candidateUserNames[index] || '',
        }))
      }
      else {
        currentSelectedUsers.value = []
      }
    }
    else if (type === 'carbonCopyUsers') {
      userSelectTitle.value = '选择抄送人'
      userSelectMultiple.value = true
      if (properties.candidateUsers.length > 0) {
        currentSelectedUsers.value = properties.candidateUsers.map((id, index) => ({
          id: String(id),
          nickName: properties.candidateUserNames[index] || '',
        }))
      }
      else {
        currentSelectedUsers.value = []
      }
    }
    showUserSelect.value = true
  }

  // 用户选择确认
  function handleUserSelectConfirm(users) {
    if (userSelectType.value === 'assignee') {
      const user = Array.isArray(users) ? users[0] : users
      if (user) {
        properties.assignee = 'custom'
        properties.assigneeUserId = String(user.id)
        properties.assigneeExpr = ''
        properties.assigneeUserName = user.nickName || user.userName
        updateUserTaskAssignee()
      }
    }
    else if (userSelectType.value === 'candidateUsers') {
      const userList = Array.isArray(users) ? users : [users]
      userList.forEach((user) => {
        if (!properties.candidateUsers.includes(user.id.toString())) {
          properties.candidateUsers.push(user.id.toString())
          properties.candidateUserNames.push(user.nickName || user.userName)
        }
      })
      updateCandidateUsers()
    }
    else if (userSelectType.value === 'carbonCopyUsers') {
      const userList = Array.isArray(users) ? users : [users]
      properties.ccReceiverType = 'users'
      properties.ccExpression = ''
      properties.ccExpressionTarget = 'users'
      properties.candidateGroups = []
      properties.candidateGroupNames = []
      userList.filter(Boolean).forEach((user) => {
        if (!properties.candidateUsers.includes(user.id.toString())) {
          properties.candidateUsers.push(user.id.toString())
          properties.candidateUserNames.push(user.nickName || user.realName || user.name || user.userName)
        }
      })
      updateCarbonCopyConfig()
    }
    showUserSelect.value = false
  }

  // 清除审批人
  function clearAssigneeUser() {
    properties.assignee = ''
    properties.assigneeUserId = ''
    properties.assigneeExpr = ''
    properties.assigneeUserName = ''
    updateUserTaskAssignee()
  }

  // 移除候选用户
  function removeCandidateUser(index) {
    properties.candidateUsers.splice(index, 1)
    properties.candidateUserNames.splice(index, 1)
    updateCandidateUsers()
  }

  function removeCarbonCopyUser(index) {
    properties.candidateUsers.splice(index, 1)
    properties.candidateUserNames.splice(index, 1)
    updateCarbonCopyConfig()
  }

  // 打开角色选择弹窗
  async function openRoleSelect() {
    // 回显已选角色
    if (properties.candidateGroups.length > 0) {
      checkedRoleKeys.value = properties.candidateGroups.map(id => Number.parseInt(id))
    }
    else {
      checkedRoleKeys.value = []
    }
    showRoleSelect.value = true
    await loadRoleList()
  }

  // 加载角色列表
  async function loadRoleList(keyword = '') {
    roleLoading.value = true
    try {
      const res = await request.get('/system/role/page', {
        params: {
          pageNum: 1,
          pageSize: 1000,
          roleName: typeof keyword === 'string' ? keyword || undefined : undefined,
        },
      })
      if (res.code === 200 && res.data?.records) {
        roleList.value = res.data.records
      }
    }
    catch (error) {
      console.error('加载角色列表失败:', error)
    }
    finally {
      roleLoading.value = false
    }
  }

  function normalizeRoleOption(role) {
    const value = isFilled(role?.roleKey) ? role.roleKey : role?.id
    if (!isFilled(value))
      return null
    const label = String(role.roleName || role.roleKey || value)
    return {
      label,
      value: String(value),
      roleName: label,
      roleKey: String(value),
    }
  }

  function mergeSelectOptions(primary = [], append = []) {
    const map = new Map()
    for (const option of [...append, ...primary]) {
      if (!option || !isFilled(option.value))
        continue
      map.set(String(option.value), option)
    }
    return Array.from(map.values())
  }

  function isFilled(value) {
    return value !== null && value !== undefined && String(value).trim() !== ''
  }

  // 角色选择
  function handleRoleCheck(keys) {
    checkedRoleKeys.value = keys
  }

  // 角色选择确认
  function handleRoleConfirm() {
    const selectedRoles = roleList.value.filter(r => checkedRoleKeys.value.includes(r.id))
    selectedRoles.forEach((role) => {
      if (!properties.candidateGroups.includes(role.id.toString())) {
        properties.candidateGroups.push(role.id.toString())
        properties.candidateGroupNames.push(role.roleName)
      }
    })
    updateCandidateGroups()
    showRoleSelect.value = false
  }

  // 移除候选组
  function removeCandidateGroup(index) {
    properties.candidateGroups.splice(index, 1)
    properties.candidateGroupNames.splice(index, 1)
    updateCandidateGroups()
  }

  function handleCarbonCopyReceiverTypeChange(value) {
    properties.ccReceiverType = value || 'users'
    if (properties.ccReceiverType === 'users') {
      properties.candidateGroups = []
      properties.candidateGroupNames = []
      properties.ccExpression = ''
      properties.ccExpressionTarget = 'users'
    }
    else if (properties.ccReceiverType === 'roles') {
      properties.candidateUsers = []
      properties.candidateUserNames = []
      properties.ccExpression = ''
      properties.ccExpressionTarget = 'roles'
      loadRoleList()
    }
    else {
      applyCarbonCopyExpressionToProperties()
    }
    updateCarbonCopyConfig()
  }

  function handleCarbonCopyRolesChange(values, selectedOptions = []) {
    const nextValues = normalizeList(values)
    const selectedOptionList = Array.isArray(selectedOptions) ? selectedOptions : selectedOptions ? [selectedOptions] : []
    const optionMap = new Map(carbonCopyRoleOptions.value.map(option => [String(option.value), option]))
    const selectedMap = new Map(selectedOptionList.map(option => [String(option.value), option]))
    properties.ccReceiverType = 'roles'
    properties.ccExpression = ''
    properties.ccExpressionTarget = 'roles'
    properties.candidateUsers = []
    properties.candidateUserNames = []
    properties.candidateGroups = nextValues
    properties.candidateGroupNames = nextValues.map((value) => {
      const option = selectedMap.get(String(value)) || optionMap.get(String(value))
      return option?.roleName || option?.label || value
    })
    updateCarbonCopyConfig()
  }

  function updateCarbonCopyExpression() {
    properties.ccReceiverType = 'expression'
    applyCarbonCopyExpressionToProperties()
    updateCarbonCopyConfig()
  }

  function applyCarbonCopyExpressionToProperties() {
    const expression = normalizeCarbonCopyExpression(properties.ccExpression)
    properties.ccExpression = expression
    const label = expression ? ['表达式配置'] : []
    if (properties.ccExpressionTarget === 'roles') {
      properties.candidateUsers = []
      properties.candidateUserNames = []
      properties.candidateGroups = expression ? [expression] : []
      properties.candidateGroupNames = label
      return
    }
    properties.candidateUsers = expression ? [expression] : []
    properties.candidateUserNames = label
    properties.candidateGroups = []
    properties.candidateGroupNames = []
  }

  function normalizeCarbonCopyExpression(value) {
    const text = String(value || '').trim()
    if (!text)
      return ''
    if (text.startsWith('$' + '{') && text.endsWith('}'))
      return text
    return '$' + `{${text}}`
  }

  function normalizeList(value) {
    if (Array.isArray(value))
      return value.map(item => String(item ?? '').trim()).filter(Boolean)
    if (!isFilled(value))
      return []
    return String(value).split(/[,，\s]+/).map(item => item.trim()).filter(Boolean)
  }

  function findCarbonCopyExpression(values) {
    return normalizeList(values).find(value => value.startsWith('$' + '{') && value.endsWith('}')) || ''
  }

  function inferCarbonCopyReceiverType(candidateUsers, candidateGroups, configuredType) {
    if (configuredType)
      return configuredType
    if (findCarbonCopyExpression(candidateUsers) || findCarbonCopyExpression(candidateGroups))
      return 'expression'
    if (normalizeList(candidateGroups).length)
      return 'roles'
    return 'users'
  }

  // 加载元素属性
  function loadElementProperties(element) {
    const bo = element.businessObject
    if (!bo)
      return

    // 基础属性
    properties.id = bo.id || ''
    properties.name = bo.name || ''

    // 文档
    const docs = bo.documentation || []
    properties.documentation = docs.length > 0 ? docs[0].text : ''

    // 根据元素类型加载不同属性
    if (element.type === 'bpmn:UserTask') {
      loadUserTaskProperties(bo)
    }
    else if (element.type === 'bpmn:ServiceTask') {
      loadServiceTaskProperties(bo)
    }
    else if (element.type === 'bpmn:SequenceFlow') {
      loadSequenceFlowProperties(bo)
    }
    else if (element.type === 'bpmn:StartEvent') {
      loadStartEventProperties(bo)
    }
  }

  // 加载用户任务属性
  __impl.toExpression = toExpression
  __impl.unwrapExpression = unwrapExpression
  __impl.isSimpleVariableExpression = isSimpleVariableExpression
  __impl.getCatalogField = getCatalogField
  __impl.quoteConditionValue = quoteConditionValue
  __impl.normalizeConditionExpression = normalizeConditionExpression
  __impl.findApprovalResultPresetByExpression = findApprovalResultPresetByExpression
  __impl.syncConditionPresetFromCondition = syncConditionPresetFromCondition
  __impl.updateConditionPreset = updateConditionPreset
  __impl.markDirty = markDirty
  __impl.switchRelativeTab = switchRelativeTab
  __impl.scrollActiveTabIntoView = scrollActiveTabIntoView
  __impl.loadSpelTemplates = loadSpelTemplates
  __impl.loadFormDefinitions = loadFormDefinitions
  __impl.handleSaveConfig = handleSaveConfig
  __impl.setPassRate = setPassRate
  __impl.openUserSelect = openUserSelect
  __impl.handleUserSelectConfirm = handleUserSelectConfirm
  __impl.clearAssigneeUser = clearAssigneeUser
  __impl.removeCandidateUser = removeCandidateUser
  __impl.removeCarbonCopyUser = removeCarbonCopyUser
  __impl.openRoleSelect = openRoleSelect
  __impl.loadRoleList = loadRoleList
  __impl.normalizeRoleOption = normalizeRoleOption
  __impl.mergeSelectOptions = mergeSelectOptions
  __impl.isFilled = isFilled
  __impl.handleRoleCheck = handleRoleCheck
  __impl.handleRoleConfirm = handleRoleConfirm
  __impl.removeCandidateGroup = removeCandidateGroup
  __impl.handleCarbonCopyReceiverTypeChange = handleCarbonCopyReceiverTypeChange
  __impl.handleCarbonCopyRolesChange = handleCarbonCopyRolesChange
  __impl.updateCarbonCopyExpression = updateCarbonCopyExpression
  __impl.applyCarbonCopyExpressionToProperties = applyCarbonCopyExpressionToProperties
  __impl.normalizeCarbonCopyExpression = normalizeCarbonCopyExpression
  __impl.normalizeList = normalizeList
  __impl.findCarbonCopyExpression = findCarbonCopyExpression
  __impl.inferCarbonCopyReceiverType = inferCarbonCopyReceiverType
  __impl.loadElementProperties = loadElementProperties

  return {
    props, emit, __impl, mut, addExecutionListener, addTaskListener, applyCandidateVariable, applyCarbonCopyExpressionToProperties,
    applyConditionBuilder, applySpelTemplate, buildNodeFormKey, clearAssigneeUser, clearNodeInlineForm, countFormRules, extractLegacyFixedAssigneeUserId, findApprovalResultPresetByExpression,
    findCarbonCopyExpression, getCatalogField, getImplementationPlaceholder, handleCarbonCopyReceiverTypeChange, handleCarbonCopyRolesChange, handleFormKeyChange, handleRoleCheck, handleRoleConfirm,
    handleSaveConfig, handleSaveNodeFormSchema, handleUserSelectConfirm, inferCarbonCopyReceiverType, insertSpelVariable, isFilled, isSimpleVariableExpression, loadElementProperties,
    loadFormDefinitions, loadFormSchemaByKey, loadRoleList, loadSequenceFlowProperties, loadServiceTaskProperties, loadSpelTemplates, loadStartEventProperties, loadUserTaskProperties,
    markDirty, mergeSelectOptions, normalizeCarbonCopyExpression, normalizeConditionExpression, normalizeFixedAssigneeUserId, normalizeList, normalizeRoleOption, openNodeFormDesigner,
    openNodeFormPreview, openRoleSelect, openUserSelect, quoteConditionValue, readBooleanAttr, removeCandidateGroup, removeCandidateUser, removeCarbonCopyUser,
    removeExecutionListener, removeTaskListener, resolveNodeFormRules, scrollActiveTabIntoView, setPassRate, switchRelativeTab, syncConditionPresetFromCondition, toExpression,
    toggleCarbonCopyService, toggleCondition, toggleDefault, unwrapExpression, updateApprovalControl, updateAsync, updateCandidateGroups, updateCandidateUsers,
    updateCarbonCopyConfig, updateCarbonCopyExpression, updateCondition, updateConditionPreset, updateConditionType, updateDueDate, updateExecutionListeners, updateExtensionProperty,
    updateFormType, updateMultiInstance, updateProperty, updateServiceImplementation, updateTaskListeners, updateTaskType, updateUserTaskAssignee, validateSpelExpression,
    activeTab, tabsWrapperRef, isDirty, saving, spelTemplatesFromApi, passRatePresets, passRateMarks, rawElement,
    elementType, showExecutionListener, visibleTabs, activeTabIndex, canGoPrevTab, canGoNextTab, activeTabPositionText, properties,
    taskTypeOptions, staticAssigneeOptions, systemVariableOptions, formVariableOptions, variableCatalogOptions, assigneeOptions, conditionOperatorOptions, approvalResultConditionOptions,
    formTypeOptions, nodeInlineFormRules, nodeFormFieldCount, canPreviewNodeForm, nodeFormDesignerTitle, nodeFormPreviewTitle, nodeFormBuilderTitle, nodeFormBuilderDesc,
    approvalActionOptions, showUserSelect, userSelectTitle, userSelectMultiple, userSelectType, currentSelectedUsers, showRoleSelect, roleList,
    roleLoading, checkedRoleKeys, roleColumns, formDefinitionOptions, formDefinitionLoading, showNodeFormDesigner, showNodeFormPreview, nodeFormDesignerRef,
    nodeFormDesignerSchema, nodeFormPreviewSchema, selectedSpelTemplate, spelValidationError, selectedSpelVariable, conditionBuilder, completionConditionOptions, passRateDesc,
    taskEventOptions, executionEventOptions, implementationTypeOptions, carbonCopyImplementationTypeOptions, carbonCopyReceiverTypeOptions, carbonCopyExpressionTargetOptions, carbonCopyRoleOptions, scriptFormatOptions,
  }
}
