<script setup>
import { NModal } from 'naive-ui'
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { ACTION_NODE_TEMPLATES, createActionTemplateConfig } from './node-templates.js'

const props = defineProps({
  node: { type: Object, required: true },
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

const emit = defineEmits(['update:config', 'openFlowDesigner', 'refreshFlowModel', 'editAction'])

const FlowDesignPage = defineAsyncComponent(() => import('@/views/flow/design.vue'))

const localConfig = ref(clone(props.node.config))
const flowDesignerVisible = ref(false)
const selectedTemplate = ref('')

const publishedFlowModels = computed(() => props.flowModels.filter((item) => {
  const published = item.status === 1 || item.published === true
  const deployed = item.deployed === true || Boolean(item.deploymentId)
  return published && deployed
}))

const selectedFlowModel = computed(() => publishedFlowModels.value.find(item =>
  modelKey(item) === localConfig.value.flowModelKey,
) || null)

const selectedFlowModelId = computed(() => {
  const item = selectedFlowModel.value
  return stringValue(item?.modelId || item?.id)
})

const fieldOptions = computed(() => props.fields.map(item => ({
  label: item.fieldName || item.fieldLabel || item.label || item.fieldCode || item.code,
  value: item.fieldCode || item.code || item.value,
})).filter(item => item.value))

watch(() => props.node, (value) => {
  localConfig.value = clone(value?.config)
  selectedTemplate.value = ''
}, { deep: true })

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
  if (['UPDATE_RECORD', 'CREATE_RECORD'].includes(actionType))
    keep.objectCode = props.objectCode
  localConfig.value = keep
  emit('update:config', clone(localConfig.value))
}

function handleFlowModel(event) {
  const key = event.target.value
  const item = publishedFlowModels.value.find(model => modelKey(model) === key)
  patchConfig({
    flowModelKey: key,
    flowModelName: item?.modelName || item?.name || key,
    versionPolicy: 'PINNED_AT_APPLICATION_PUBLISH',
  })
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

function handleFlowDesignerClosed() {
  flowDesignerVisible.value = false
  emit('refreshFlowModel', modelKey(selectedFlowModel.value))
}

function updateSingleReference(key, event) {
  patchConfig({ [key]: event.target.value || null })
}

function handleFormAsset(event) {
  const formKey = event.target.value
  const item = props.formAssets.find(candidate => optionValue(candidate) === formKey)
  patchConfig({
    formAsset: formKey
      ? {
          formKey,
          formMode: item?.formMode || item?.type || undefined,
          providerKey: item?.providerKey || undefined,
        }
      : {},
  })
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
  return stringValue(item?.value || item?.code || item?.actionCode || item?.templateCode || item?.processCode)
}

function optionLabel(item) {
  return item?.label || item?.name || item?.actionName || item?.templateName || item?.processName || optionValue(item)
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
          <select :value="localConfig.objectCode || objectCode" @change="updateSingleReference('objectCode', $event)">
            <option :value="objectCode">当前主对象</option>
            <option v-for="item in objects" :key="optionValue(item)" :value="optionValue(item)">
              {{ optionLabel(item) }}
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
      <label class="config-field">
        <span>审批流程</span>
        <select :value="localConfig.flowModelKey || ''" @change="handleFlowModel">
          <option value="">选择已发布并部署的审批模型</option>
          <option v-for="item in publishedFlowModels" :key="modelKey(item)" :value="modelKey(item)">
            {{ item.modelName || item.name || modelKey(item) }}
          </option>
        </select>
        <small v-if="!publishedFlowModels.length" class="catalog-empty-tip">
          当前租户暂无已发布并部署的审批流程。请先发布审批模型，再返回刷新。
        </small>
      </label>
      <label class="config-field">
        <span>审批标题</span>
        <input
          :value="localConfig.titleTemplate || ''"
          placeholder="例如：采购审批-{orderNo}"
          @input="patchConfig({ titleTemplate: $event.target.value })"
        >
      </label>
      <label class="config-field">
        <span>任务表单</span>
        <select :value="localConfig.formAsset?.formKey || ''" @change="handleFormAsset">
          <option value="">使用审批模型现有表单</option>
          <option v-for="item in formAssets" :key="optionValue(item)" :value="optionValue(item)">
            {{ optionLabel(item) }}
          </option>
        </select>
      </label>
      <div class="approval-boundary-card">
        <strong>审批内部配置</strong>
        <p>审批人、会签、驳回和字段权限在真实流程设计器中维护；业务画布只固定发布版本并处理四种结果。</p>
        <button
          type="button"
          class="open-flow-designer-button"
          :disabled="!selectedFlowModelId"
          @click="openFlowDesigner"
        >
          打开真实流程设计器
        </button>
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

    <NModal v-model:show="flowDesignerVisible" :mask-closable="false" :auto-focus="false">
      <section class="flow-designer-modal-shell">
        <FlowDesignPage
          v-if="flowDesignerVisible"
          embedded
          :model-id="selectedFlowModelId"
          :business-object-code="objectCode"
          :business-object-name="objectName || objectCode"
          @close="handleFlowDesignerClosed"
          @saved="handleFlowDesignerClosed"
          @deployed="handleFlowDesignerClosed"
        />
      </section>
    </NModal>
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
  min-height: 34px;
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
  grid-template-columns: 1fr 1fr auto;
}

.remove-row {
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

.flow-designer-modal-shell {
  width: min(1480px, 96vw);
  height: min(900px, 94vh);
  overflow: hidden;
  border-radius: 10px;
  background: var(--card-color, #fff);
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
