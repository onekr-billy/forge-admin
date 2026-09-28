<template>
  <view class="task-summary">
    <view class="task-summary__head">
      <view class="task-summary__title-wrap">
        <view class="task-summary__title-icon"><AiIcon icon="/static/icons/ai-icon/file-text.svg" color="#3b82f6" size="sm" /></view>
        <text class="task-title">{{ title }}</text>
      </view>
      <button class="task-summary__refresh" aria-label="刷新详情" @click.stop="emit('refresh')">
        <AiIcon icon="/static/icons/ai-icon/refresh-cw.svg" color="#3b82f6" size="sm" />
        <text>刷新详情</text>
      </button>
    </view>
    <text class="task-node">{{ task.taskName || task.name || '审批节点' }}</text>
    <view class="task-facts">
      <view class="task-fact"><view class="task-fact__label"><AiIcon icon="/static/icons/ai-icon/user.svg" color="#94a3b8" size="xs" /><text>申请人</text></view><text>{{ task.startUserName || task.createByName || '-' }}</text></view>
      <view class="task-fact"><view class="task-fact__label"><AiIcon icon="/static/icons/ai-icon/briefcase.svg" color="#94a3b8" size="xs" /><text>发起部门</text></view><text>{{ task.startDeptName || '-' }}</text></view>
      <view class="task-fact"><view class="task-fact__label"><AiIcon icon="/static/icons/ai-icon/folder.svg" color="#94a3b8" size="xs" /><text>流程分类</text></view><text>{{ task.categoryName || task.category || '-' }}</text></view>
      <view class="task-fact"><view class="task-fact__label"><AiIcon icon="/static/icons/ai-icon/clock.svg" color="#94a3b8" size="xs" /><text>提交时间</text></view><text>{{ formatFlowDateTime(task.createTime || task.startTime) }}</text></view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import AiIcon from '@/components/AiIcon.vue'
import { formatFlowDateTime } from '@/utils/flow-display'

const props = defineProps({ task: { type: Object, default: () => ({}) } })
const emit = defineEmits(['refresh'])
const title = computed(() => props.task.title || props.task.businessTitle || props.task.processName || props.task.processDefinitionName || props.task.taskName || '审批任务')
</script>

<style lang="scss" scoped>
.task-summary { position: relative; overflow: hidden; margin: 16px 16px 0; padding: 20px; border: 1px solid var(--border-light); border-radius: var(--radius-card); background: linear-gradient(135deg, #fff 0%, #f8fbff 100%); box-shadow: var(--shadow-soft); }
.task-summary::after { position: absolute; top: -48px; right: -40px; width: 132px; height: 132px; border-radius: 50%; background: rgba(59, 130, 246, .06); content: ''; pointer-events: none; }
.task-summary__head { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 12rpx; }
.task-summary__title-wrap, .task-fact__label { display: flex; min-width: 0; align-items: center; }
.task-summary__title-wrap { flex: 1; gap: 8px; }
.task-summary__title-icon { display: flex; width: 20px; height: 20px; flex: 0 0 20px; align-items: center; justify-content: center; }
.task-summary__title-icon :deep(.ai-icon) { width: 20px; height: 20px; }
.task-title, .task-node, .task-fact text { display: block; }
.task-title { position: relative; z-index: 1; min-width: 0; flex: 1; color: var(--text-strong); font-size: 18px; font-weight: 700; line-height: 1.4; }
.task-summary__refresh { position: relative; z-index: 1; display: flex; min-width: 44px; min-height: 36px; flex: 0 0 auto; align-items: center; justify-content: center; gap: 5px; margin: -3px -6px 0 0; padding: 0 10px; border: 0; border-radius: 999px; color: var(--primary-color); font-size: 12px; font-weight: 600; line-height: 1.3; white-space: nowrap; background: rgba(255, 255, 255, .82); }
.task-summary__refresh::after { border: 0; }
.task-node { position: relative; z-index: 1; width: fit-content; margin-top: 7px; padding: 4px 9px; border-radius: 999px; color: var(--primary-color); font-size: 11px; font-weight: 600; background: var(--primary-soft); }
.task-facts { position: relative; z-index: 1; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px 16px; margin-top: 16px; padding-top: 16px; border-top: 1rpx solid var(--border-light); }
.task-fact { min-width: 0; }
.task-fact__label { gap: 5px; color: var(--text-muted); }
.task-fact__label :deep(.ai-icon) { width: 12px; height: 12px; }
.task-fact__label text { color: var(--text-muted); font-size: 11px; }
.task-fact > text { overflow: hidden; margin-top: 3px; color: var(--text-secondary); font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }

@media (min-width: 1024px) {
  .task-summary {
    width: calc(100% - 48px);
    max-width: 1280px;
    margin: 24px auto 0;
    padding: 24px;
    box-sizing: border-box;
  }
}
</style>
