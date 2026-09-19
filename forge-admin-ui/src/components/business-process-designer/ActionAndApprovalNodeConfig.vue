<script setup>
import { NButton, NEmpty, NSelect, NTag } from 'naive-ui'
import { computed, h, ref, watch } from 'vue'
import { ensureBusinessFlowStatusField } from '@/api/business-app'
import flowApi from '@/api/flow'
import DingFlowViewer from '@/components/flow-designer/viewer/DingFlowViewer.vue'
import BusinessFlowFormAssetSelect from '@/views/app-center/components/designer/BusinessFlowFormAssetSelect.vue'
import TemplateVariableEditor from '@/views/app-center/components/designer/TemplateVariableEditor.vue'
import { ACTION_NODE_TEMPLATES, createActionTemplateConfig } from './node-templates.js'

const props = defineProps({
  node: { type: Object, required: true },
  objectId: { type: String, default: '' },
  objectCode: { type: String, default: '' },
  objectName: { type: String, default: '' },
  objects: { type: Array, default: () => [] },
  fields: { type: Array, default: () => [] },
  flowModels: { type: Array, default: () => [] },
  formAssets: { type: Array, default: () => [] },
  businessActions: { type: Array, default: () => [] },
  messageTemplates: { type: Array, default: () => [] },
  capabilities: { type: Array, default: () => [] },
  subProcesses: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:config', 'openFlowDesigner', 'refreshFlowModel', 'refreshFields', 'editAction'])

const localConfig = ref(clone(props.node.config))
const optimisticFlowModel = ref(null)
const selectedTemplate = ref('')
const creatingModel = ref(false)
const previewXml = ref('')
const previewLoading = ref(false)
const autoBoundNodeId = ref('')
const ensuringStatusField = ref(false)

const designableFlowModels = computed(() => (props.flowModels || []).filter((item) => {
  const designerType = String(item.designerType || '').toLowerCase()
  return designerType !== 'business' && Boolean(modelKey(item))
}))

const selectedFlowModel = computed(() => {
  const key = stringValue(localConfig.value.flowModelKey)
  if (!key)
    return null
  return designableFlowModels.value.find(item => modelKey(item) === key)
    || (modelKey(optimisticFlowModel.value) === key ? optimisticFlowModel.value : null)
    || {
      modelId: stringValue(localConfig.value.flowModelId),
      modelKey: key,
      modelName: localConfig.value.flowModelName || key,
      deployed: false,
      pending: true,
    }
})

const selectableFlowModels = computed(() => {
  const published = designableFlowModels.value.filter(isDeployedFlowModel)
  const selected = selectedFlowModel.value
  if (!selected || published.some(item => modelKey(item) === modelKey(selected)))
    return published
  return [selected, ...published]
})

const flowModelOptions = computed(() => selectableFlowModels.value.map(item => ({
  label: isDeployedFlowModel(item)
    ? (item.modelName || item.name || modelKey(item))
    : `${item.modelName || item.name || modelKey(item)}（待发布）`,
  value: modelKey(item),
  modelKey: modelKey(item),
  version: item.version,
  deployed: isDeployedFlowModel(item),
  // 支持按名称或 modelKey 搜索（默认只匹配 label）
  filter: (pattern) => {
    const keyword = String(pattern || '').toLowerCase()
    return !keyword
      || String(item.modelName || item.name || '').toLowerCase().includes(keyword)
      || modelKey(item).toLowerCase().includes(keyword)
  },
})))

const resolvedFormAssets = computed(() => (props.formAssets || []).filter(item => stringValue(item?.formKey)))

const fieldOptions = computed(() => props.fields.map(item => ({
  label: item.fieldName || item.fieldLabel || item.label || item.fieldCode || item.code || item.columnName,
  value: item.fieldCode || item.field || item.code || item.value || item.columnName,
  columnName: item.columnName || item.column || '',
})).filter(item => item.value || item.columnName))

const statusFieldOptions = computed(() => fieldOptions.value.filter(item => isFlowStatusField(item)))

const suggestedStatusField = computed(() => {
  return fieldOptions.value.find(item => isFlowStatusField(item))?.value || ''
})

const hasIndependentFlowStatus = computed(() => Boolean(suggestedStatusField.value))
const usesIndependentFlowStatus = computed(() => isFlowStatusField(localConfig.value.statusField))

const selectedFlowModelId = computed(() => {
  const item = selectedFlowModel.value
  return stringValue(item?.modelId || item?.id || localConfig.value.flowModelId)
})

watch(() => props.node, (value) => {
  localConfig.value = clone(value?.config)
  selectedTemplate.value = ''
  if (value?.type === 'APPROVAL' && value.id !== autoBoundNodeId.value) {
    autoBoundNodeId.value = value.id
    ensureDefaultApprovalBindings()
  }
}, { deep: true, immediate: true })

watch(() => props.flowModels, (models) => {
  const optimisticKey = modelKey(optimisticFlowModel.value)
  if (optimisticKey && (models || []).some(item => modelKey(item) === optimisticKey))
    optimisticFlowModel.value = null
}, { deep: true })

watch(() => props.formAssets, () => {
  if (props.node?.type === 'APPROVAL' && !stringValue(localConfig.value.formAsset?.formKey))
    ensureDefaultApprovalBindings()
})

watch(() => props.fields, async () => {
  if (props.node?.type === 'APPROVAL')
    await ensureDefaultApprovalBindings()
}, { deep: true })

watch(selectedFlowModel, async (model) => {
  const modelId = stringValue(model?.modelId || model?.id || localConfig.value.flowModelId)
  previewXml.value = ''
  if (!modelId)
    return
  previewLoading.value = true
  try {
    const response = await flowApi.getModelDetail(modelId)
    previewXml.value = String(response?.data?.bpmnXml || response?.bpmnXml || '')
  }
  catch {
    previewXml.value = ''
  }
  finally {
    previewLoading.value = false
  }
}, { immediate: true })

function applyActionTemplate(value) {
  const config = createActionTemplateConfig(value, { objectCode: props.objectCode })
  if (!config)
    return
  selectedTemplate.value = value
  localConfig.value = config
  emit('update:config', clone(localConfig.value))
}

function patchConfig(patch) {
  localConfig.value = { ...localConfig.value, ...clone(patch) }
  emit('update:config', clone(localConfig.value))
}

function handleActionType(event) {
  const actionType = event.target.value
  const keep = { actionType }
  selectedTemplate.value = ''
  if (['UPDATE_RECORD', 'CREATE_RECORD'].includes(actionType)) {
    Object.assign(keep, targetObjectRef(props.objectCode))
  }
  localConfig.value = keep
  emit('update:config', clone(localConfig.value))
}

function handleFlowModelKey(key) {
  const item = selectableFlowModels.value.find(model => modelKey(model) === key)
  const defaultTitle = localConfig.value.titleTemplate || defaultApprovalTitle()
  patchConfig({
    flowModelKey: key || '',
    flowModelName: item?.modelName || item?.name || key || '',
    flowModelId: stringValue(item?.modelId || item?.id),
    versionPolicy: 'PINNED_AT_APPLICATION_PUBLISH',
    titleTemplate: defaultTitle,
    statusField: localConfig.value.statusField || suggestedStatusField.value,
    formAsset: localConfig.value.formAsset?.formKey
      ? localConfig.value.formAsset
      : toFormAssetRef(resolvedFormAssets.value[0]),
  })
}

function defaultApprovalTitle() {
  return '${starterName}发起的${objectName}审批单'
}

const titlePresets = computed(() => {
  const objectLabel = props.objectName || '业务'
  const nameField = fieldOptions.value.find(item => ['name', 'title', 'subject', 'orderNo'].includes(item.value))
    || fieldOptions.value[0]
  const presets = [
    {
      label: `张三发起的${objectLabel}审批单`,
      value: '${starterName}发起的${objectName}审批单',
    },
  ]
  if (nameField?.value) {
    presets.push({
      label: `${objectLabel}-${nameField.label || nameField.value}`,
      value: `${objectLabel}-\${${nameField.value}}`,
    })
  }
  return presets
})

const titleVariables = computed(() => [
  { label: '发起人', value: 'starterName', sampleValue: '张三' },
  { label: '业务名称', value: 'objectName', sampleValue: props.objectName || '测试页面' },
])

function openFlowDesigner() {
  if (!selectedFlowModelId.value)
    return
  const payload = {
    modelId: selectedFlowModelId.value,
    modelKey: modelKey(selectedFlowModel.value),
    businessFormKey: stringValue(localConfig.value.formAsset?.formKey),
    applicationId: stringValue(localConfig.value.formAsset?.applicationId),
  }
  emit('openFlowDesigner', payload)
}

async function createAndDesign() {
  if (creatingModel.value)
    return
  creatingModel.value = true
  try {
    const objectLabel = props.objectName || props.objectCode || '业务'
    const modelKeyValue = `${String(props.objectCode || 'biz').replace(/\W/g, '_')}_approval_${Date.now().toString(36)}`
    const defaultForm = toFormAssetRef(localConfig.value.formAsset?.formKey
      ? localConfig.value.formAsset
      : resolvedFormAssets.value[0])
    const response = await flowApi.createModel({
      modelName: `${objectLabel}审批`,
      modelKey: modelKeyValue,
      designerType: 'approval',
      formType: 'business',
      flowType: 'approval',
      description: '由应用业务流程审批节点创建',
      formJson: defaultForm.formKey
        ? JSON.stringify({
            type: defaultForm.formMode || 'BUSINESS_OBJECT_FORM',
            formMode: defaultForm.formMode || 'BUSINESS_OBJECT_FORM',
            objectCode: props.objectCode,
            objectName: objectLabel,
            formKey: defaultForm.formKey,
            formName: defaultForm.formName,
            providerKey: defaultForm.providerKey || '',
            applicationId: defaultForm.applicationId || '',
            pageId: defaultForm.pageId || '',
            pageCode: defaultForm.pageCode || '',
            pageName: defaultForm.pageName || '',
            pageType: defaultForm.pageType || '',
            sourceFormKey: defaultForm.sourceFormKey || '',
            viewKey: 'default',
          })
        : undefined,
    })
    const created = response?.data || {}
    const modelId = stringValue(created.id || created.modelId)
    const createdModelKey = created.modelKey || modelKeyValue
    optimisticFlowModel.value = {
      ...created,
      modelId,
      modelKey: createdModelKey,
      modelName: created.modelName || `${objectLabel}审批`,
      deployed: false,
      status: created.status ?? 0,
      pending: true,
    }
    patchConfig({
      flowModelKey: createdModelKey,
      flowModelName: created.modelName || `${objectLabel}审批`,
      flowModelId: modelId,
      versionPolicy: 'PINNED_AT_APPLICATION_PUBLISH',
      titleTemplate: localConfig.value.titleTemplate || defaultApprovalTitle(),
      statusField: localConfig.value.statusField || suggestedStatusField.value,
      formAsset: defaultForm.formKey ? defaultForm : {},
    })
    emit('refreshFlowModel', createdModelKey)
    if (modelId) {
      emit('openFlowDesigner', {
        modelId,
        modelKey: createdModelKey,
        businessFormKey: stringValue(defaultForm.formKey),
        applicationId: stringValue(defaultForm.applicationId),
      })
    }
  }
  catch (error) {
    window.$message?.error(error?.message || '新建审批流程失败')
  }
  finally {
    creatingModel.value = false
  }
}

function updateSingleReference(key, event) {
  patchConfig({ [key]: event.target.value || null })
}

function handleTargetObjectChange(event) {
  patchConfig(targetObjectRef(event.target.value))
}

function targetObjectRef(code) {
  const object = (props.objects || []).find(item => objectOptionValue(item) === code)
  const current = code === props.objectCode
  return {
    objectCode: code || null,
    targetObjectId: stringValue(object?.objectId || object?.id || (current ? props.objectId : '')) || null,
    targetConfigKey: stringValue(object?.configKey) || null,
  }
}

function handleFormAssetUpdate(payload) {
  patchConfig({
    formAsset: payload?.formKey ? toFormAssetRef(payload) : {},
  })
}

async function ensureDefaultApprovalBindings() {
  if (props.node?.type !== 'APPROVAL')
    return
  // 防止 ensureFlowStatusField 异步过程中重复进入
  if (ensuringStatusField.value)
    return
  const patch = {}
  const currentFormKey = stringValue(localConfig.value.formAsset?.formKey)
  const currentProviderKey = stringValue(localConfig.value.formAsset?.providerKey)
  const currentFormAvailable = resolvedFormAssets.value.some(item =>
    formAssetKey(item) === currentFormKey
    && (!currentProviderKey || stringValue(item.providerKey) === currentProviderKey),
  )
  if (resolvedFormAssets.value[0] && (!currentFormKey || (!currentFormAvailable
    && !isStableApplicationFormRef(localConfig.value.formAsset)))) {
    patch.formAsset = toFormAssetRef(resolvedFormAssets.value[0])
  }
  else if (!resolvedFormAssets.value.length
    && currentFormKey
    && String(localConfig.value.formAsset?.formMode || 'BUSINESS_OBJECT_FORM').toUpperCase() !== 'EXTERNAL'
    && !isStableApplicationFormRef(localConfig.value.formAsset)) {
    patch.formAsset = {}
  }
  if (!usesIndependentFlowStatus.value && suggestedStatusField.value) {
    patch.statusField = suggestedStatusField.value
  }
  if (!stringValue(localConfig.value.titleTemplate)) {
    patch.titleTemplate = defaultApprovalTitle()
  }
  if (Object.keys(patch).length)
    patchConfig(patch)
  // 自动创建 flowStatus 字段（无需用户手动点击）；静默失败，创建成功后由 fields 刷新重入本函数
  if (!hasIndependentFlowStatus.value && props.objectId)
    await ensureFlowStatusField({ silent: true })
}

/**
 * 应用页面表单的 formKey 同时携带 applicationId/pageId，属于可恢复的稳定引用。
 * 资产目录异步加载失败或暂时为空时不能把它当成已删除表单清掉，否则用户重新打开
 * 流程草稿会看到“任务表单”消失，保存还会覆盖原有绑定。
 */
function isStableApplicationFormRef(formAsset = {}) {
  if (!formAsset || typeof formAsset !== 'object')
    return false
  const formKey = stringValue(formAsset.formKey)
  const applicationId = stringValue(formAsset.applicationId)
  const pageId = stringValue(formAsset.pageId)
  return formKey.startsWith('app_') || (Boolean(applicationId) && Boolean(pageId))
}

async function ensureFlowStatusField({ silent = false } = {}) {
  if (ensuringStatusField.value)
    return
  if (!props.objectId) {
    if (!silent)
      window.$message?.warning('当前流程未关联有效业务对象，无法添加流程状态字段')
    return
  }
  ensuringStatusField.value = true
  try {
    const response = await ensureBusinessFlowStatusField(props.objectId)
    const field = response?.data || {}
    const fieldCode = stringValue(field.fieldCode || field.field || 'flowStatus')
    patchConfig({ statusField: isFlowStatusField(fieldCode) ? fieldCode : 'flowStatus' })
    emit('refreshFields', field)
    if (!silent)
      window.$message?.success('流程状态字段已添加')
  }
  catch (error) {
    // 自动触发时静默失败（如缺少 DDL 权限），仅手动点击时提示
    if (!silent)
      window.$message?.error(error?.response?.data?.message || error?.message || '添加流程状态字段失败')
  }
  finally {
    ensuringStatusField.value = false
  }
}

function isFlowStatusField(value) {
  const candidates = typeof value === 'object' && value !== null
    ? [value.value, value.fieldCode, value.field, value.columnName, value.column]
    : [value]
  return candidates.some(candidate => stringValue(candidate).replace(/[-_]/g, '').toLowerCase() === 'flowstatus')
}

function renderFlowModelOption({ node, option }) {
  // 必须渲染 naive-ui 传入的默认 node（已绑定点击选择与选中态），仅在下方追加副标题
  const subtitle = [
    option.modelKey || '',
    option.version ? `v${option.version}` : '',
    option.deployed ? '已部署' : '待发布',
  ].filter(Boolean).join(' · ')
  return h('div', { class: 'flow-model-option' }, [
    node,
    subtitle ? h('span', { class: 'flow-model-option-key' }, subtitle) : null,
  ])
}

function toFormAssetRef(item) {
  if (!item)
    return {}
  const formKey = formAssetKey(item)
  if (!formKey)
    return {}
  return {
    formKey,
    formName: item.formName || item.name || item.label || formKey,
    formMode: item.formMode || item.type || 'BUSINESS_OBJECT_FORM',
    providerKey: item.providerKey || undefined,
    applicationId: item.applicationId || undefined,
    pageId: item.pageId || undefined,
    pageCode: item.pageCode || undefined,
    pageName: item.pageName || undefined,
    pageType: item.pageType || undefined,
    sourceFormKey: item.sourceFormKey || undefined,
  }
}

function addFieldMapping() {
  patchConfig({
    fieldMappings: [
      ...(localConfig.value.fieldMappings || []),
      { field: '', valueSource: 'CONSTANT', value: '' },
    ],
  })
}

function updateFieldMapping(index, patch) {
  const mappings = clone(localConfig.value.fieldMappings || [])
  mappings[index] = { ...mappings[index], ...patch }
  patchConfig({ fieldMappings: mappings })
}

function removeFieldMapping(index) {
  const mappings = clone(localConfig.value.fieldMappings || [])
  mappings.splice(index, 1)
  patchConfig({ fieldMappings: mappings })
}

function modelKey(item) {
  return stringValue(item?.modelKey || item?.key || item?.value)
}

function isDeployedFlowModel(item) {
  return item?.deployed === true || Boolean(item?.deploymentId)
}

function optionValue(item) {
  return stringValue(
    item?.formKey
    || item?.assetKey
    || item?.value
    || item?.code
    || item?.actionCode
    || item?.templateCode
    || item?.processCode
    || item?.objectCode,
  )
}

function optionLabel(item) {
  return item?.objectName
    || item?.pageName
    || item?.formName
    || item?.label
    || item?.name
    || item?.actionName
    || item?.templateName
    || item?.processName
    || optionValue(item)
}

function objectOptionLabel(item, current = false) {
  const label = item?.objectName || item?.pageName || item?.name || item?.label || '未命名业务对象'
  return current ? `当前主对象（${label}）` : label
}

function objectOptionValue(item) {
  return stringValue(item?.objectCode || item?.configKey || optionValue(item))
}

function formAssetKey(item) {
  return stringValue(item?.formKey || item?.assetKey || item?.value || item?.code)
}

function stringValue(value) {
  return value == null ? '' : String(value)
}

function clone(value) {
  return JSON.parse(JSON.stringify(value || {}))
}
</script>

<template>
  <div class="execution-node-config">
    <template v-if="node.type === 'ACTION'">
      <section class="template-section" aria-label="动作节点场景模板">
        <div class="template-section-head">
          <strong>场景模板</strong>
          <span>选择后仍可继续调整</span>
        </div>
        <div class="template-grid">
          <button
            v-for="item in ACTION_NODE_TEMPLATES"
            :key="item.value"
            type="button"
            class="template-card"
            :class="{ 'is-selected': selectedTemplate === item.value }"
            :data-action-template="item.value"
            @click="applyActionTemplate(item.value)"
          >
            <strong>{{ item.label }}</strong>
            <span>{{ item.description }}</span>
          </button>
        </div>
      </section>

      <label class="config-field">
        <span>执行动作</span>
        <select :value="localConfig.actionType || 'UPDATE_RECORD'" @change="handleActionType">
          <option value="UPDATE_RECORD">更新记录</option>
          <option value="CREATE_RECORD">创建记录</option>
          <option value="BUSINESS_ACTION">复用业务动作</option>
          <option value="SEND_MESSAGE">发送消息</option>
          <option value="INVOKE_CAPABILITY">调用受治理能力</option>
        </select>
      </label>

      <template v-if="['UPDATE_RECORD', 'CREATE_RECORD'].includes(localConfig.actionType)">
        <label class="config-field">
          <span>目标业务对象</span>
          <select :value="localConfig.objectCode || objectCode" @change="handleTargetObjectChange">
            <option :value="objectCode">{{ objectOptionLabel({ objectCode, objectName }, true) }}</option>
            <option v-for="item in objects" :key="objectOptionValue(item)" :value="objectOptionValue(item)">
              {{ objectOptionLabel(item) }}
            </option>
          </select>
        </label>
        <section class="mapping-card">
          <div class="mapping-head">
            <span>字段赋值</span>
            <button type="button" @click="addFieldMapping">
              添加字段
            </button>
          </div>
          <div v-for="(mapping, index) in localConfig.fieldMappings || []" :key="index" class="mapping-row">
            <select :value="mapping.field" @change="updateFieldMapping(index, { field: $event.target.value })">
              <option value="">
                选择字段
              </option>
              <option v-for="item in fieldOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
            <input
              :value="mapping.value ?? ''"
              placeholder="固定值"
              @input="updateFieldMapping(index, { value: $event.target.value })"
            >
            <button type="button" class="remove-row" @click="removeFieldMapping(index)">
              删除
            </button>
          </div>
          <p v-if="!localConfig.fieldMappings?.length">
            按需添加受控字段赋值，不执行任意数据库语句。
          </p>
        </section>
      </template>

      <div v-else-if="localConfig.actionType === 'BUSINESS_ACTION'" class="config-field">
        <span>业务动作</span>
        <select :value="localConfig.businessActionCode || ''" @change="updateSingleReference('businessActionCode', $event)">
          <option value="">
            选择业务动作
          </option>
          <option v-for="item in businessActions" :key="optionValue(item)" :value="optionValue(item)">
            {{ optionLabel(item) }}
          </option>
        </select>
        <div class="action-edit-hint">
          <button
            v-if="localConfig.businessActionCode"
            type="button"
            class="link-button"
            @click="emit('editAction', { actionCode: localConfig.businessActionCode, isNew: false, nodeId: node.id })"
          >
            编辑该动作的执行步骤
          </button>
          <button
            type="button"
            class="link-button"
            @click="emit('editAction', { actionCode: '', isNew: true, nodeId: node.id })"
          >
            ＋ 新建业务动作
          </button>
          <span class="hint-text">动作是一组自动执行的步骤（校验、改状态、写记录等），保存后可在多个流程中复用</span>
        </div>
      </div>

      <label v-else-if="localConfig.actionType === 'SEND_MESSAGE'" class="config-field">
        <span>消息模板</span>
        <select :value="localConfig.messageTemplateCode || ''" @change="updateSingleReference('messageTemplateCode', $event)">
          <option value="">选择已启用消息模板</option>
          <option v-for="item in messageTemplates" :key="optionValue(item)" :value="optionValue(item)">
            {{ optionLabel(item) }}
          </option>
        </select>
      </label>

      <label v-else class="config-field">
        <span>平台能力</span>
        <select :value="localConfig.capabilityCode || ''" @change="updateSingleReference('capabilityCode', $event)">
          <option value="">选择已授权的受治理能力</option>
          <option v-for="item in capabilities" :key="optionValue(item)" :value="optionValue(item)">
            {{ optionLabel(item) }}
          </option>
        </select>
        <small>这里只保存能力编码，连接凭据和目标地址由服务端治理。</small>
      </label>
    </template>

    <template v-else-if="node.type === 'APPROVAL'">
      <div class="approval-config-layout">
        <div class="approval-config-main">
          <label class="config-field">
            <span>审批流程</span>
            <div class="approval-model-row">
              <NSelect
                :value="localConfig.flowModelKey || null"
                filterable
                clearable
                :options="flowModelOptions"
                placeholder="搜索已发布的审批模型"
                :render-option="renderFlowModelOption"
                @update:value="handleFlowModelKey"
              />
              <NButton size="small" secondary :loading="creatingModel" @click="createAndDesign">
                新建并设计
              </NButton>
            </div>
            <small>
              可选择已部署模型；新建模型会自动绑定当前节点，完成设计并发布后即可随业务流程发布。
            </small>
            <div v-if="!flowModelOptions.length && !creatingModel" class="flow-model-empty-guide">
              <span>暂无已发布的审批模型。点击「新建并设计」创建，当前节点会自动绑定并等待发布。</span>
            </div>
          </label>

          <label class="config-field">
            <span>审批标题</span>
            <div class="title-presets">
              <button
                v-for="preset in titlePresets"
                :key="preset.value"
                type="button"
                class="title-preset"
                :class="{ active: localConfig.titleTemplate === preset.value }"
                @click="patchConfig({ titleTemplate: preset.value })"
              >
                {{ preset.label }}
              </button>
            </div>
            <TemplateVariableEditor
              :model-value="localConfig.titleTemplate || ''"
              :fields="fieldOptions"
              :variables="titleVariables"
              placeholder="点上面的默认标题，或自己写，例如 ${starterName}发起的${objectName}审批单"
              @update:model-value="patchConfig({ titleTemplate: $event })"
            />
            <small>点默认标题直接套用，也可以在输入框里改。发起人、业务名称和单据字段会在发起时替换。</small>
          </label>

          <label class="config-field">
            <span>任务表单</span>
            <BusinessFlowFormAssetSelect
              :node-form="localConfig.formAsset || {}"
              :form-assets="resolvedFormAssets"
              show-all-modes
              @update="handleFormAssetUpdate"
            />
            <small v-if="!resolvedFormAssets.length" class="catalog-empty-tip">
              当前对象还没有可绑定的表单，请先完成对象表单设计。
            </small>
            <small v-else>
              已默认绑定当前对象表单，审批待办会打开这张表。
            </small>
          </label>

          <label class="config-field">
            <span>流程状态字段</span>
            <NSelect
              v-if="hasIndependentFlowStatus"
              :value="localConfig.statusField || suggestedStatusField || null"
              filterable
              :options="statusFieldOptions"
              placeholder="选择回写到业务对象的状态字段"
              @update:value="patchConfig({ statusField: $event || '' })"
            />
            <div v-else class="flow-status-provision">
              <div>
                <strong>尚未添加独立流程状态</strong>
                <span>系统将创建只读字段 flowStatus，并仅追加数据库列 flow_status。</span>
              </div>
              <NButton
                size="small"
                type="primary"
                secondary
                :loading="ensuringStatusField"
                @click="ensureFlowStatusField"
              >
                一键添加流程状态字段
              </NButton>
            </div>
            <small :class="{ 'status-field-warning': localConfig.statusField && !usesIndependentFlowStatus }">
              发起写入 IN_PROCESS，通过、驳回、取消分别写入 APPROVED、REJECTED、CANCELED；业务自己的“状态”字段不会被流程修改。
            </small>
          </label>
        </div>

        <aside class="approval-preview-card">
          <div class="approval-preview-head">
            <div>
              <strong>{{ selectedFlowModel?.modelName || selectedFlowModel?.flowModelName || localConfig.flowModelName || '审批流程图' }}</strong>
              <NTag
                v-if="selectedFlowModel"
                size="small"
                :type="isDeployedFlowModel(selectedFlowModel) ? 'success' : 'warning'"
                :bordered="false"
              >
                {{ isDeployedFlowModel(selectedFlowModel) ? '已部署' : '待发布，可继续设计' }}
              </NTag>
            </div>
            <button
              type="button"
              class="open-flow-designer-button"
              :disabled="!selectedFlowModelId"
              @click="openFlowDesigner"
            >
              在本页设计
            </button>
          </div>
          <div class="approval-preview-canvas">
            <DingFlowViewer
              v-if="previewXml"
              compact
              :bpmn-xml="previewXml"
            />
            <NEmpty
              v-else
              size="small"
              :description="previewLoading ? '流程图加载中…' : (selectedFlowModel ? '保存审批模型后可在此预览流程图' : '选择或新建审批流程后在此预览')"
            />
          </div>
        </aside>
      </div>

      <div class="approval-boundary-card">
        <strong>审批内部配置</strong>
        <p>审批人、会签、驳回和字段权限在真实流程设计器中维护；业务画布只固定发布版本并处理四种结果。</p>
      </div>
    </template>

    <template v-else>
      <label class="config-field">
        <span>业务子流程</span>
        <select :value="localConfig.processCode || ''" @change="updateSingleReference('processCode', $event)">
          <option value="">选择当前应用已发布流程</option>
          <option v-for="item in subProcesses" :key="optionValue(item)" :value="optionValue(item)">
            {{ optionLabel(item) }}
          </option>
        </select>
        <small>服务端会检查直接或间接循环，并限制最大调用深度。</small>
      </label>
    </template>
  </div>
</template>

<style scoped>
.execution-node-config {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.template-section {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.template-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.template-section-head strong {
  color: var(--text-color-1, #0f172a);
  font-size: 13px;
}

.template-section-head span {
  color: var(--text-color-3, #64748b);
  font-size: 12px;
}

.template-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.template-card {
  min-width: 0;
  min-height: 76px;
  border: 1px solid rgba(148, 163, 184, 0.38);
  border-radius: 7px;
  background: var(--card-color, #fff);
  padding: 10px;
  text-align: left;
}

.template-card:hover,
.template-card.is-selected {
  border-color: var(--primary-color, #2563eb);
  background: rgba(37, 99, 235, 0.05);
}

.template-card strong,
.template-card span {
  display: block;
}

.template-card strong {
  color: var(--text-color-1, #0f172a);
  font-size: 13px;
}

.template-card span {
  margin-top: 5px;
  color: var(--text-color-3, #64748b);
  font-size: 12px;
  line-height: 1.45;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: var(--text-color-2, #334155);
  font-size: 13px;
}

.config-field > span,
.mapping-head > span {
  color: var(--text-color-1, #0f172a);
  font-weight: 600;
}

.config-field select,
.config-field input,
.mapping-row select,
.mapping-row input {
  box-sizing: border-box;
  width: 100%;
  min-height: 34px;
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 6px;
  background: var(--input-color, #fff);
  padding: 6px 9px;
  color: var(--text-color-1, #0f172a);
  outline: none;
}

.config-field select:focus,
.config-field input:focus,
.mapping-row select:focus,
.mapping-row input:focus {
  border-color: var(--primary-color, #2563eb);
}

.title-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.title-preset {
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
  color: #334155;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.4;
  padding: 4px 8px;
}

.title-preset.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

.config-field small,
.mapping-card p,
.approval-boundary-card p {
  margin: 0;
  color: var(--text-color-3, #64748b);
  font-size: 12px;
  line-height: 1.6;
}

.mapping-card,
.approval-boundary-card {
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 7px;
  background: rgba(241, 245, 249, 0.58);
  padding: 12px;
}

.flow-status-provision {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(245, 158, 11, 0.25);
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.04);
  padding: 11px 12px;
  transition: opacity 200ms ease;
}

.flow-status-provision > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.flow-status-provision strong {
  color: var(--text-color-1, #0f172a);
  font-size: 12px;
}

.flow-status-provision span,
.status-field-warning {
  color: var(--warning-color, #c17a16) !important;
  font-size: 11px;
  line-height: 1.5;
}

.mapping-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.mapping-head button,
.open-flow-designer-button {
  border: 1px solid rgba(37, 99, 235, 0.35);
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.06);
  padding: 6px 10px;
  color: var(--primary-color, #2563eb);
  font-size: 12px;
}

.mapping-row {
  display: grid;
  align-items: center;
  gap: 7px;
  margin-top: 8px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
}

.remove-row {
  white-space: nowrap;
  color: var(--error-color, #dc2626);
  font-size: 12px;
}

.approval-boundary-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.open-flow-designer-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.approval-config-layout {
  display: grid;
  gap: 18px;
  grid-template-columns: minmax(0, 1fr);
}

.approval-config-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.approval-model-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
}

.approval-model-row :deep(.n-select) {
  flex: 1;
  min-width: 0;
}

.approval-preview-card {
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.6);
  padding: 14px;
  transition: opacity 200ms ease;
}

@container node-config (min-width: 700px) {
  .approval-config-layout {
    grid-template-columns: minmax(340px, 1.15fr) minmax(280px, 0.85fr);
    align-items: start;
  }
}

@container node-config (max-width: 560px) {
  .approval-model-row,
  .flow-status-provision {
    align-items: stretch;
    flex-direction: column;
  }

  .mapping-row {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .mapping-row .remove-row {
    justify-self: end;
    grid-column: 1 / -1;
  }
}

@container node-config (max-width: 480px) {
  .template-grid,
  .mapping-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .mapping-row .remove-row {
    grid-column: auto;
  }
}

.approval-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.approval-preview-head strong {
  margin-right: 8px;
}

.approval-preview-canvas {
  min-height: 240px;
  overflow: auto;
  background: #fff;
  border-radius: 6px;
}

.action-edit-hint {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.link-button {
  padding: 0;
  border: none;
  background: none;
  color: #18a058;
  font-size: 13px;
  cursor: pointer;
}

.link-button:hover {
  text-decoration: underline;
}

.action-edit-hint .hint-text {
  font-size: 12px;
  color: #86909c;
}

.flow-model-empty-guide {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.04);
  border: 1px dashed rgba(37, 99, 235, 0.2);
}

.flow-model-empty-guide span {
  color: var(--text-color-3, #64748b);
  font-size: 12px;
  line-height: 1.55;
}
</style>

<style>
/* NSelect 下拉菜单 teleport 到 body，scoped 样式无法命中，需全局样式 */
.flow-model-option {
  display: flex;
  flex-direction: column;
}

.flow-model-option-key {
  padding-left: 12px;
  padding-bottom: 3px;
  color: #94a3b8;
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  line-height: 1.4;
}
</style>
