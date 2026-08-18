<script setup>
import { NAlert, NButton } from 'naive-ui'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  BUSINESS_PROCESS_NODE_DRAG_MIME,
  getBusinessProcessNodeDefinition,
  isBusinessProcessStartType,
} from './business-process-node-types.js'
import {
  businessProcessHashInput,
  validateBusinessProcessGraph,
} from './business-process-schema.js'
import BusinessProcessCanvas from './BusinessProcessCanvas.vue'
import BusinessProcessNodeConfigDrawer from './BusinessProcessNodeConfigDrawer.vue'
import { useBusinessProcessDesigner } from './useBusinessProcessDesigner.js'

const props = defineProps({
  schema: { type: Object, required: true },
  processName: { type: String, default: '业务流程' },
  readonly: { type: Boolean, default: false },
  autoSaveDelay: { type: Number, default: 900 },
  saveState: { type: String, default: 'idle' },
  saveError: { type: String, default: '' },
  serverValidation: { type: Object, default: null },
  objectName: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  objects: { type: Array, default: () => [] },
  flowModels: { type: Array, default: () => [] },
  formAssets: { type: Array, default: () => [] },
  businessActions: { type: Array, default: () => [] },
  messageTemplates: { type: Array, default: () => [] },
  capabilities: { type: Array, default: () => [] },
  subProcesses: { type: Array, default: () => [] },
  serviceActors: { type: Array, default: () => [] },
})

const emit = defineEmits([
  'update:schema',
  'save',
  'validate',
  'openFlowDesigner',
  'refreshFlowModel',
  'editAction',
  'dirtyChange',
  'locateIssue',
  'reload',
])

const designer = useBusinessProcessDesigner(props.schema)
const drawerVisible = ref(false)
const operationError = ref('')
const issuesExpanded = ref(true)
const draggingNodeType = ref('')
let autoSaveTimer = null

const palette = ['CONDITION', 'ACTION', 'APPROVAL', 'SUB_PROCESS']
  .map(type => ({ type, ...getBusinessProcessNodeDefinition(type) }))

const selectedNode = computed(() => designer.getNode(designer.selectedNodeId.value))
const clientValidation = computed(() => validateBusinessProcessGraph(designer.schema.value))
const issues = computed(() => mergeIssues(
  clientValidation.value.issues,
  props.serverValidation?.issues || props.serverValidation?.errors || [],
))

const saveStatus = computed(() => {
  const states = {
    idle: { label: designer.isDirty.value ? '有未保存修改' : '草稿已同步', tone: 'idle' },
    saving: { label: '正在保存…', tone: 'saving' },
    saved: { label: '刚刚已保存', tone: 'saved' },
    conflict: { label: '草稿发生冲突', tone: 'conflict' },
    error: { label: '保存失败', tone: 'error' },
  }
  return states[props.saveState] || states.idle
})

const canEditSelected = computed(() => selectedNode.value && !props.readonly)
const canCopySelected = computed(() => canEditSelected.value
  && !isBusinessProcessStartType(selectedNode.value.type)
  && selectedNode.value.type !== 'END'
  && designer.getOutgoingEdges(selectedNode.value.id).length === 1)
const canDeleteSelected = computed(() => canEditSelected.value
  && !isBusinessProcessStartType(selectedNode.value.type)
  && selectedNode.value.type !== 'END')

const initialStart = designer.schema.value.nodes.find(node => isBusinessProcessStartType(node.type))
designer.selectNode(initialStart?.id)

watch(() => designer.schema.value, () => {
  emit('update:schema', designer.exportSchema())
  if (designer.isDirty.value && !props.readonly)
    scheduleAutoSave()
}, { deep: true })

watch(() => designer.isDirty.value, (value) => {
  emit('dirtyChange', value)
})

watch(() => props.schema, (value) => {
  if (businessProcessHashInput(value) === businessProcessHashInput(designer.schema.value))
    return
  designer.setSchema(value, { markSaved: true })
  designer.clearHistory()
}, { deep: true })

