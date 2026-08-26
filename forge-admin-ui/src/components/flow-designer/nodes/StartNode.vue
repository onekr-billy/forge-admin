<script setup>
/**
 * StartNode — 发起人节点卡片
 * 配色：青绿色（teal）代表流程启动
 * 副标题：发起人来源
 */
import NodeCard from './NodeCard.vue'

defineProps({
  node: { type: Object, required: true },
  selected: Boolean,
  status: { type: String, default: null },
  readonly: Boolean,
})

defineEmits(['click', 'delete', 'contextMenu'])

const subtitle = '系统自动记录发起人'
</script>

<template>
  <NodeCard
    :node="node"
    :selected="selected"
    :status="status"
    :readonly="readonly"
    icon="i-lucide:play"
    color-var="success"
    :subtitle="subtitle"
    :deletable="false"
    @click="$emit('click', $event)"
    @context-menu="$emit('contextMenu', $event)"
  />
</template>
