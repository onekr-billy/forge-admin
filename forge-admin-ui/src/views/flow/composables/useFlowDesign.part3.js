/** flow/design setup part 3. */
import { NTag, NTreeSelect } from 'naive-ui'
import { computed, defineAsyncComponent, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { modelListByProvider, providerPage } from '@/api/ai'
import { businessFlowFormAssets, businessFlowModelBindings } from '@/api/business-app'
import { businessApplicationList, businessApplicationObjects } from '@/api/business-application'
import flowApi from '@/api/flow'
import { streamFlowGenerate } from '@/api/flow-generator'
import messageApi from '@/api/message'
import NodePropertiesPanel from '@/components/bpmn/NodePropertiesPanel.vue'
import { DingFlowDesigner } from '@/components/flow-designer'
import FlowPropertyPanelShell from '@/components/flow/FlowPropertyPanelShell.vue'
import FlowFormCreateDesigner from '@/components/form-create/FlowFormCreateDesigner.vue'
import FlowFormCreateRenderer from '@/components/form-create/FlowFormCreateRenderer.vue'
import { useDict } from '@/composables/useDict'
import { useTabStore } from '@/store'
import { toNumberDictOptions } from '@/utils/dict-options'
import { appendChildTableCatalogFields } from '@/utils/flow-field-permissions'
import { loadFlowBusinessFormFieldCatalog } from '@/utils/flow-form-loader'
import { sanitizeHtml } from '@/utils/sanitize-html'
import BusinessFlowFormAssetSelect from '@/views/app-center/components/designer/BusinessFlowFormAssetSelect.vue'
import { buildFlowCategoryTreeOptions, resolveFlowCategoryValue } from '../utils/categoryOptions'
import { buildLocalFormFieldCatalog } from '../utils/form-field-catalog'
import VersionHistory from '../version.vue'
export function applyFlowDesignPart3(props, emit, deps = {}) {
  const {
    __impl, mut, applyProcessConfigFromXml, applyProcessConfigToXml, buildBusinessGlobalFormJson, buildNotifyConfig, channelsForEvent, collectBusinessAssetFields,
    decodeHtmlEntities, defaultNotifyChannels, ensureBusinessGlobalFormSelection, extractProcessConfigFromXml, findBusinessFormAsset, findBusinessFormAssetInList, getRouteModelId, getSettingsBadge,
    getSettingsBadgeClass, getXmlForSave, goBackToBusinessApp, handleAbortAi, handleAiSend, handleApplyAiDraft, handleBusinessApplicationChange, handleBusinessGlobalFormUpdate,
    handleBusinessManagedFormTypeChange, handleBusinessObjectChange, handleFlowSSEChunk, handleFlowSSEComplete, handleFlowSSEError, handleFormSelect, handleFormTypeChange, handleNewAiSession,
    handleOpenVersionHistory, handlePreviewAiXml, handleSaveFormSchema, handleVersionHistoryRefresh, isAppManagedFormType, loadBusinessApplicationOptions, loadBusinessObjectOptions, loadCategories,
    loadForms, loadModel, loadModelOptions, loadNotificationMetadata, loadProviderOptions, normalizeAiBpmnXml, normalizeAiFlowDraft, normalizeAppManagedFormType,
    normalizeAutoApprovalMode, normalizeBusinessEntryRoute, normalizeBusinessFieldCatalog, normalizeBusinessFormAssets, normalizeBusinessFormMode, normalizeDesignerType, normalizeNotifyChannels, normalizeRejectStrategy,
    notificationTemplateOptions, notificationTemplatePrefix, notificationTemplateSummary, openSettingsPanel, parseAiFlowResponse, parseBooleanWithDefault, parseBusinessGlobalFormRef, parseNotifyConfig,
    previewNotificationTemplate, readFlowableAttr, refreshBusinessFormFieldCatalog, refreshExternalFormFieldCatalog, refreshFormFieldCatalog, renderNotificationTemplate, resetNewModelState, resetNotifyMatrix,
    resolveBusinessBindingForModel, resolveLocalFormFieldCatalog, routeQueryText, scrollActiveReasoningToBottom, scrollAiToBottom, selectedNotificationTemplateValue, setReasoningContentRef, setWorkspaceMode,
    stripCodeFence, toggleAiPanel, toggleReasoning, updateLastAiAssistantMessage, updateNotificationTemplate, updateNotifyChannels, route, router,
    tabStore, message, FlowModeler, DEFAULT_TODO_DETAIL_URL_TEMPLATE, embedded, explicitBusinessObjectCode, explicitBusinessObjectName, explicitBusinessApplicationId,
    explicitBusinessEntryRoute, resolvedBusinessBinding, manualBusinessApplicationId, manualBusinessApplicationName, manualBusinessObjectCode, manualBusinessObjectName, businessApplicationOptions, businessApplicationLoading,
    businessObjectOptions, businessObjectLoading, businessObjectOptionsApplicationId, businessObjectCode, businessContextActive, businessContextName, businessEntryRoute, effectiveApplicationId,
    businessApplicationName, effectiveCodeApp, saving, deploying, pageLoading, diagramLoadingCount, modelerRef, bpmnXml,
    hasChanges, aiSending, aiPrompt, aiSessionId, aiMessages, aiDraft, showAiXmlPreview, showVersionHistory,
    aiAbortController, aiRawContent, aiReasoningContent, aiIsReasoningPhase, aiReasoningStartTime, aiReasoningEndTime, aiCurrentStage, expandedReasonings,
    aiMessageListRef, aiMessageEndRef, reasoningContentRefs, showModelPanel, syncingModel, aiProviderId, aiModelId, providerOptions,
    modelOptions, workspaceMode, rightActiveTab, modelInfo, showFormDesigner, formDesignerRef, formSchema, formOptions,
    formFieldCatalog, formFieldCatalogError, showFormPreview, businessFormAssets, categoryTreeOptions, modelerInstance, dockedElement, notifyEventDefinitions,
    notifyChannelOrder, defaultNotificationTemplateCodes, notifyChannels, notifyChannelsLoading, notifyChannelsLoaded, notificationTemplates, notificationTemplatesLoading, notificationTemplatesLoaded,
    notifyMatrixDirty, notifyConfigInitialPresent, notifyMatrix, notifyTemplateCodes, notifyEventConfigured, showNotificationTemplatePreview, notificationPreview, designerTypeOptions,
    formTypeOptions, businessManagedFormTypeOptions, autoApprovalModeOptions, modelStatusOptions, aiExamples, aiStages, currentAiStageIndex, statusTag,
    isReadonly, designerLoading, aiGeneratingCanvasHintVisible, aiCanvasHintText, currentProviderLabel, currentModelLabel, designerType, isApprovalDesigner,
    isBusinessDesigner, designerRenderKey, designerTypeLabel, businessPanelTitle, businessPanelIcon, processConfig, appManagedFormTypeActive, businessFormConfigActive,
    businessApplicationPickerVisible, businessObjectPickerVisible, nodeFormAssetOptions, businessGlobalFormRef, businessGlobalFormNode, selectedBusinessGlobalFormAsset, formConfigStatus, notifySmsSelected,
    notificationConfigStatus, autoApprovalModeLabel, isAiPanelActive, settingsTreeGroups,
  } = deps
  function extractJsonObject(text) {
    const start = text.indexOf('{')
    const end = text.lastIndexOf('}')
    if (start >= 0 && end > start) {
      return text.slice(start, end + 1)
    }
    return ''
  }

  function extractBpmnXml(text) {
    const match = text.match(/(?:<\?xml[\s\S]*?\?>\s*)?<(?:[\w.-]+:)?definitions\b[\s\S]*<\/(?:[\w.-]+:)?definitions>/)
    return match ? match[0].trim() : ''
  }

  function ensureProcessId(xml, modelKey) {
    const safeModelKey = normalizeBpmnId(modelKey)
    if (!xml || !safeModelKey)
      return xml
    const doc = parseXmlDocument(xml)
    if (!doc)
      return xml

    const processEl = findElementByLocalName(doc, 'process')
    if (!processEl)
      return xml

    const originalProcessId = processEl.getAttribute('id')
    processEl.setAttribute('id', safeModelKey)
    processEl.setAttribute('isExecutable', 'true')

    findElementsByLocalName(doc, 'participant').forEach((el) => {
      if (!originalProcessId || el.getAttribute('processRef') === originalProcessId) {
        el.setAttribute('processRef', safeModelKey)
      }
    })

    const hasCollaboration = !!findElementByLocalName(doc, 'collaboration')
    findElementsByLocalName(doc, 'BPMNPlane').forEach((el) => {
      if (!hasCollaboration || !originalProcessId || !el.getAttribute('bpmnElement') || el.getAttribute('bpmnElement') === originalProcessId) {
        el.setAttribute('bpmnElement', safeModelKey)
      }
    })

    return new XMLSerializer().serializeToString(doc)
  }

  function syncSafeModelKey(modelKey) {
    const safeModelKey = normalizeBpmnId(modelKey)
    if (modelInfo.modelKey !== safeModelKey) {
      const originalModelKey = modelInfo.modelKey || modelKey
      modelInfo.modelKey = safeModelKey
      if (originalModelKey && originalModelKey !== safeModelKey) {
        message.warning(`流程Key已调整为 ${safeModelKey}，BPMN流程ID不能以数字开头或包含特殊字符`)
      }
    }
    return safeModelKey
  }

  function normalizeBpmnId(value) {
    let id = String(value || '').trim()
    if (!id)
      id = `process_${Date.now()}`

    id = id
      .replace(/[^\w.-]/g, '_')
      .replace(/_+/g, '_')

    if (!/^[a-z_]/i.test(id))
      id = `process_${id}`

    return id || `process_${Date.now()}`
  }

  function repairBpmnXml(xml, modelKey) {
    if (!xml)
      return xml
    const parseError = getXmlParseError(xml)
    const displayIssue = parseError ? '' : getBpmnDisplayIssue(xml)
    if (!parseError && !displayIssue && hasBpmnDiagram(xml))
      return xml

    const repaired = rebuildDiagramInfo(xml, modelKey)
    if (!repaired)
      return xml

    const repairedError = getXmlParseError(repaired)
    const repairedDisplayIssue = repairedError ? '' : getBpmnDisplayIssue(repaired)
    if (repairedError) {
      console.warn('[FlowDesign] BPMN XML 修复后仍不可解析:', repairedError)
      return xml
    }
    if (repairedDisplayIssue) {
      console.warn('[FlowDesign] BPMN XML 修复后仍不可展示:', repairedDisplayIssue)
      return xml
    }
    console.warn('[FlowDesign] AI 返回的 BPMNDI 坐标信息无效，已重新生成图形信息:', parseError || displayIssue || '缺少 BPMNDiagram')
    return repaired
  }

  function parseXmlDocument(xml) {
    try {
      const doc = new DOMParser().parseFromString(xml, 'application/xml')
      if (doc.querySelector('parsererror'))
        return null
      return doc
    }
    catch {
      return null
    }
  }

  function getXmlParseError(xml) {
    try {
      const doc = new DOMParser().parseFromString(xml, 'application/xml')
      const error = doc.querySelector('parsererror')
      return error?.textContent || ''
    }
    catch (error) {
      return error.message
    }
  }

  function hasBpmnDiagram(xml) {
    const doc = parseXmlDocument(xml)
    if (!doc)
      return /<(?:[\w.-]+:)?BPMNDiagram\b/.test(xml)
    return !!findElementByLocalName(doc, 'BPMNDiagram')
  }

  function getBpmnDisplayIssue(xml) {
    const doc = parseXmlDocument(xml)
    if (!doc)
      return 'BPMN XML 语法不合法，无法解析'

    const definitionsEl = findElementByLocalName(doc, 'definitions')
    const rootChildren = definitionsEl ? Array.from(definitionsEl.children) : []
    const displayRoots = rootChildren.filter(el => ['process', 'collaboration'].includes(el.localName))
    if (displayRoots.length === 0)
      return 'BPMN XML 缺少 process 或 collaboration，无法在画布展示'

    const planeEl = findElementByLocalName(doc, 'BPMNPlane')
    if (!planeEl)
      return 'BPMN XML 缺少 BPMNPlane 图形平面'

    const rootIds = new Set(displayRoots.map(el => el.getAttribute('id')).filter(Boolean))
    const planeElement = planeEl.getAttribute('bpmnElement')
    if (!planeElement || !rootIds.has(planeElement))
      return 'BPMNPlane 的 bpmnElement 未指向真实的 process/collaboration'

    return ''
  }

  function rebuildDiagramInfo(xml, modelKey) {
    const semanticXml = stripDiagramInfo(xml)
    const semanticError = getXmlParseError(semanticXml)
    if (semanticError) {
      console.warn('[FlowDesign] BPMN 语义 XML 不可解析，无法重建图形信息:', semanticError)
      return ''
    }

    const doc = new DOMParser().parseFromString(semanticXml, 'application/xml')
    const processEl = findElementByLocalName(doc, 'process')
    if (!processEl)
      return ''

    const processId = processEl.getAttribute('id') || modelKey
    const nodes = []
    const flows = []

    Array.from(processEl.children).forEach((el) => {
      const id = el.getAttribute('id')
      if (!id)
        return
      if (el.localName === 'sequenceFlow') {
        flows.push({
          id,
          sourceRef: el.getAttribute('sourceRef'),
          targetRef: el.getAttribute('targetRef'),
        })
        return
      }
      if (isBpmnFlowNode(el.localName)) {
        nodes.push({
          id,
          type: el.localName,
        })
      }
    })

    if (nodes.length === 0)
      return ''

    const bounds = layoutBpmnNodes(nodes, flows)
    const diagramXml = buildDiagramXml(processId, nodes, flows, bounds)
    const normalizedSemanticXml = ensureDiagramNamespaces(semanticXml)
    return normalizedSemanticXml.replace(/<\/((?:[\w.-]+:)?definitions)>\s*$/i, `${diagramXml}\n</$1>`)
  }

  function stripDiagramInfo(xml) {
    const openMatch = xml.match(/<([\w.-]+:)?BPMNDiagram\b/)
    const start = openMatch?.index ?? -1
    if (start < 0)
      return xml

    const closeTag = `</${openMatch[1] || ''}BPMNDiagram>`
    const end = xml.indexOf(closeTag, start)
    if (end >= 0) {
      return `${xml.slice(0, start)}${xml.slice(end + closeTag.length)}`
    }

    const definitionsEnd = xml.search(/<\/(?:[\w.-]+:)?definitions>\s*$/i)
    if (definitionsEnd >= 0) {
      return `${xml.slice(0, start)}${xml.slice(definitionsEnd)}`
    }
    return xml.slice(0, start)
  }

  function ensureDiagramNamespaces(xml) {
    const namespaces = [
      ['bpmndi', 'http://www.omg.org/spec/BPMN/20100524/DI'],
      ['dc', 'http://www.omg.org/spec/DD/20100524/DC'],
      ['di', 'http://www.omg.org/spec/DD/20100524/DI'],
    ]
    return namespaces.reduce((text, [prefix, uri]) => {
      if (text.includes(`xmlns:${prefix}=`))
        return text
      return text.replace(/<(?:[\w.-]+:)?definitions\b/, match => `${match} xmlns:${prefix}="${uri}"`)
    }, xml)
  }

  function findElementByLocalName(doc, localName) {
    return Array.from(doc.getElementsByTagName('*')).find(el => el.localName === localName) || null
  }

  function findElementsByLocalName(doc, localName) {
    return Array.from(doc.getElementsByTagName('*')).filter(el => el.localName === localName)
  }

  function isBpmnFlowNode(localName) {
    return localName.endsWith('Event')
      || localName.endsWith('Task')
      || localName.endsWith('Gateway')
      || ['subProcess', 'callActivity'].includes(localName)
  }

  function layoutBpmnNodes(nodes, flows) {
    const nodeMap = new Map(nodes.map(node => [node.id, node]))
    const rankMap = new Map()
    const outgoing = new Map()
    const incomingCount = new Map(nodes.map(node => [node.id, 0]))

    flows.forEach((flow) => {
      if (!nodeMap.has(flow.sourceRef) || !nodeMap.has(flow.targetRef))
        return
      if (!outgoing.has(flow.sourceRef))
        outgoing.set(flow.sourceRef, [])
      outgoing.get(flow.sourceRef).push(flow.targetRef)
      incomingCount.set(flow.targetRef, (incomingCount.get(flow.targetRef) || 0) + 1)
    })

    const startNodes = nodes.filter(node => node.type === 'startEvent' || incomingCount.get(node.id) === 0)
    const queue = startNodes.length > 0 ? [...startNodes] : [nodes[0]]
    queue.forEach(node => rankMap.set(node.id, 0))

    while (queue.length > 0) {
      const node = queue.shift()
      const nextRank = (rankMap.get(node.id) || 0) + 1
      ;(outgoing.get(node.id) || []).forEach((targetId) => {
        if (rankMap.has(targetId))
          return
        rankMap.set(targetId, nextRank)
        queue.push(nodeMap.get(targetId))
      })
    }

    nodes.forEach((node, index) => {
      if (!rankMap.has(node.id))
        rankMap.set(node.id, index)
    })

    const rankCounts = new Map()
    const bounds = new Map()
    nodes.forEach((node) => {
      const rank = rankMap.get(node.id)
      const lane = rankCounts.get(rank) || 0
      rankCounts.set(rank, lane + 1)
      const size = getBpmnNodeSize(node.type)
      bounds.set(node.id, {
        x: 100 + rank * 180,
        y: 120 + lane * 140,
        ...size,
      })
    })
    return bounds
  }

  function getBpmnNodeSize(type) {
    if (type.endsWith('Gateway'))
      return { width: 50, height: 50 }
    if (type.endsWith('Event'))
      return { width: 36, height: 36 }
    return { width: 100, height: 80 }
  }

  function buildDiagramXml(processId, nodes, flows, bounds) {
    const lines = [
      '  <bpmndi:BPMNDiagram id="BPMNDiagram_1">',
      `    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${escapeXmlAttr(processId)}">`,
    ]

    nodes.forEach((node) => {
      const b = bounds.get(node.id)
      lines.push(`      <bpmndi:BPMNShape id="${escapeXmlAttr(node.id)}_di" bpmnElement="${escapeXmlAttr(node.id)}">`)
      lines.push(`        <dc:Bounds x="${b.x}" y="${b.y}" width="${b.width}" height="${b.height}" />`)
      lines.push('      </bpmndi:BPMNShape>')
    })

    flows.forEach((flow) => {
      const source = bounds.get(flow.sourceRef)
      const target = bounds.get(flow.targetRef)
      if (!source || !target)
        return
      const waypoints = buildWaypoints(source, target)
      lines.push(`      <bpmndi:BPMNEdge id="${escapeXmlAttr(flow.id)}_di" bpmnElement="${escapeXmlAttr(flow.id)}">`)
      waypoints.forEach(point => lines.push(`        <di:waypoint x="${point.x}" y="${point.y}" />`))
      lines.push('      </bpmndi:BPMNEdge>')
    })

    lines.push('    </bpmndi:BPMNPlane>')
    lines.push('  </bpmndi:BPMNDiagram>')
    return lines.join('\n')
  }

  function buildWaypoints(source, target) {
    const sourceRight = { x: source.x + source.width, y: source.y + Math.round(source.height / 2) }
    const targetLeft = { x: target.x, y: target.y + Math.round(target.height / 2) }
    if (targetLeft.x > sourceRight.x) {
      return [sourceRight, targetLeft]
    }

    const sourceBottom = { x: source.x + Math.round(source.width / 2), y: source.y + source.height }
    const targetBottom = { x: target.x + Math.round(target.width / 2), y: target.y + target.height }
    const routeY = Math.max(sourceBottom.y, targetBottom.y) + 40
    return [
      sourceBottom,
      { x: sourceBottom.x, y: routeY },
      { x: targetBottom.x, y: routeY },
      targetBottom,
    ]
  }

  function escapeXmlAttr(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('"', '&quot;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
  }

  async function handleBpmnChange(xml) {
    if (xml) {
      bpmnXml.value = xml
      hasChanges.value = true
    }
    else {
      try {
        const newXml = await modelerRef.value?.getXML(true)
        if (newXml) {
          bpmnXml.value = newXml
          hasChanges.value = true
        }
      }
      catch (error) {
        console.error('获取 XML 失败:', error)
      }
    }
  }

  function handleDiagramImportStart() {
    diagramLoadingCount.value += 1
  }

  function handleDiagramImportEnd() {
    diagramLoadingCount.value = Math.max(0, diagramLoadingCount.value - 1)
  }

  async function handleSaveDraft(allowDuringDeploy = false) {
    if (saving.value || (deploying.value && !allowDuringDeploy))
      return false
    try {
      saving.value = true

      normalizeBusinessGlobalFormBeforeSave()
      if (!validateBusinessGlobalFormBeforeSave())
        return false
      const xml = await getXmlForSave()

      if (!xml) {
        window.$message?.warning('流程图验证失败，请检查后重试')
        return false
      }

      const data = {
        ...modelInfo,
        bpmnXml: xml,
        formJson: modelInfo.formJson || (formSchema.value.length > 0 ? JSON.stringify(formSchema.value) : ''),
        notifyConfig: buildNotifyConfig(),
      }

      let res
      if (modelInfo.id) {
        res = await flowApi.updateModel(data)
      }
      else {
        res = await flowApi.createModel(data)
        if (res.code === 200 && res.data) {
          modelInfo.id = res.data.id
          modelInfo.modelKey = res.data.modelKey || modelInfo.modelKey
        }
      }

      if (res.code === 200) {
        window.$message?.success('保存成功')
        hasChanges.value = false
        emit('saved', { ...modelInfo })
        return true
      }
      else {
        window.$message?.error(res.message || '保存失败')
        return false
      }
    }
    catch (error) {
      console.error('保存失败:', error)
      window.$message?.error('保存失败')
      return false
    }
    finally {
      saving.value = false
    }
  }

  function normalizeBusinessGlobalFormBeforeSave() {
    if (!businessFormConfigActive.value)
      return
    if (modelInfo.formType === 'none' || modelInfo.formType === 'external')
      return
    if (!businessContextActive.value)
      return
    modelInfo.formType = 'business'
    const currentRef = parseBusinessGlobalFormRef(modelInfo.formJson)
    if (routeQueryText(currentRef.formKey)) {
      // 历史模型可能只保存了 formKey/objectCode；保存时补齐当前应用上下文，
      // 这样列表和下一次编辑都能准确回显用户选择的业务应用。
      if (!routeQueryText(currentRef.applicationId) && effectiveApplicationId.value) {
        modelInfo.formJson = buildBusinessGlobalFormJson({
          ...currentRef,
          applicationId: effectiveApplicationId.value,
          applicationName: businessApplicationName.value,
        })
      }
      return
    }
    ensureBusinessGlobalFormSelection()
    if (routeQueryText(parseBusinessGlobalFormRef(modelInfo.formJson).formKey))
      return
    modelInfo.formId = null
    modelInfo.formUrl = ''
    modelInfo.formJson = ''
    formSchema.value = []
  }

  function validateBusinessGlobalFormBeforeSave() {
    if (modelInfo.formType !== 'business')
      return true
    if (!businessFormConfigActive.value)
      return true
    if (routeQueryText(parseBusinessGlobalFormRef(modelInfo.formJson, '').formKey))
      return true

    const messageText = !effectiveApplicationId.value
      ? '请先选择业务应用'
      : !businessObjectCode.value
          ? '请先选择业务对象'
          : '请选择应用表单资产'
    window.$message?.warning(`${messageText}后再保存流程`)
    return false
  }

  async function handleDeploy() {
    if (deploying.value || saving.value)
      return
    try {
      deploying.value = true

      const xml = await getXmlForSave()
      if (!xml) {
        window.$message?.error('流程图验证失败，无法部署。请检查：\n1. 所有连接线是否完整连接\n2. 是否包含开始和结束节点')
        return
      }

      const saved = await handleSaveDraft(true)
      if (!saved)
        return

      if (!modelInfo.id) {
        window.$message?.error('请先保存模型')
        return
      }

      const res = await flowApi.deployModel(modelInfo.id)
      if (res.code === 200) {
        window.$message?.success('部署成功')
        await loadModel(modelInfo.id)
        emit('deployed', { ...modelInfo })
      }
      else {
        window.$message?.error(res.message || '部署失败')
      }
    }
    catch (error) {
      console.error('部署失败:', error)
      window.$message?.error('部署失败')
    }
    finally {
      deploying.value = false
    }
  }

  function handleBack() {
    const close = () => {
      if (props.embedded)
        emit('close')
      else
        router.back()
    }

    if (hasChanges.value) {
      window.$dialog?.warning({
        title: '提示',
        content: '有未保存的更改，确定要离开吗？',
        positiveText: '保存并离开',
        negativeText: '直接离开',
        onPositiveClick: async () => {
          await handleSaveDraft()
          close()
        },
        onNegativeClick: () => {
          close()
        },
      })
    }
    else {
      close()
    }
  }

  function handleModelerReady(modeler) {
    modelerInstance.value = modeler || modelerRef.value?.modeler?.() || null
  }

  function handleBusinessElementSelect(element) {
    if (!isBusinessDesigner.value) {
      dockedElement.value = null
      return
    }
    if (mut.businessSelectionClearTimer) {
      clearTimeout(mut.businessSelectionClearTimer)
      mut.businessSelectionClearTimer = null
    }
    if (element) {
      dockedElement.value = element
      return
    }
    mut.businessSelectionClearTimer = setTimeout(() => {
      dockedElement.value = null
      mut.businessSelectionClearTimer = null
    }, 80)
  }

  function handleBusinessPanelClose() {
    if (mut.businessSelectionClearTimer) {
      clearTimeout(mut.businessSelectionClearTimer)
      mut.businessSelectionClearTimer = null
    }
    dockedElement.value = null
    modelerRef.value?.clearSelection?.()
  }

  function getElementTitle(el) {
    if (!el)
      return '属性设置'
    const typeNames = {
      'bpmn:StartEvent': '开始节点',
      'bpmn:EndEvent': '结束节点',
      'bpmn:UserTask': '用户任务',
      'bpmn:ServiceTask': '服务任务',
      'bpmn:ScriptTask': '脚本任务',
      'bpmn:BusinessRuleTask': '业务规则任务',
      'bpmn:ManualTask': '手工任务',
      'bpmn:ExclusiveGateway': '排他网关',
      'bpmn:ParallelGateway': '并行网关',
      'bpmn:InclusiveGateway': '包容网关',
      'bpmn:SequenceFlow': '序列流',
      'bpmn:SubProcess': '子流程',
      'bpmn:CallActivity': '调用活动',
    }
    return el.businessObject?.name || typeNames[el.type] || '属性设置'
  }

  function getElementIcon(el) {
    const iconMap = {
      'bpmn:StartEvent': 'i-material-symbols:play-circle-outline',
      'bpmn:EndEvent': 'i-material-symbols:stop-circle-outline',
      'bpmn:UserTask': 'i-material-symbols:person-check-outline',
      'bpmn:ServiceTask': 'i-material-symbols:settings-outline',
      'bpmn:ScriptTask': 'i-material-symbols:code-blocks-outline',
      'bpmn:BusinessRuleTask': 'i-material-symbols:rule-settings-outline',
      'bpmn:ManualTask': 'i-material-symbols:pan-tool-outline',
      'bpmn:ExclusiveGateway': 'i-material-symbols:conversion-path-outline',
      'bpmn:ParallelGateway': 'i-material-symbols:call-split-outline',
      'bpmn:InclusiveGateway': 'i-material-symbols:merge-type-outline',
      'bpmn:SequenceFlow': 'i-material-symbols:arrow-right-alt',
      'bpmn:SubProcess': 'i-material-symbols:account-tree-outline',
      'bpmn:CallActivity': 'i-material-symbols:call-made',
    }
    return iconMap[el?.type] || 'i-material-symbols:tune'
  }
  __impl.extractJsonObject = extractJsonObject
  __impl.extractBpmnXml = extractBpmnXml
  __impl.ensureProcessId = ensureProcessId
  __impl.syncSafeModelKey = syncSafeModelKey
  __impl.normalizeBpmnId = normalizeBpmnId
  __impl.repairBpmnXml = repairBpmnXml
  __impl.parseXmlDocument = parseXmlDocument
  __impl.getXmlParseError = getXmlParseError
  __impl.hasBpmnDiagram = hasBpmnDiagram
  __impl.getBpmnDisplayIssue = getBpmnDisplayIssue
  __impl.rebuildDiagramInfo = rebuildDiagramInfo
  __impl.stripDiagramInfo = stripDiagramInfo
  __impl.ensureDiagramNamespaces = ensureDiagramNamespaces
  __impl.findElementByLocalName = findElementByLocalName
  __impl.findElementsByLocalName = findElementsByLocalName
  __impl.isBpmnFlowNode = isBpmnFlowNode
  __impl.layoutBpmnNodes = layoutBpmnNodes
  __impl.getBpmnNodeSize = getBpmnNodeSize
  __impl.buildDiagramXml = buildDiagramXml
  __impl.buildWaypoints = buildWaypoints
  __impl.escapeXmlAttr = escapeXmlAttr
  __impl.handleBpmnChange = handleBpmnChange
  __impl.handleDiagramImportStart = handleDiagramImportStart
  __impl.handleDiagramImportEnd = handleDiagramImportEnd
  __impl.handleSaveDraft = handleSaveDraft
  __impl.normalizeBusinessGlobalFormBeforeSave = normalizeBusinessGlobalFormBeforeSave
  __impl.validateBusinessGlobalFormBeforeSave = validateBusinessGlobalFormBeforeSave
  __impl.handleDeploy = handleDeploy
  __impl.handleBack = handleBack
  __impl.handleModelerReady = handleModelerReady
  __impl.handleBusinessElementSelect = handleBusinessElementSelect
  __impl.handleBusinessPanelClose = handleBusinessPanelClose
  __impl.getElementTitle = getElementTitle
  __impl.getElementIcon = getElementIcon

  return {
    ...deps,
  }
}
