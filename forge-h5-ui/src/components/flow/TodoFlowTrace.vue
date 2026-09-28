<template>
  <view class="trace-panel">
    <!-- 同一流程记录页中的分区标题 -->
    <view class="trace-heading">
      <text class="trace-heading__title">{{ mode === 'history' ? '办理记录' : '节点进度' }}</text>
      <text v-if="!loading && items.length" class="trace-heading__count">{{ items.length }} 项</text>
    </view>
    <AiListSkeleton v-if="loading" :rows="4" compact />
    <view v-else-if="items.length" class="trace-list">
      <view
        v-for="(item, index) in items"
        :key="itemKey(item, index)"
        class="trace-item"
        :class="`is-${mode === 'process' ? flowStatusTone(item.status) : actionTone(item.action || item.status)}`"
      >
        <view class="trace-dot" />
        <view class="trace-copy">
          <view class="trace-title-row">
            <text class="trace-title">{{ itemTitle(item) }}</text>
            <text class="trace-state" :class="mode === 'history' ? actionTone(item.action) : flowStatusTone(item.status)">
              {{ mode === 'history' ? formatFlowAction(item.action || item.status) : formatFlowStatus(item.status || item.statusText) }}
            </text>
          </view>
          <text v-if="itemMeta(item)" class="trace-meta">{{ itemMeta(item) }}</text>
          <text v-if="mode === 'history' && item.comment" class="trace-comment">{{ item.comment }}</text>
        </view>
      </view>
    </view>
    <view v-else class="trace-empty">{{ mode === 'history' ? '暂无办理记录' : '暂无可展示的流程节点' }}</view>
  </view>
</template>

<script setup>
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import { flowStatusTone, formatFlowAction, formatFlowDateTime, formatFlowStatus } from '@/utils/flow-display'

defineProps({
  mode: { type: String, default: 'history' },
  loading: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
})

function itemKey(item, index) { return item.id || item.taskId || item.nodeId || `${item.activityName || item.taskName || 'node'}:${index}` }
function itemTitle(item) { return item.activityName || item.taskName || item.nodeName || item.name || '流程节点' }
function actionTone(action) {
  const value = String(action || '').toLowerCase()
  if (['approve', 'approved', 'pass', 'passed', 'start'].includes(value)) return 'completed'
  if (['reject', 'rejected', 'terminate', 'terminated'].includes(value)) return 'exception'
  return 'pending'
}
function itemMeta(item) {
  const actor = Array.isArray(item.assigneeNames) && item.assigneeNames.length
    ? item.assigneeNames.join('、')
    : item.assigneeName || item.userName || item.operatorName
  const time = item.completeTime || item.endTime || item.createTime || item.startTime
  return [actor, time ? formatFlowDateTime(time) : ''].filter(Boolean).join(' · ')
}
</script>

<style lang="scss" scoped>
.trace-panel { margin-top: 14rpx; padding: 18px; border: 1px solid var(--border-light); border-radius: var(--radius-card); background: #fff}
.trace-heading, .trace-title-row { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12rpx; }
.trace-heading { margin-bottom: 10px; padding-bottom: 12px; border-bottom: 1rpx solid var(--border-light); }
.trace-heading__title { color: var(--text-strong); font-size: 14px; font-weight: 700; }
.trace-heading__count { color: var(--text-muted); font-size: 21rpx; }
.trace-list { padding: 4rpx 0; }
.trace-item { position: relative; display: flex; gap: 12px; padding-bottom: 18px; }
.trace-item:not(:last-child)::before { position: absolute; top: 12px; bottom: 0; left: 5px; width: 2px; background: #e2e8f0; content: ''; }
.trace-dot { position: relative; z-index: 1; width: 12px; height: 12px; flex: 0 0 12px; margin-top: 4px; border: 3px solid #cbd5e1; border-radius: 50%; background: #fff; box-sizing: border-box; }
.trace-item.is-running .trace-dot { border-color: var(--primary-color); background: var(--primary-color); }
.trace-item.is-completed .trace-dot { border-color: var(--forge-color-success); background: var(--forge-color-success); }
.trace-item.is-exception .trace-dot { border-color: var(--forge-color-danger); background: var(--forge-color-danger); }
.trace-copy { min-width: 0; flex: 1; }
.trace-title-row { align-items: flex-start; }
.trace-title { min-width: 0; flex: 1; color: var(--text-strong); font-size: 13px; font-weight: 600; }
.trace-state { flex: 0 0 auto; padding: 3px 8px; border-radius: 999px; color: var(--text-secondary); font-size: 10px; font-weight: 600; background: var(--surface-muted); }
.trace-state.running { color: var(--primary-color); background: var(--primary-soft); }
.trace-state.completed { color: var(--forge-color-success); background: #e8f7eb; }
.trace-state.exception { color: var(--forge-color-danger); background: #fff1f0; }
.trace-meta, .trace-comment { display: block; overflow-wrap: anywhere; margin-top: 5px; color: var(--text-muted); font-size: 11px; line-height: 1.5; }
.trace-comment { margin-top: 7px; padding: 8px 10px; border-radius: 10px; color: var(--text-secondary); font-size: 12px; background: #f8fafc; }
.trace-empty { padding: 52rpx 20rpx; color: var(--text-muted); font-size: 23rpx; text-align: center; }
</style>
