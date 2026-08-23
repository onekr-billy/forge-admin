<script setup>
import { NButton, NEmpty, NSelect, NTag } from 'naive-ui'
import { computed, defineAsyncComponent, ref, watch } from 'vue'
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

const FlowDesignPage = defineAsyncComponent(() => import('@/views/flow/design.vue'))

const localConfig = ref(clone(props.node.config))
const flowDesignerVisible = ref(false)
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

const flowModelOptions = computed(() => designableFlowModels.value.map(item => ({
  label: item.modelName || item.name || modelKey(item),
  value: modelKey(item),
  deployed: item.deployed === true || Boolean(item.deploymentId),
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

const selectedFlowModel = computed(() => designableFlowModels.value.find(item =>
  modelKey(item) === localConfig.value.flowModelKey,
) || null)

const selectedFlowModelId = computed(() => {
  const item = selectedFlowModel.value
  return stringValue(item?.modelId || item?.id)
})

watch(() => props.node, (value) => {
  localConfig.value = clone(value?.config)
  selectedTemplate.value = ''
  if (value?.type === 'APPROVAL' && value.id !== autoBoundNodeId.value) {
    autoBoundNodeId.value = value.id
    ensureDefaultApprovalBindings()
  }
}, { deep: true, immediate: true })

watch(() => props.formAssets, () => {
  if (props.node?.type === 'APPROVAL' && !stringValue(localConfig.value.formAsset?.formKey))
    ensureDefaultApprovalBindings()
})

watch(() => props.fields, () => {
  if (props.node?.type === 'APPROVAL')
    ensureDefaultApprovalBindings()
}, { deep: true })

watch(selectedFlowModelId, async (modelId) => {
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
  const item = designableFlowModels.value.find(model => modelKey(model) === key)
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
  const nameField = fieldOptions.value.find(item => ['name', 'title', 'subject', 'orderNo'].includes(item.value))
    || fieldOptions.value[0]
  const objectLabel = props.objectName || props.objectCode || '业务'
  return nameField ? `${objectLabel}-\${${nameField.value}}` : `${objectLabel}审批`
}

function openFlowDesigner() {
  if (!selectedFlowModelId.value)
    return
  const payload = {
    modelId: selectedFlowModelId.value,
    modelKey: modelKey(selectedFlowModel.value),
  }
  flowDesignerVisible.value = true
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
    patchConfig({
      flowModelKey: created.modelKey || modelKeyValue,
      flowModelName: created.modelName || `${objectLabel}审批`,
      flowModelId: modelId,
      versionPolicy: 'PINNED_AT_APPLICATION_PUBLISH',
      titleTemplate: localConfig.value.titleTemplate || defaultApprovalTitle(),
      statusField: localConfig.value.statusField || suggestedStatusField.value,
      formAsset: defaultForm.formKey ? defaultForm : {},
    })
    emit('refreshFlowModel', created.modelKey || modelKeyValue)
    if (modelId) {
      flowDesignerVisible.value = true
      emit('openFlowDesigner', { modelId, modelKey: created.modelKey || modelKeyValue })
    }
  }
  catch (error) {
    window.$message?.error(error?.message || '新建审批流程失败')
  }
  finally {
    creatingModel.value = false
  }
}

function handleFlowDesignerClosed() {
  flowDesignerVisible.value = false
  emit('refreshFlowModel', modelKey(selectedFlowModel.value))
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

function ensureDefaultApprovalBindings() {
  if (props.node?.type !== 'APPROVAL')
    return
  const patch = {}
  const currentFormKey = stringValue(localConfig.value.formAsset?.formKey)
  const currentProviderKey = stringValue(localConfig.value.formAsset?.providerKey)
  const currentFormAvailable = resolvedFormAssets.value.some(item =>
    formAssetKey(item) === currentFormKey
    && (!currentProviderKey || stringValue(item.providerKey) === currentProviderKey),
  )
  if (resolvedFormAssets.value[0] && (!currentFormKey || !currentFormAvailable)) {
    patch.formAsset = toFormAssetRef(resolvedFormAssets.value[0])
  }
  else if (!resolvedFormAssets.value.length
    && currentFormKey
    && String(localConfig.value.formAsset?.formMode || 'BUSINESS_OBJECT_FORM').toUpperCase() !== 'EXTERNAL') {
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
}

async function ensureFlowStatusField() {
  if (ensuringStatusField.value)
    return
  if (!props.objectId) {
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
    window.$message?.success('流程状态字段已添加，数据库列已安全同步')
  }
  catch (error) {
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
                placeholder="搜索已有审批模型"
                @update:value="handleFlowModelKey"
              />
              <NButton size="small" secondary :loading="creatingModel" @click="createAndDesign">
                新建并设计
              </NButton>
            </div>
            <small>
              可选已有模型，或直接在本页新建。发布业务流程前请先部署审批模型。
            </small>
          </label>

          <label class="config-field">
            <span>审批标题</span>
            <TemplateVariableEditor
              :model-value="localConfig.titleTemplate || ''"
              :fields="fieldOptions"
              placeholder="点击下方字段插入，例如 ${name} 的审批"
              @update:model-value="patchConfig({ titleTemplate: $event })"
            />
            <small>待办标题按当前记录字段自动替换，不需要手写表达式。</small>
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
                :type="(selectedFlowModel.deployed || selectedFlowModel.deploymentId) ? 'success' : 'warning'"
                :bordered="false"
              >
                {{ (selectedFlowModel.deployed || selectedFlowModel.deploymentId) ? '已部署' : '草稿，可在本页设计后部署' }}
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

    <div v-if="flowDesignerVisible" class="flow-designer-fullscreen">
      <FlowDesignPage
        embedded
        :model-id="selectedFlowModelId"
        :business-object-code="objectCode"
        :business-object-name="objectName || objectCode"
        :business-form-key="localConfig.formAsset?.formKey || ''"
        :application-id="localConfig.formAsset?.applicationId || ''"
        @close="handleFlowDesignerClosed"
        @saved="handleFlowDesignerClosed"
        @deployed="handleFlowDesignerClosed"
      />
    </div>
  </div>
</template>

<style scoped>
.execution-node-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  border: 1px solid rgba(245, 158, 11, 0.32);
  border-radius: 7px;
  background: rgba(245, 158, 11, 0.07);
  padding: 11px 12px;
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
  gap: 16px;
  grid-template-columns: minmax(0, 1fr);
}

.approval-config-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 7px;
  background: rgba(241, 245, 249, 0.58);
  padding: 12px;
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

.flow-designer-fullscreen {
  position: fixed;
  inset: 12px;
  z-index: 1000;
  overflow: hidden;
  border-radius: 10px;
  background: var(--card-color, #fff);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.18);
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
</style>