watch(() => props.saveState, (value, previous) => {
  if (value === 'saved' && previous !== 'saved') {
    designer.markSaved()
    clearAutoSave()
  }
})

function handleAddNode(type) {
  operationError.value = ''
  const insertionNode = selectedNode.value || initialStart
  try {
    const outgoing = designer.getOutgoingEdges(insertionNode.id)
    if (outgoing.length !== 1)
      throw new Error('当前节点有多个结果出口，请使用对应分支线上的 + 添加节点')
    handleInsertNode({ edgeId: outgoing[0].id, type })
  }
  catch (error) {
    operationError.value = error.message
  }
}

function handleInsertNode({ edgeId, type }) {
  operationError.value = ''
  try {
    const overrides = type === 'ACTION'
      ? { config: { objectCode: designer.schema.value.subject?.objectCode } }
      : {}
    const nodeId = designer.insertNodeOnEdge(edgeId, type, overrides)
    designer.selectNode(nodeId)
    drawerVisible.value = true
    draggingNodeType.value = ''
  }
  catch (error) {
    operationError.value = error.message
    draggingNodeType.value = ''
  }
}

function handlePaletteDragStart(event, type) {
  if (props.readonly) {
    event.preventDefault()
    return
  }
  draggingNodeType.value = type
  if (!event.dataTransfer)
    return
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData(BUSINESS_PROCESS_NODE_DRAG_MIME, type)
  event.dataTransfer.setData('text/plain', type)
}

function handlePaletteDragEnd() {
  draggingNodeType.value = ''
}

function handleNodeSelect(node) {
  designer.selectNode(node.id)
  drawerVisible.value = true
}

function handleCopyNode() {
  if (!canCopySelected.value)
    return
  operationError.value = ''
  try {
    const nodeId = designer.copyNode(selectedNode.value.id)
    designer.selectNode(nodeId)
  }
  catch (error) {
    operationError.value = error.message
  }
}

