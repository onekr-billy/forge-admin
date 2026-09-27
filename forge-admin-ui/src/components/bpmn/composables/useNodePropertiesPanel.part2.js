/** NodePropertiesPanel.vue setup part 2. */
import { computed, nextTick, onMounted, reactive, ref, toRaw, watch } from 'vue'
import flowApi from '@/api/flow'
import UserSelectModal from '@/components/common/UserSelectModal.vue'
import FlowFormCreateDesigner from '@/components/form-create/FlowFormCreateDesigner.vue'
import FlowFormCreateRenderer from '@/components/form-create/FlowFormCreateRenderer.vue'
import { cloneValue, normalizeFormCreateRules } from '@/components/form-create/formCreateBridge'
import { request } from '@/utils/http'
export function applyNodePropertiesPanelPart2(props, emit, deps = {}) {
  const {
    __impl, mut, applyCarbonCopyExpressionToProperties, clearAssigneeUser, findApprovalResultPresetByExpression, findCarbonCopyExpression, getCatalogField, handleCarbonCopyReceiverTypeChange,
    handleCarbonCopyRolesChange, handleRoleCheck, handleRoleConfirm, handleSaveConfig, handleUserSelectConfirm, inferCarbonCopyReceiverType, isFilled, isSimpleVariableExpression,
    loadElementProperties, loadFormDefinitions, loadRoleList, loadSpelTemplates, markDirty, mergeSelectOptions, normalizeCarbonCopyExpression, normalizeConditionExpression,
    normalizeList, normalizeRoleOption, openRoleSelect, openUserSelect, quoteConditionValue, removeCandidateGroup, removeCandidateUser, removeCarbonCopyUser,
    scrollActiveTabIntoView, setPassRate, switchRelativeTab, syncConditionPresetFromCondition, toExpression, unwrapExpression, updateCarbonCopyExpression, updateConditionPreset,
    activeTab, tabsWrapperRef, isDirty, saving, spelTemplatesFromApi, passRatePresets, passRateMarks, rawElement,
    elementType, showExecutionListener, visibleTabs, activeTabIndex, canGoPrevTab, canGoNextTab, activeTabPositionText, properties,
    taskTypeOptions, staticAssigneeOptions, systemVariableOptions, formVariableOptions, variableCatalogOptions, assigneeOptions, conditionOperatorOptions, approvalResultConditionOptions,
    formTypeOptions, nodeInlineFormRules, nodeFormFieldCount, canPreviewNodeForm, nodeFormDesignerTitle, nodeFormPreviewTitle, nodeFormBuilderTitle, nodeFormBuilderDesc,
    approvalActionOptions, showUserSelect, userSelectTitle, userSelectMultiple, userSelectType, currentSelectedUsers, showRoleSelect, roleList,
    roleLoading, checkedRoleKeys, roleColumns, formDefinitionOptions, formDefinitionLoading, showNodeFormDesigner, showNodeFormPreview, nodeFormDesignerRef,
    nodeFormDesignerSchema, nodeFormPreviewSchema, selectedSpelTemplate, spelValidationError, selectedSpelVariable, conditionBuilder, completionConditionOptions, passRateDesc,
    taskEventOptions, executionEventOptions, implementationTypeOptions, carbonCopyImplementationTypeOptions, carbonCopyReceiverTypeOptions, carbonCopyExpressionTargetOptions, carbonCopyRoleOptions, scriptFormatOptions,
  } = deps
  function loadUserTaskProperties(bo) {
    // moddleExtensions 注册后，flowable 属性直接挂载在 bo 上（无命名空间前缀）
    // 同时兼容 $attrs 里的原始属性（XML 里带前缀的情况）
    const attrs = bo.$attrs || {}

    // 审批人类型判断 - 先读直接属性，再读 $attrs 兼容
    const assignee = bo.assignee ?? attrs['flowable:assignee'] ?? ''
    const candidateUsers = bo.candidateUsers ?? attrs['flowable:candidateUsers'] ?? ''
    const candidateGroups = bo.candidateGroups ?? attrs['flowable:candidateGroups'] ?? ''

    // 读取保存的用户名/角色名（自定义扩展属性）
    const assigneeName = bo.assigneeName ?? attrs['flowable:assigneeName'] ?? ''
    const candidateUserNames = bo.candidateUserNames ?? attrs['flowable:candidateUserNames'] ?? ''
    const candidateGroupNames = bo.candidateGroupNames ?? attrs['flowable:candidateGroupNames'] ?? ''

    // 读取审批人类型标识（用于区分 SPEL 表达式）
    const assigneeType = bo.assigneeType ?? attrs['flowable:assigneeType'] ?? ''
    // 读取 SPEL 表达式模板（用于回显模板选择）
    const spelTemplate = bo.spelTemplate ?? attrs['flowable:spelTemplate'] ?? ''

    // 重置审批相关属性，防止上一个节点的数据残留
    properties.taskType = 'assignee'
    properties.assignee = ''
    properties.assigneeUserId = ''
    properties.assigneeExpr = ''
    properties.assigneeUserName = ''
    properties.candidateUsers = []
    properties.candidateUserNames = []
    properties.candidateGroups = []
    properties.candidateGroupNames = []

    if (assignee) {
      properties.taskType = 'assignee'
      properties.assigneeUserName = assigneeName

      // 优先使用 assigneeType 标识来判断类型
      if (assigneeType === 'spel') {
        properties.assignee = 'spel'
        properties.assigneeExpr = assignee
      }
      // 新版固定人员直接保存字符串用户 ID；旧 ${user_123} 在加载时迁移。
      else if (assigneeType === 'custom') {
        properties.assignee = 'custom'
        properties.assigneeUserId = normalizeFixedAssigneeUserId(assignee)
          || extractLegacyFixedAssigneeUserId(assignee)
      }
      else if (extractLegacyFixedAssigneeUserId(assignee)) {
        properties.assignee = 'custom'
        properties.assigneeUserId = extractLegacyFixedAssigneeUserId(assignee)
      }
      // 预定义表达式：${initiator}, ${initiatorLeader}, ${deptManager}, ${hr}
      else if (['$' + '{initiator}', '$' + '{initiatorLeader}', '$' + '{deptManager}', '$' + '{hr}'].includes(assignee)) {
        properties.assignee = assignee
      }
      // 简单变量表达式可直接作为 Flowable 审批人表达式回显
      else if (isSimpleVariableExpression(assignee)) {
        properties.assignee = assignee
      }
      // 其他 ${} 表达式默认当作 SPEL（向后兼容旧数据）
      else if (assignee.startsWith('$' + '{') && assignee.endsWith('}')) {
        properties.assignee = 'spel'
        properties.assigneeExpr = assignee
      }
      else {
        properties.assignee = assignee
      }
    }
    else if (assigneeType === 'spel') {
      properties.taskType = 'assignee'
      properties.assignee = 'spel'
    }
    else if (candidateUsers) {
      properties.taskType = 'candidateUsers'
      properties.candidateUsers = candidateUsers.split(',').filter(Boolean)
      properties.candidateUserNames = candidateUserNames ? candidateUserNames.split(',').filter(Boolean) : []
    }
    else if (candidateGroups) {
      properties.taskType = 'candidateGroups'
      properties.candidateGroups = candidateGroups.split(',').filter(Boolean)
      properties.candidateGroupNames = candidateGroupNames ? candidateGroupNames.split(',').filter(Boolean) : []
    }

    if (assigneeType === 'spel') {
      selectedSpelTemplate.value = spelTemplate
    }

    // 表单配置 - 直接读 bo 上的属性
    properties.formKey = bo.formKey ?? attrs['flowable:formKey'] ?? ''
    properties.formJson = bo.formJson ?? attrs['flowable:formJson'] ?? ''
    properties.formUrl = bo.formUrl ?? attrs['flowable:formUrl'] ?? ''

    // 表单类型判断
    if (properties.formUrl) {
      properties.formType = 'external'
    }
    else if (properties.formKey || properties.formJson) {
      properties.formType = 'dynamic'
    }
    else {
      properties.formType = 'none'
    }

    // 优先级和截止日期
    const priorityVal = bo.priority ?? attrs['flowable:priority']
    properties.priority = priorityVal != null ? Number.parseInt(priorityVal) : 50
    const dueDateVal = bo.dueDate ?? attrs['flowable:dueDate']
    if (dueDateVal) {
      // 格式可能是 P3D (ISO 8601 duration) 或纯数字
      const match = String(dueDateVal).match(/(\d+)/)
      properties.dueDate = match ? Number.parseInt(match[1]) : 0
    }
    else {
      properties.dueDate = 0
    }

    properties.allowApprove = readBooleanAttr(bo, attrs, 'allowApprove', true)
    properties.allowReject = readBooleanAttr(bo, attrs, 'allowReject', true)
    properties.allowDelegate = readBooleanAttr(bo, attrs, 'allowDelegate', true)
    properties.allowReturn = readBooleanAttr(bo, attrs, 'allowReturn', false)
    properties.allowTerminate = readBooleanAttr(bo, attrs, 'allowTerminate', false)
    properties.requireSignature = readBooleanAttr(bo, attrs, 'requireSignature', false)
    properties.requireComment = readBooleanAttr(bo, attrs, 'requireComment', true)

    // 多实例配置
    const loopCharacteristics = bo.loopCharacteristics
    if (loopCharacteristics) {
      properties.multiInstanceType = loopCharacteristics.isSequential ? 'sequential' : 'parallel'
      // 解析完成条件
      if (loopCharacteristics.completionCondition) {
        const condition = loopCharacteristics.completionCondition.body || ''
        if (condition.includes('nrOfCompletedInstances == nrOfInstances')) {
          properties.completionCondition = 'all'
        }
        else if (condition.includes('nrOfCompletedInstances >= 1')) {
          properties.completionCondition = 'any'
        }
        else if (condition.includes('/ nrOfInstances')) {
          properties.completionCondition = 'rate'
          const match = condition.match(/>= *([\d.]+)/)
          if (match) {
            properties.passRate = Math.round(Number.parseFloat(match[1]) * 100)
          }
        }
      }
      else {
        properties.completionCondition = 'all'
      }
    }
    else {
      properties.multiInstanceType = 'none'
      properties.completionCondition = 'all'
      properties.passRate = 100
    }

    // 任务监听器
    properties.taskListeners = []
    const extensionElements = bo.extensionElements?.values || []
    extensionElements.forEach((ext) => {
      if (ext.$type === 'flowable:TaskListener') {
        properties.taskListeners.push({
          event: ext.event || 'create',
          class: ext.class || '',
        })
      }
    })

    // 执行监听器
    properties.executionListeners = []
    extensionElements.forEach((ext) => {
      if (ext.$type === 'flowable:ExecutionListener') {
        properties.executionListeners.push({
          event: ext.event || 'start',
          class: ext.class || '',
        })
      }
    })
  }

  // 加载服务任务属性
  function loadServiceTaskProperties(bo) {
    const attrs = bo.$attrs || {}

    // moddleExtensions 注册后直接读 bo.class / bo.expression / bo.delegateExpression
    const classVal = bo.class ?? bo['flowable:class'] ?? attrs['flowable:class']
    const exprVal = bo.expression ?? bo['flowable:expression'] ?? attrs['flowable:expression']
    const delegateVal = bo.delegateExpression ?? bo['flowable:delegateExpression'] ?? attrs['flowable:delegateExpression']
    const flowableType = bo.type ?? bo['flowable:type'] ?? attrs['flowable:type'] ?? ''
    const candidateUsers = bo.candidateUsers ?? attrs['flowable:candidateUsers'] ?? ''
    const candidateUserNames = bo.candidateUserNames ?? attrs['flowable:candidateUserNames'] ?? ''
    const candidateGroups = bo.candidateGroups ?? attrs['flowable:candidateGroups'] ?? ''
    const candidateGroupNames = bo.candidateGroupNames ?? attrs['flowable:candidateGroupNames'] ?? ''
    const ccReceiverType = bo.ccReceiverType ?? attrs['flowable:ccReceiverType'] ?? ''
    const ccExpressionTarget = bo.ccExpressionTarget ?? attrs['flowable:ccExpressionTarget'] ?? ''

    if (classVal) {
      properties.implementationType = 'class'
      properties.implementation = classVal
    }
    else if (exprVal) {
      properties.implementationType = 'expression'
      properties.implementation = exprVal
    }
    else if (delegateVal) {
      properties.implementationType = 'delegateExpression'
      properties.implementation = flowableType === 'cc' && delegateVal === '$' + '{flowCcNodeDelegate}' ? '' : delegateVal
    }
    else {
      properties.implementationType = flowableType === 'cc' ? 'delegateExpression' : 'class'
      properties.implementation = ''
    }
    properties.flowableType = flowableType
    properties.candidateUsers = candidateUsers ? candidateUsers.split(',').filter(Boolean) : []
    properties.candidateUserNames = candidateUserNames ? candidateUserNames.split(',').filter(Boolean) : []
    properties.candidateGroups = candidateGroups ? candidateGroups.split(',').filter(Boolean) : []
    properties.candidateGroupNames = candidateGroupNames ? candidateGroupNames.split(',').filter(Boolean) : []
    properties.ccReceiverType = flowableType === 'cc'
      ? inferCarbonCopyReceiverType(properties.candidateUsers, properties.candidateGroups, ccReceiverType)
      : 'users'
    properties.ccExpressionTarget = ccExpressionTarget || (findCarbonCopyExpression(properties.candidateGroups) ? 'roles' : 'users')
    properties.ccExpression = findCarbonCopyExpression(properties.candidateUsers) || findCarbonCopyExpression(properties.candidateGroups) || ''
    properties.async = bo.async ?? attrs['flowable:async'] ?? false
  }

  // 加载序列流属性
  function loadSequenceFlowProperties(bo) {
    const conditionExpression = bo.conditionExpression
    if (conditionExpression) {
      properties.hasCondition = true
      properties.condition = conditionExpression.body || ''
      properties.conditionType = 'expression'
      syncConditionPresetFromCondition()
    }
    else {
      properties.hasCondition = false
      properties.condition = ''
      properties.conditionType = 'expression'
      properties.conditionPreset = 'custom'
    }

    // 检查是否默认流
    const source = bo.sourceRef
    if (source && source.default) {
      properties.isDefault = source.default.id === bo.id
    }
    else {
      properties.isDefault = false
    }
  }

  // 加载开始事件属性
  function loadStartEventProperties(bo) {
    const attrs = bo.$attrs || {}
    // moddleExtensions 注册后直接读 bo.initiator
    properties.initiator = bo.initiator ?? bo['flowable:initiator'] ?? attrs['flowable:initiator'] ?? 'initiator'
    properties.formKey = bo.formKey ?? bo['flowable:formKey'] ?? attrs['flowable:formKey'] ?? ''
  }

  function readBooleanAttr(bo, attrs, name, defaultValue) {
    const value = bo[name] ?? bo[`flowable:${name}`] ?? attrs[`flowable:${name}`]
    if (value === undefined || value === null || value === '')
      return defaultValue
    if (typeof value === 'boolean')
      return value
    return ['true', '1', 'y', 'yes'].includes(String(value).trim().toLowerCase())
  }

  // 更新扩展属性（flowable 命名空间属性，通过 moddleExtensions 注册后直接用属性名）
  function updateExtensionProperty(prop) {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    const value = properties[prop]
    // 使用 flowable: 前缀写入，bpmn-js 会根据 moddleExtensions 映射
    modeling.updateProperties(rawElement.value, {
      [`flowable:${prop}`]: value !== '' ? value : null,
    })
  }

  // 更新任务类型
  function updateTaskType() {
    // 清空其他审批人配置
    properties.assignee = ''
    properties.assigneeUserId = ''
    properties.assigneeExpr = ''
    properties.assigneeUserName = ''
    properties.candidateUsers = []
    properties.candidateUserNames = []
    properties.candidateGroups = []
    properties.candidateGroupNames = []
  }

  function insertSpelVariable(field) {
    if (!field)
      return
    properties.assigneeExpr = toExpression(field)
    selectedSpelVariable.value = null
    updateUserTaskAssignee()
  }

  function applyConditionBuilder() {
    if (!conditionBuilder.field) {
      window.$message?.warning('请选择条件字段')
      return
    }
    const field = conditionBuilder.field
    const operator = conditionBuilder.operator || '=='
    const rightValue = operator === 'contains'
      ? `.contains(${quoteConditionValue(conditionBuilder.value)})`
      : ` ${operator} ${quoteConditionValue(conditionBuilder.value)}`
    properties.hasCondition = true
    properties.conditionType = 'expression'
    properties.conditionPreset = 'custom'
    properties.condition = operator === 'contains'
      ? toExpression(`${field}${rightValue}`)
      : toExpression(`${field}${rightValue}`)
    updateCondition()
  }

  function applyCandidateVariable(type, field) {
    if (!field)
      return
    const option = getCatalogField(field)
    const expression = toExpression(field)
    if (type === 'users') {
      properties.candidateUsers = [expression]
      properties.candidateUserNames = [option?.label ? `表单字段：${option.label}` : expression]
      updateCandidateUsers()
      return
    }
    properties.candidateGroups = [expression]
    properties.candidateGroupNames = [option?.label ? `表单字段：${option.label}` : expression]
    updateCandidateGroups()
  }

  function handleFormKeyChange(value) {
    properties.formKey = value || ''
    markDirty()
  }

  async function openNodeFormDesigner() {
    properties.formType = 'dynamic'
    nodeFormDesignerSchema.value = cloneValue(await resolveNodeFormRules()) || []
    showNodeFormDesigner.value = true
  }

  async function openNodeFormPreview() {
    const rules = await resolveNodeFormRules()
    if (!rules.length) {
      window.$message?.warning('暂无可预览的节点表单')
      return
    }
    nodeFormPreviewSchema.value = cloneValue(rules) || []
    showNodeFormPreview.value = true
  }

  function handleSaveNodeFormSchema(schema) {
    const rules = Array.isArray(schema) ? cloneValue(schema) : []
    properties.formType = 'dynamic'
    if (!properties.formKey)
      properties.formKey = buildNodeFormKey()
    properties.formJson = JSON.stringify(rules)
    updateFormType()
    showNodeFormDesigner.value = false
    isDirty.value = false
    window.$message?.success('节点表单已保存到当前节点')
  }

  function clearNodeInlineForm() {
    properties.formJson = ''
    updateFormType()
    isDirty.value = false
    window.$message?.success('已清空节点内嵌表单')
  }

  async function resolveNodeFormRules() {
    const inlineRules = normalizeFormCreateRules(properties.formJson)
    if (inlineRules.length || !properties.formKey)
      return inlineRules
    const schema = await loadFormSchemaByKey(properties.formKey)
    return normalizeFormCreateRules(schema)
  }

  async function loadFormSchemaByKey(formKey) {
    if (!formKey)
      return []
    try {
      const res = await flowApi.getFormByKey(formKey)
      if (res.code === 200 && res.data?.formSchema)
        return res.data.formSchema
    }
    catch (error) {
      console.error('加载引用表单失败:', error)
      window.$message?.warning('引用表单加载失败，请检查表单Key')
    }
    return []
  }

  function buildNodeFormKey() {
    const rawKey = `${properties.id || rawElement.value?.id || 'user_task'}_form`
    return rawKey.replace(/[^\w-]/g, '_')
  }

  function countFormRules(rules) {
    if (!Array.isArray(rules))
      return 0
    return rules.reduce((count, rule) => {
      const childCount = Array.isArray(rule.children) ? countFormRules(rule.children) : 0
      return count + 1 + childCount
    }, 0)
  }

  // 更新表单类型
  function updateFormType() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')

    if (properties.formType === 'none') {
      properties.formKey = ''
      properties.formJson = ''
      properties.formUrl = ''
      modeling.updateProperties(rawElement.value, {
        'flowable:formKey': null,
        'flowable:formJson': null,
        'flowable:formUrl': null,
      })
    }
    else if (properties.formType === 'external') {
      properties.formKey = ''
      properties.formJson = ''
      modeling.updateProperties(rawElement.value, {
        'flowable:formKey': null,
        'flowable:formJson': null,
        'flowable:formUrl': properties.formUrl, // ✅ 保存外部表单URL
      })
    }
    else if (properties.formType === 'dynamic') {
      properties.formUrl = ''
      modeling.updateProperties(rawElement.value, {
        'flowable:formUrl': null,
        'flowable:formKey': properties.formKey || null,
        'flowable:formJson': properties.formJson || null,
      })
    }
    emit('update')
  }

  // 更新用户任务审批人
  function updateUserTaskAssignee() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    let value = properties.assignee
    let assigneeType = null

    // 处理不同的审批人类型
    if (properties.assignee === 'custom') {
      value = normalizeFixedAssigneeUserId(properties.assigneeUserId)
        || extractLegacyFixedAssigneeUserId(properties.assigneeExpr)
      assigneeType = 'custom'
    }
    else if (properties.assignee === 'spel') {
      properties.assigneeUserId = ''
      properties.assigneeUserName = ''
      value = properties.assigneeExpr
      assigneeType = 'spel'
    }
    else {
      properties.assigneeUserId = ''
      properties.assigneeExpr = ''
      properties.assigneeUserName = ''
    }

    const element = rawElement.value

    modeling.updateProperties(element, {
      'flowable:assignee': value || null,
      'flowable:assigneeType': assigneeType,
      'flowable:assigneeName': properties.assigneeUserName || null,
      'flowable:spelTemplate': selectedSpelTemplate.value || null,
      'flowable:candidateUsers': null,
      'flowable:candidateUserNames': null,
      'flowable:candidateGroups': null,
      'flowable:candidateGroupNames': null,
    })
    emit('update')
  }

  function normalizeFixedAssigneeUserId(value) {
    const userId = String(value ?? '').trim()
    return /^\d+$/.test(userId) ? userId : ''
  }

  function extractLegacyFixedAssigneeUserId(value) {
    const match = String(value || '').match(/^\$\{user_(\d+)\}$/)
    return match?.[1] || ''
  }

  // 更新候选用户
  function updateCandidateUsers() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    const element = rawElement.value
    const usersStr = properties.candidateUsers.join(',')
    const userNamesStr = properties.candidateUserNames.join(',')

    modeling.updateProperties(element, {
      'flowable:assignee': null,
      'flowable:assigneeType': null,
      'flowable:assigneeName': null,
      'flowable:spelTemplate': null,
      'flowable:candidateUsers': usersStr || null,
      'flowable:candidateUserNames': userNamesStr || null,
      'flowable:candidateGroups': null,
      'flowable:candidateGroupNames': null,
    })
    emit('update')
  }

  // 更新候选组
  function updateCandidateGroups() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    const element = rawElement.value
    const groupsStr = properties.candidateGroups.join(',')
    const groupNamesStr = properties.candidateGroupNames.join(',')

    modeling.updateProperties(element, {
      'flowable:assignee': null,
      'flowable:assigneeType': null,
      'flowable:assigneeName': null,
      'flowable:spelTemplate': null,
      'flowable:candidateUsers': null,
      'flowable:candidateUserNames': null,
      'flowable:candidateGroups': groupsStr || null,
      'flowable:candidateGroupNames': groupNamesStr || null,
    })
    emit('update')
  }

  // 更新截止日期
  function updateDueDate() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    modeling.updateProperties(rawElement.value, {
      'flowable:dueDate': properties.dueDate > 0 ? `P${properties.dueDate}D` : null,
    })
  }

  function updateApprovalControl() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    modeling.updateProperties(rawElement.value, {
      'flowable:allowApprove': properties.allowApprove,
      'flowable:allowReject': properties.allowReject,
      'flowable:allowDelegate': properties.allowDelegate,
      'flowable:allowReturn': properties.allowReturn,
      'flowable:allowTerminate': properties.allowTerminate,
      'flowable:requireSignature': properties.requireSignature,
      'flowable:requireComment': properties.requireComment,
    })
    emit('update')
  }

  // 更新多实例配置
  function updateMultiInstance() {
    if (!rawElement.value || !props.modeler)
      return

    const moddle = props.modeler.get('moddle')
    const modeling = props.modeler.get('modeling')
    const bo = rawElement.value.businessObject

    if (properties.multiInstanceType === 'none') {
      modeling.updateProperties(rawElement.value, { loopCharacteristics: null })
      emit('update')
      return
    }

    // 构建完成条件
    let completionConditionStr = ''
    if (properties.completionCondition === 'all')
      completionConditionStr = '$' + '{nrOfCompletedInstances == nrOfInstances}'
    else if (properties.completionCondition === 'any')
      completionConditionStr = '$' + '{nrOfCompletedInstances >= 1}'
    else if (properties.completionCondition === 'rate')
      completionConditionStr = '$' + `{nrOfCompletedInstances / nrOfInstances >= ${(properties.passRate / 100).toFixed(2)}}`

    const loopCardinalityObj = moddle.create('bpmn:FormalExpression', { body: '$' + '{nrOfInstances}' })
    const completionCondObj = completionConditionStr
      ? moddle.create('bpmn:FormalExpression', { body: completionConditionStr })
      : null

    // 确保 loopCardinality 被正确序列化到 XML：
    // updateModdleProperties 适用于修改已有的 moddle 子元素
    if (bo.loopCharacteristics) {
      modeling.updateModdleProperties(rawElement.value, bo.loopCharacteristics, {
        isSequential: properties.multiInstanceType === 'sequential',
        loopCardinality: loopCardinalityObj,
        completionCondition: completionCondObj,
      })
    }
    else {
      // 首次创建：用 updateProperties 设置整个 loopCharacteristics
      const lc = moddle.create('bpmn:MultiInstanceLoopCharacteristics', {
        isSequential: properties.multiInstanceType === 'sequential',
        loopCardinality: loopCardinalityObj,
        completionCondition: completionCondObj,
      })
      modeling.updateProperties(rawElement.value, { loopCharacteristics: lc })
    }

    emit('update')
  }

  // 添加任务监听器
  function addTaskListener() {
    properties.taskListeners.push({
      event: 'create',
      class: '',
    })
  }

  // 移除任务监听器
  function removeTaskListener(index) {
    properties.taskListeners.splice(index, 1)
    updateTaskListeners()
  }

  // 更新任务监听器
  function updateTaskListeners() {
    if (!rawElement.value || !props.modeler)
      return

    const moddle = props.modeler.get('moddle')
    const modeling = props.modeler.get('modeling')
    const bo = rawElement.value.businessObject

    // 保留执行监听器，合并任务监听器
    const existingExtValues = bo.extensionElements?.values || []
    const executionListeners = existingExtValues.filter(v => v.$type === 'flowable:ExecutionListener')

    const taskListeners = properties.taskListeners
      .filter(l => l.class)
      .map(l => moddle.create('flowable:TaskListener', {
        event: l.event,
        class: l.class,
      }))

    const allValues = [...executionListeners, ...taskListeners]

    const extensionElements = moddle.create('bpmn:ExtensionElements', { values: allValues })
    modeling.updateProperties(rawElement.value, { extensionElements })
    emit('update')
  }

  // 添加执行监听器
  function addExecutionListener() {
    properties.executionListeners.push({
      event: 'start',
      class: '',
    })
  }

  // 移除执行监听器
  function removeExecutionListener(index) {
    properties.executionListeners.splice(index, 1)
  }

  // 更新执行监听器
  function updateExecutionListeners() {
    if (!rawElement.value || !props.modeler)
      return

    const moddle = props.modeler.get('moddle')
    const modeling = props.modeler.get('modeling')
    const bo = rawElement.value.businessObject

    // 保留任务监听器，合并执行监听器
    const existingExtValues = bo.extensionElements?.values || []
    const taskListeners = existingExtValues.filter(v => v.$type === 'flowable:TaskListener')

    const executionListeners = properties.executionListeners
      .filter(l => l.class)
      .map(l => moddle.create('flowable:ExecutionListener', {
        event: l.event,
        class: l.class,
      }))

    const allValues = [...taskListeners, ...executionListeners]

    const extensionElements = moddle.create('bpmn:ExtensionElements', { values: allValues })
    modeling.updateProperties(rawElement.value, { extensionElements })
    emit('update')
  }

  // 更新服务任务实现
  function updateServiceImplementation() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    const updateProps = {
      'flowable:class': null,
      'flowable:expression': null,
      'flowable:delegateExpression': null,
    }

    const key = `flowable:${properties.implementationType}`
    updateProps[key] = properties.implementation || null

    modeling.updateProperties(rawElement.value, updateProps)
    emit('update')
  }

  function toggleCarbonCopyService(checked) {
    properties.flowableType = checked ? 'cc' : ''
    if (checked) {
      properties.ccReceiverType = properties.ccReceiverType || 'users'
      if (!properties.implementation) {
        properties.implementationType = 'delegateExpression'
      }
    }
    else {
      properties.candidateUsers = []
      properties.candidateUserNames = []
      properties.candidateGroups = []
      properties.candidateGroupNames = []
      properties.ccReceiverType = 'users'
      properties.ccExpression = ''
      properties.ccExpressionTarget = 'users'
      if (properties.implementationType === 'delegateExpression' && !properties.implementation) {
        properties.implementationType = 'class'
      }
    }
    updateCarbonCopyConfig()
  }

  function updateCarbonCopyConfig() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    const isCarbonCopy = properties.flowableType === 'cc'
    if (isCarbonCopy && properties.ccReceiverType === 'expression') {
      applyCarbonCopyExpressionToProperties()
    }
    const usersStr = properties.candidateUsers.join(',')
    const userNamesStr = properties.candidateUserNames.join(',')
    const groupsStr = properties.candidateGroups.join(',')
    const groupNamesStr = properties.candidateGroupNames.join(',')
    const updateProps = {
      'flowable:type': isCarbonCopy ? 'cc' : null,
      'flowable:ccReceiverType': isCarbonCopy ? properties.ccReceiverType : null,
      'flowable:ccExpressionTarget': isCarbonCopy ? properties.ccExpressionTarget : null,
      'flowable:candidateUsers': isCarbonCopy && usersStr ? usersStr : null,
      'flowable:candidateUserNames': isCarbonCopy && userNamesStr ? userNamesStr : null,
      'flowable:candidateGroups': isCarbonCopy && groupsStr ? groupsStr : null,
      'flowable:candidateGroupNames': isCarbonCopy && groupNamesStr ? groupNamesStr : null,
    }
    if (isCarbonCopy && !properties.implementation) {
      updateProps['flowable:delegateExpression'] = '$' + '{flowCcNodeDelegate}'
    }
    if (!isCarbonCopy && !properties.implementation) {
      updateProps['flowable:delegateExpression'] = null
    }
    modeling.updateProperties(rawElement.value, updateProps)
    emit('update')
  }

  // 更新异步
  function updateAsync() {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    modeling.updateProperties(rawElement.value, {
      'flowable:async': properties.async,
    })
    emit('update')
  }

  // 获取实现方式占位符
  function getImplementationPlaceholder() {
    const placeholders = {
      class: 'com.example.MyServiceTask',
      expression: '${' + 'myService.execute()}',
      delegateExpression: '${' + 'myServiceDelegate}',
    }
    return placeholders[properties.implementationType] || ''
  }

  // 切换条件
  function toggleCondition(checked) {
    if (!checked) {
      properties.condition = ''
      properties.conditionPreset = 'custom'
      updateCondition()
      return
    }
    syncConditionPresetFromCondition()
  }

  // 更新条件类型
  function updateConditionType() {
    properties.condition = ''
    properties.script = ''
    properties.conditionPreset = 'custom'
    updateCondition()
  }

  // 更新流转条件
  function updateCondition() {
    if (!rawElement.value || !props.modeler)
      return

    const moddle = props.modeler.get('moddle')
    const modeling = props.modeler.get('modeling')

    if (properties.conditionType === 'expression' && properties.condition) {
      const expr = moddle.create('bpmn:FormalExpression', { body: properties.condition })
      modeling.updateProperties(rawElement.value, { conditionExpression: expr })
    }
    else if (properties.conditionType === 'script' && properties.script) {
      const expr = moddle.create('bpmn:FormalExpression', {
        body: properties.script,
        language: properties.scriptFormat,
      })
      modeling.updateProperties(rawElement.value, { conditionExpression: expr })
    }
    else {
      modeling.updateProperties(rawElement.value, { conditionExpression: null })
    }
    emit('update')
  }

  // 切换默认路径
  function toggleDefault(checked) {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    const bo = rawElement.value.businessObject

    if (checked) {
      // 设置为默认流
      modeling.updateProperties(bo.sourceRef, {
        default: rawElement.value,
      })
    }
    else {
      // 取消默认流
      modeling.updateProperties(bo.sourceRef, {
        default: null,
      })
    }
  }

  // 自定义审批人表达式
  // 应用 SPEL 表达式模板
  function applySpelTemplate(value) {
    if (value) {
      properties.assigneeExpr = value
      selectedSpelTemplate.value = value
      updateUserTaskAssignee()
    }
  }

  // 验证 SPEL 表达式
  function validateSpelExpression() {
    spelValidationError.value = ''

    if (!properties.assigneeExpr || !properties.assigneeExpr.trim()) {
      return
    }

    const expr = properties.assigneeExpr.trim()

    // 检查是否包含 ${} 包裹
    if (!expr.startsWith('$' + '{') || !expr.endsWith('}')) {
      spelValidationError.value = 'SPEL 表达式必须使用 $' + '{} 包裹'
      return
    }

    // 检查括号匹配
    const openCount = (expr.match(/\(/g) || []).length
    const closeCount = (expr.match(/\)/g) || []).length
    if (openCount !== closeCount) {
      spelValidationError.value = '括号不匹配，请检查表达式语法'
      return
    }

    // 验证通过，更新到节点
    updateUserTaskAssignee()
  }

  // 更新基础属性（id/name/documentation）时也触发 emit
  function updateProperty(prop) {
    if (!rawElement.value || !props.modeler)
      return

    const modeling = props.modeler.get('modeling')
    const moddle = props.modeler.get('moddle')

    if (prop === 'id') {
      modeling.updateProperties(rawElement.value, { id: properties.id })
    }
    else if (prop === 'name') {
      modeling.updateProperties(rawElement.value, { name: properties.name })
    }
    else if (prop === 'documentation') {
      const docs = properties.documentation
        ? [moddle.create('bpmn:Documentation', { text: properties.documentation })]
        : []
      modeling.updateProperties(rawElement.value, { documentation: docs })
    }
    emit('update')
  }
  __impl.loadUserTaskProperties = loadUserTaskProperties
  __impl.loadServiceTaskProperties = loadServiceTaskProperties
  __impl.loadSequenceFlowProperties = loadSequenceFlowProperties
  __impl.loadStartEventProperties = loadStartEventProperties
  __impl.readBooleanAttr = readBooleanAttr
  __impl.updateExtensionProperty = updateExtensionProperty
  __impl.updateTaskType = updateTaskType
  __impl.insertSpelVariable = insertSpelVariable
  __impl.applyConditionBuilder = applyConditionBuilder
  __impl.applyCandidateVariable = applyCandidateVariable
  __impl.handleFormKeyChange = handleFormKeyChange
  __impl.openNodeFormDesigner = openNodeFormDesigner
  __impl.openNodeFormPreview = openNodeFormPreview
  __impl.handleSaveNodeFormSchema = handleSaveNodeFormSchema
  __impl.clearNodeInlineForm = clearNodeInlineForm
  __impl.resolveNodeFormRules = resolveNodeFormRules
  __impl.loadFormSchemaByKey = loadFormSchemaByKey
  __impl.buildNodeFormKey = buildNodeFormKey
  __impl.countFormRules = countFormRules
  __impl.updateFormType = updateFormType
  __impl.updateUserTaskAssignee = updateUserTaskAssignee
  __impl.normalizeFixedAssigneeUserId = normalizeFixedAssigneeUserId
  __impl.extractLegacyFixedAssigneeUserId = extractLegacyFixedAssigneeUserId
  __impl.updateCandidateUsers = updateCandidateUsers
  __impl.updateCandidateGroups = updateCandidateGroups
  __impl.updateDueDate = updateDueDate
  __impl.updateApprovalControl = updateApprovalControl
  __impl.updateMultiInstance = updateMultiInstance
  __impl.addTaskListener = addTaskListener
  __impl.removeTaskListener = removeTaskListener
  __impl.updateTaskListeners = updateTaskListeners
  __impl.addExecutionListener = addExecutionListener
  __impl.removeExecutionListener = removeExecutionListener
  __impl.updateExecutionListeners = updateExecutionListeners
  __impl.updateServiceImplementation = updateServiceImplementation
  __impl.toggleCarbonCopyService = toggleCarbonCopyService
  __impl.updateCarbonCopyConfig = updateCarbonCopyConfig
  __impl.updateAsync = updateAsync
  __impl.getImplementationPlaceholder = getImplementationPlaceholder
  __impl.toggleCondition = toggleCondition
  __impl.updateConditionType = updateConditionType
  __impl.updateCondition = updateCondition
  __impl.toggleDefault = toggleDefault
  __impl.applySpelTemplate = applySpelTemplate
  __impl.validateSpelExpression = validateSpelExpression
  __impl.updateProperty = updateProperty

  return {
    ...deps,
  }
}
