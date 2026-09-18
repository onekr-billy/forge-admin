<script setup>
import { computed } from 'vue'
import {
  getBusinessProcessNodeDefinition,
  getBusinessProcessPortLabel,
  isBusinessProcessStartType,
} from './business-process-node-types.js'

const props = defineProps({
  node: { type: Object, required: true },
  position: { type: Object, default: null },
  selected: { type: Boolean, default: false },
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'delete'])

const definition = computed(() => getBusinessProcessNodeDefinition(props.node.type) || ({
  label: props.node.type || '未知节点',
  tone: 'unknown',
  ports: [],
}))

const style = computed(() => {
  if (!props.position)
    return { display: 'none' }
  return {
    left: `${props.position.x}px`,
    top: `${props.position.y}px`,
    width: `${props.position.width}px`,
    minHeight: `${props.position.height}px`,
  }
})

const summary = computed(() => {
  if (props.node.type === 'APPROVAL')
    return props.node.config?.flowModelName || props.node.config?.flowModelKey || '选择已发布审批流程'
  if (props.node.type === 'ACTION')
    return actionLabel(props.node.config?.actionType)
  if (props.node.type === 'CONDITION')
    return `${props.node.config?.branches?.length || props.node.ports?.length || 0} 个结果分支`
  if (props.node.type === 'SUB_PROCESS')
    return props.node.config?.processCode || '选择当前应用已发布流程'
  return definition.value.label
})

const visiblePorts = computed(() => {
  if (props.node.type === 'CONDITION' || props.node.type === 'APPROVAL')
    return props.node.ports || []
  return []
})

const portLabels = computed(() => visiblePorts.value.map((port, index) => ({
  port,
  label: getBusinessProcessPortLabel(props.node, port, index),
})))

const deletable = computed(() => !props.readonly
  && !isBusinessProcessStartType(props.node.type)
  && props.node.type !== 'END')

function selectNode() {
  if (!props.readonly)
    emit('select', props.node)
}

function deleteNode() {
  emit('delete', props.node)
}

function actionLabel(actionType) {
  const labels = {
    UPDATE_RECORD: '更新记录',
    CREATE_RECORD: '创建记录',
    BUSINESS_ACTION: '执行业务动作',
    EXECUTE_BUSINESS_ACTION: '执行业务动作',
    DOMAIN_ACTION: '执行领域动作',
    SEND_MESSAGE: '发送消息',
    INVOKE_CAPABILITY: '调用受治理能力',
  }
  return labels[actionType] || '配置受控动作'
}
</script>

<template>
  <div
    class="business-process-node absolute z-2 flex items-stretch overflow-visible text-left"
    :class="[
      `is-${definition.tone}`,
      { 'is-selected': selected },
    ]"
    :style="style"
    :data-node-id="node.id"
    data-business-process-node
    role="button"
    :tabindex="readonly ? -1 : 0"
    :aria-pressed="selected"
    :aria-disabled="readonly"
    @click.stop="selectNode"
    @keydown.enter.prevent="selectNode"
    @keydown.space.prevent="selectNode"
  >
    <button
      v-if="deletable"
      type="button"
      class="node-delete-button"
      :aria-label="`删除节点：${node.name || definition.label}`"
      title="删除节点"
      data-business-node-delete
      @click.stop="deleteNode"
    >
      <span aria-hidden="true">×</span>
    </button>

    <span class="node-shell min-w-0 flex flex-1 items-stretch overflow-hidden">
      <span class="node-rail" aria-hidden="true" />
      <span class="min-w-0 flex flex-col flex-1 justify-center px-4 py-3">
        <span class="node-kicker">{{ definition.label }}</span>
        <span class="node-title mt-1 truncate">{{ node.name || definition.label }}</span>
        <span class="node-summary mt-1 truncate">{{ summary }}</span>
      </span>
      <span class="node-status flex items-center pr-4" aria-hidden="true">
        <span class="node-status-dot" />
      </span>
    </span>

    <span v-if="portLabels.length" class="node-ports" aria-label="节点结果出口">
      <span
        v-for="item in portLabels"
        :key="item.port"
        class="node-port"
        data-business-port
      >
        {{ item.label }}
      </span>
    </span>
  </div>
</template>

<style scoped>
.business-process-node {
  border: 1px solid rgba(100, 116, 139, 0.22);
  border-radius: 10px;
  background: var(--card-color, #fff);
  color: var(--text-color-base, #0f172a);
  box-shadow:
    0 1px 3px rgba(15, 23, 42, 0.06),
    0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease,
    transform 150ms ease;
  cursor: pointer;
}

.business-process-node:hover:not([aria-disabled='true']) {
  border-color: rgba(37, 99, 235, 0.36);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.business-process-node.is-selected {
  border-color: var(--primary-color, #2563eb);
  box-shadow:
    0 0 0 2px rgba(37, 99, 235, 0.15),
    0 4px 12px rgba(15, 23, 42, 0.08);
}

.business-process-node[aria-disabled='true'] {
  cursor: default;
}

.node-delete-button {
  position: absolute;
  z-index: 4;
  top: -10px;
  right: -10px;
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--card-color, #fff);
  border-radius: 50%;
  background: var(--error-color, #dc2626);
  color: #fff;
  cursor: pointer;
  font-size: 20px;
  font-weight: 500;
  line-height: 1;
  opacity: 0.8;
  box-shadow: 0 3px 9px rgba(153, 27, 27, 0.22);
  transition:
    opacity 120ms ease,
    transform 120ms ease;
}

.business-process-node:hover .node-delete-button,
.business-process-node.is-selected .node-delete-button,
.node-delete-button:focus-visible {
  opacity: 1;
  transform: scale(1.06);
}

.node-shell {
  border-radius: inherit;
}

.node-rail {
  width: 4px;
  flex: 0 0 4px;
  background: #94a3b8;
}

.is-manual .node-rail,
.is-event .node-rail,
.is-schedule .node-rail {
  background: linear-gradient(180deg, #14b8a6, #0f766e);
}

.is-condition .node-rail {
  background: linear-gradient(180deg, #f59e0b, #d97706);
}

.is-action .node-rail {
  background: linear-gradient(180deg, #3b82f6, #2563eb);
}

.is-sub-process .node-rail {
  background: linear-gradient(180deg, #6366f1, #4f46e5);
}

.is-approval .node-rail {
  background: linear-gradient(180deg, #8b5cf6, #7c3aed);
}

.is-end .node-rail {
  background: linear-gradient(180deg, #64748b, #475569);
}

.node-kicker {
  color: var(--text-color-3, #64748b);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.node-title {
  color: var(--text-color-1, #0f172a);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.35;
}

.node-summary {
  color: var(--text-color-3, #64748b);
  font-size: 12px;
  line-height: 1.35;
}

.node-status-dot {
  width: 7px;
  height: 7px;
  border: 2px solid rgba(100, 116, 139, 0.55);
  border-radius: 999px;
}

.business-process-node.is-selected .node-status-dot {
  border-color: var(--primary-color, #2563eb);
  background: var(--primary-color, #2563eb);
}

.node-ports {
  position: absolute;
  top: calc(100% + 7px);
  left: 0;
  display: grid;
  width: 100%;
  grid-auto-columns: minmax(0, 1fr);
  grid-auto-flow: column;
  gap: 4px;
  padding: 0 4px;
}

.node-port {
  overflow: hidden;
  min-width: 0;
  padding: 2px 5px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 4px;
  background: var(--card-color, #fff);
  color: var(--text-color-3, #64748b);
  font-size: 9px;
  line-height: 1.2;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .business-process-node {
    transition: none;
  }
}
</style>