function handleDeleteNode(node = selectedNode.value) {
  if (!node || props.readonly || isBusinessProcessStartType(node.type) || node.type === 'END')
    return
  designer.selectNode(node.id)
  const dialog = window.$dialog
  if (!dialog) {
    performDeleteNode(node)
    return
  }
  dialog.warning({
    title: '删除节点',
    content: `确认删除“${node.name || '当前节点'}”吗？删除后会自动恢复前后节点连线。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => performDeleteNode(node),
  })
}

function performDeleteNode(node) {
  operationError.value = ''
  try {
    designer.deleteNode(node.id)
    drawerVisible.value = false
  }
  catch (error) {
    operationError.value = error.message
  }
}

function handleDrawerSave(node, metadata = {}) {
  const current = designer.getNode(node.id)
  if (!current)
    return
  operationError.value = ''
  try {
    if (isBusinessProcessStartType(current.type) && node.type !== current.type) {
      designer.changeStartType(node.id, node.type, {
        name: node.name,
        config: node.config,
        recordIdSource: metadata.recordIdSource,
      })
      return
    }
    designer.updateNode(node.id, {
      name: node.name,
      ports: node.ports,
      config: node.config,
    })
  }
  catch (error) {
    metadata.reject?.()
    operationError.value = error.message
    drawerVisible.value = true
  }
}

function handleValidate() {
  emit('validate', designer.exportSchema(), clientValidation.value)
}

function handleSave(reason = 'manual') {
  if (props.readonly || props.saveState === 'saving')
    return
  clearAutoSave()
  emit('save', designer.exportSchema(), {
    reason,
    hashInput: businessProcessHashInput(designer.schema.value),
    clientValidation: clientValidation.value,
  })
}

function scheduleAutoSave() {
  clearAutoSave()
  if (props.saveState === 'conflict')
    return
  const delay = Math.max(Number(props.autoSaveDelay) || 0, 0)
  autoSaveTimer = window.setTimeout(() => handleSave('auto'), delay)
}

function clearAutoSave() {
  if (autoSaveTimer != null) {
    window.clearTimeout(autoSaveTimer)
    autoSaveTimer = null
  }
}

function locateIssue(item) {
  if (item.nodeId)
    designer.selectNode(item.nodeId)
  emit('locateIssue', item)
}

function issueLocation(item) {
  const node = item?.nodeId ? designer.getNode(item.nodeId) : null
  return node ? `节点：${node.name || '未命名节点'}` : '流程结构'
}

function handleBeforeUnload(event) {
  if (!designer.isDirty.value)
    return
  event.preventDefault()
  event.returnValue = ''
}

function mergeIssues(...lists) {
  const result = []
  const keys = new Set()
  for (const item of lists.flat()) {
    const key = `${item.code || ''}\u0000${item.nodeId || ''}\u0000${item.path || ''}`
    if (keys.has(key))
      continue
    keys.add(key)
    result.push(item)
  }
  return result
}

onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
onBeforeUnmount(() => {
  clearAutoSave()
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

defineExpose({
  designer,
  markSaved: designer.markSaved,
  exportSchema: designer.exportSchema,
  hasUnsavedChanges: designer.isDirty,
})
</script>

<template>
  <section class="business-process-designer h-full min-h-150 flex flex-col overflow-hidden">
    <header class="designer-toolbar">
      <div class="designer-title-block">
        <span>业务流程</span>
        <strong>{{ processName }}</strong>
        <em>{{ schema.processCode }}</em>
      </div>
      <div class="toolbar-actions">
        <span class="save-state" :class="`is-${saveStatus.tone}`">{{ saveStatus.label }}</span>
        <NButton size="small" :disabled="!designer.canUndo.value || readonly" @click="designer.undo()">
          撤销
        </NButton>
        <NButton size="small" :disabled="!designer.canRedo.value || readonly" @click="designer.redo()">
          重做
        </NButton>
        <NButton size="small" :disabled="!canCopySelected" @click="handleCopyNode">
          复制节点
        </NButton>
        <NButton size="small" type="error" secondary :disabled="!canDeleteSelected" @click="handleDeleteNode()">
          删除节点
        </NButton>
        <NButton data-designer-action="validate" size="small" @click="handleValidate">
          检查流程
        </NButton>
        <NButton
          data-designer-action="save"
          size="small"
          type="primary"
          :loading="saveState === 'saving'"
          :disabled="readonly"
          @click="handleSave('manual')"
        >
          保存草稿
        </NButton>
      </div>
    </header>

    <NAlert v-if="saveState === 'conflict'" type="error" :bordered="false" class="conflict-alert">
      <div class="conflict-content">
        <span>草稿已被其他人更新。请刷新到最新版本后再继续编辑，当前修改不会自动覆盖远端草稿。</span>
        <NButton data-designer-action="reload" size="tiny" type="error" @click="emit('reload')">
          刷新草稿
        </NButton>
      </div>
    </NAlert>
    <NAlert v-else-if="saveState === 'error'" type="error" :bordered="false" class="conflict-alert">
      {{ saveError || '草稿保存失败，请检查网络后重试。' }}
    </NAlert>
    <NAlert v-if="operationError" type="warning" :bordered="false" closable @close="operationError = ''">
      {{ operationError }}
    </NAlert>

    <div class="designer-body min-h-0 flex flex-1">
      <aside class="node-palette">
        <div class="pane-heading">
          <strong>添加节点</strong>
          <span>拖到画布连线，或单击插入到选中节点后</span>
        </div>
        <button
          v-for="item in palette"
          :key="item.type"
          type="button"
          class="palette-item"
          :data-node-type="item.type"
          :disabled="readonly"
          :draggable="!readonly"
          @dragstart="handlePaletteDragStart($event, item.type)"
          @dragend="handlePaletteDragEnd"
          @click="handleAddNode(item.type)"
        >
          <span :class="`tone-${item.tone}`" />
          <strong>{{ item.label }}</strong>
          <small>{{ item.type === 'APPROVAL' ? '等待 Flowable 审批结果' : '业务编排节点' }}</small>
        </button>
        <div class="palette-boundary">
          审批节点只引用已发布模型；会签、驳回、退回和字段权限仍在真实流程设计器中配置。
        </div>
      </aside>

      <main class="canvas-pane min-w-0 flex-1">
        <BusinessProcessCanvas
          :schema="designer.schema.value"
          :selected-node-id="designer.selectedNodeId.value"
          :readonly="readonly"
          :palette="palette"
          :dragging-node-type="draggingNodeType"
          @node-select="handleNodeSelect"
          @node-delete="handleDeleteNode"
          @insert-node="handleInsertNode"
        />
      </main>

      <aside class="issue-pane" :class="{ 'is-collapsed': !issuesExpanded }">
        <button type="button" class="issue-pane-head" @click="issuesExpanded = !issuesExpanded">
          <span>
            <strong>流程问题</strong>
            <em>{{ issues.length }}</em>
          </span>
          <span>{{ issuesExpanded ? '收起' : '展开' }}</span>
        </button>
        <div v-if="issuesExpanded" class="issue-list">
          <button
            v-for="(item, index) in issues"
            :key="`${item.code}-${item.nodeId}-${index}`"
            type="button"
            class="issue-item"
            @click="locateIssue(item)"
          >
            <strong>{{ item.message }}</strong>
            <span>{{ issueLocation(item) }}</span>
          </button>
          <div v-if="!issues.length" class="issue-empty">
            当前图结构完整；发布前仍需执行服务端依赖与权限校验。
          </div>
        </div>
      </aside>
    </div>

    <BusinessProcessNodeConfigDrawer
      :visible="drawerVisible"
      :node="selectedNode"
      :readonly="readonly"
      :object-code="designer.schema.value.subject?.objectCode"
      :object-name="objectName"
      :fields="fields"
      :objects="objects"
      :flow-models="flowModels"
      :form-assets="formAssets"
      :business-actions="businessActions"
      :message-templates="messageTemplates"
      :capabilities="capabilities"
      :sub-processes="subProcesses"
      :service-actors="serviceActors"
      @update:visible="drawerVisible = $event"
      @save="handleDrawerSave"
      @open-flow-designer="emit('openFlowDesigner', $event)"
      @refresh-flow-model="emit('refreshFlowModel', $event)"
      @edit-action="emit('editAction', $event)"
    />
  </section>
</template>

<style scoped>
.business-process-designer {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 8px;
  background: var(--card-color, #fff);
}

.designer-toolbar {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.25);
  padding: 8px 14px;
}

.designer-title-block {
  display: grid;
  min-width: 0;
  align-items: baseline;
  column-gap: 8px;
  grid-template-columns: auto minmax(0, 1fr);
}

.designer-title-block > span {
  color: var(--text-color-3, #64748b);
  font-size: 11px;
  grid-column: 1 / -1;
}

.designer-title-block strong {
  overflow: hidden;
  color: var(--text-color-1, #0f172a);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.designer-title-block em {
  color: var(--text-color-3, #64748b);
  font-size: 11px;
  font-style: normal;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
}

.save-state {
  margin-right: 4px;
  color: var(--text-color-3, #64748b);
  font-size: 12px;
}

.save-state.is-saved {
  color: var(--success-color, #15803d);
}

.save-state.is-conflict,
.save-state.is-error {
  color: var(--error-color, #dc2626);
}

.save-state.is-saving {
  color: var(--primary-color, #2563eb);
}

.conflict-alert {
  border-bottom: 1px solid rgba(239, 68, 68, 0.16);
}

.conflict-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.designer-body {
  background: var(--body-color, #f7f9fa);
}

.node-palette {
  width: 218px;
  flex: 0 0 218px;
  overflow-y: auto;
  border-right: 1px solid rgba(148, 163, 184, 0.25);
  background: var(--card-color, #fff);
  padding: 14px 12px;
}

.pane-heading {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 0 4px 12px;
}

.pane-heading strong {
  color: var(--text-color-1, #0f172a);
  font-size: 13px;
}

.pane-heading span {
  color: var(--text-color-3, #64748b);
  font-size: 11px;
}

.palette-item {
  position: relative;
  display: grid;
  width: 100%;
  align-items: center;
  gap: 2px 9px;
  margin-bottom: 7px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 7px;
  background: var(--card-color, #fff);
  padding: 9px 10px;
  text-align: left;
  grid-template-columns: 4px 1fr;
  cursor: grab;
}

.palette-item:active:not(:disabled) {
  cursor: grabbing;
}

.palette-item[draggable='true']::after {
  position: absolute;
  top: 50%;
  right: 9px;
  color: var(--text-color-3, #94a3b8);
  content: '⋮⋮';
  font-size: 11px;
  letter-spacing: -3px;
  transform: translateY(-50%);
}

.palette-item:hover:not(:disabled) {
  border-color: rgba(37, 99, 235, 0.42);
  background: rgba(37, 99, 235, 0.035);
}

.palette-item > span {
  width: 4px;
  height: 29px;
  border-radius: 3px;
  background: #64748b;
  grid-row: 1 / 3;
}

.palette-item .tone-condition {
  background: #c17a16;
}

.palette-item .tone-action,
.palette-item .tone-sub-process {
  background: #2563eb;
}

.palette-item .tone-approval {
  background: #7c3aed;
}

.palette-item strong {
  color: var(--text-color-1, #0f172a);
  font-size: 12px;
}

.palette-item small {
  color: var(--text-color-3, #64748b);
  font-size: 10px;
}

.palette-boundary {
  margin-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  padding: 12px 4px 0;
  color: var(--text-color-3, #64748b);
  font-size: 11px;
  line-height: 1.6;
}

.canvas-pane {
  position: relative;
}

.issue-pane {
  width: 250px;
  flex: 0 0 250px;
  overflow: hidden;
  border-left: 1px solid rgba(148, 163, 184, 0.25);
  background: var(--card-color, #fff);
  transition:
    width 160ms ease,
    flex-basis 160ms ease;
}

.issue-pane.is-collapsed {
  width: 48px;
  flex-basis: 48px;
}

.issue-pane-head {
  display: flex;
  width: 100%;
  height: 46px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
  padding: 0 12px;
  color: var(--text-color-3, #64748b);
  font-size: 11px;
}

.issue-pane-head > span:first-child {
  display: flex;
  align-items: center;
  gap: 7px;
}

.issue-pane-head strong {
  color: var(--text-color-1, #0f172a);
  font-size: 12px;
}

.issue-pane-head em {
  min-width: 20px;
  border-radius: 999px;
  background: rgba(239, 68, 68, 0.09);
  padding: 1px 6px;
  color: var(--error-color, #dc2626);
  font-style: normal;
}

.issue-pane.is-collapsed .issue-pane-head {
  height: 100%;
  justify-content: center;
  padding: 12px 0;
  writing-mode: vertical-rl;
}

.issue-list {
  max-height: calc(100% - 46px);
  overflow-y: auto;
  padding: 10px;
}

.issue-item {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 7px;
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 6px;
  background: rgba(254, 242, 242, 0.5);
  padding: 8px 9px;
  text-align: left;
}

.issue-item strong {
  color: var(--text-color-1, #0f172a);
  font-size: 12px;
  line-height: 1.45;
}

.issue-item span {
  color: var(--text-color-3, #64748b);
  font-size: 10px;
}

.issue-empty {
  padding: 16px 4px;
  color: var(--text-color-3, #64748b);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .issue-pane {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .issue-pane {
    transition: none;
  }
}
</style>
