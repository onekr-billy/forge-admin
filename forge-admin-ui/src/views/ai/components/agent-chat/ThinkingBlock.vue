<template>
  <NCollapse class="thinking-block" :expanded-names="expandedNames" @update:expanded-names="onUpdate">
    <NCollapseItem title="思考过程" name="thinking">
      <div class="thinking-content">
        {{ content }}
      </div>
    </NCollapseItem>
  </NCollapse>
</template>

<script setup>
import { NCollapse, NCollapseItem } from 'naive-ui'
import { ref, watch } from 'vue'

defineOptions({ name: 'ChatThinkingBlock' })

const props = defineProps({
  // 思考过程文本（流式增量由父组件累积后传入）
  content: {
    type: String,
    default: '',
  },
  // 是否正在流式生成：生成中默认展开思考过程，回答完毕自动收起
  streaming: {
    type: Boolean,
    default: false,
  },
})

// 生成中默认展开；历史消息（streaming=false）默认收起
const expandedNames = ref(props.streaming ? ['thinking'] : [])

// 流式状态翻转时联动：开始生成→展开，回答完毕→收起
watch(() => props.streaming, (val) => {
  expandedNames.value = val ? ['thinking'] : []
})

// 允许用户在此期间手动展开/收起（最终仍以「回答完毕收起」为准）
function onUpdate(names) {
  expandedNames.value = names
}
</script>

<style scoped>
.thinking-block {
  margin-bottom: 8px;
  border-radius: 10px;
  overflow: hidden;
}

.thinking-block :deep(.n-collapse-item__header) {
  padding: 8px 12px;
  font-size: 12px;
}

.thinking-block :deep(.n-collapse-item__header-main) {
  color: var(--text-muted);
}

.thinking-block :deep(.n-collapse-item__content-inner) {
  padding-top: 0;
  padding-bottom: 8px;
}

.thinking-content {
  max-height: 160px;
  padding: 0 12px;
  overflow-y: auto;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
