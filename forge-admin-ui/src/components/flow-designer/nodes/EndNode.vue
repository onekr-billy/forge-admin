<script setup>
/**
 * EndNode — 结束节点卡片
 * 配色：灰色代表流程结束
 * 副标题：endType（正常结束 / 强制终止）
 */
import { computed } from 'vue'
import NodeCard from './NodeCard.vue'

const props = defineProps({
  node: { type: Object, required: true },
  selected: Boolean,
  status: { type: String, default: null },
  readonly: Boolean,
})

defineEmits(['click', 'delete', 'contextMenu'])

const subtitle = computed(() => {
  return props.node?.config?.endType === 'terminate' ? '强制终止流程' : '正常结束'
})
</script>

<template>
  <NodeCard
    :node="node"
    :selected="selected"
    :status="status"
    :readonly="readonly"
    icon="i-lucide:square"
    color-var="gray"
    :subtitle="subtitle"
    :deletable="false"
    @click="$emit('click', $event)"
    @context-menu="$emit('contextMenu', $event)"
  />
</template>
