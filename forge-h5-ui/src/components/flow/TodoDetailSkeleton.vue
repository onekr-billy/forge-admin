<template>
  <view class="todo-detail-skeleton" :class="{ 'is-form-only': formOnly }" aria-busy="true">
    <view v-if="!formOnly" class="detail-skeleton-card detail-skeleton-summary">
      <view class="detail-skeleton-head">
        <view class="detail-skeleton-icon" />
        <view class="detail-skeleton-line is-title" />
        <view class="detail-skeleton-pill" />
      </view>
      <view class="detail-skeleton-badge" />
      <view class="detail-skeleton-facts">
        <view v-for="index in 4" :key="`fact-${index}`" class="detail-skeleton-fact">
          <view class="detail-skeleton-line is-label" />
          <view class="detail-skeleton-line is-value" />
        </view>
      </view>
    </view>

    <view class="detail-skeleton-card detail-skeleton-form">
      <view class="detail-skeleton-section-head">
        <view class="detail-skeleton-icon" />
        <view class="detail-skeleton-copy">
          <view class="detail-skeleton-line is-section-title" />
          <view class="detail-skeleton-line is-section-desc" />
        </view>
      </view>
      <view v-for="index in 3" :key="`field-${index}`" class="detail-skeleton-field">
        <view class="detail-skeleton-line is-field-label" />
        <view class="detail-skeleton-control" />
      </view>
    </view>

    <view v-if="!formOnly" class="detail-skeleton-card detail-skeleton-flow">
      <view class="detail-skeleton-section-head">
        <view class="detail-skeleton-icon" />
        <view class="detail-skeleton-copy">
          <view class="detail-skeleton-line is-section-title" />
          <view class="detail-skeleton-line is-section-desc" />
        </view>
      </view>
      <view v-for="index in 3" :key="`node-${index}`" class="detail-skeleton-node">
        <view class="detail-skeleton-dot" />
        <view class="detail-skeleton-copy">
          <view class="detail-skeleton-line is-node-title" />
          <view class="detail-skeleton-line is-node-meta" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({ formOnly: { type: Boolean, default: false } })
</script>

<style lang="scss" scoped>
.todo-detail-skeleton { display: flex; flex-direction: column; gap: 14px; padding: 10px; }
.detail-skeleton-card { padding: 18px; border: 1px solid var(--border-light); border-radius: var(--radius-card); background: #fff; box-shadow: var(--shadow-soft); }
.detail-skeleton-head, .detail-skeleton-section-head, .detail-skeleton-field, .detail-skeleton-node { display: flex; min-width: 0; align-items: center; gap: 10px; }
.detail-skeleton-head { min-height: 36px; }
.detail-skeleton-icon { width: 20px; height: 20px; flex: 0 0 20px; border-radius: 6px; }
.detail-skeleton-pill { width: 72px; height: 32px; margin-left: auto; border-radius: 999px; }
.detail-skeleton-badge { width: 62px; height: 22px; margin-top: 8px; border-radius: 999px; }
.detail-skeleton-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 18px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border-light); }
.detail-skeleton-fact, .detail-skeleton-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 7px; }
.detail-skeleton-section-head { padding-bottom: 13px; border-bottom: 1px solid var(--border-light); }
.detail-skeleton-field { display: grid; grid-template-columns: 78px minmax(0, 1fr); margin-top: 12px; }
.detail-skeleton-control { height: 44px; border-radius: var(--radius-control); }
.detail-skeleton-node { align-items: flex-start; margin-top: 10px; }
.detail-skeleton-dot { width: 12px; height: 12px; flex: 0 0 12px; margin-top: 3px; border: 3px solid #dbe4ef; border-radius: 50%; background: #fff; box-sizing: border-box; }
.detail-skeleton-line { height: 10px; border-radius: 999px; }
.detail-skeleton-line.is-title { width: 42%; height: 16px; }
.detail-skeleton-line.is-label { width: 42%; height: 9px; }
.detail-skeleton-line.is-value { width: 72%; height: 12px; }
.detail-skeleton-line.is-section-title { width: 34%; height: 14px; }
.detail-skeleton-line.is-section-desc { width: 55%; height: 9px; }
.detail-skeleton-line.is-field-label { width: 52px; height: 11px; }
.detail-skeleton-line.is-node-title { width: 36%; height: 12px; }
.detail-skeleton-line.is-node-meta { width: 58%; height: 9px; }
.detail-skeleton-icon, .detail-skeleton-pill, .detail-skeleton-badge, .detail-skeleton-control, .detail-skeleton-line, .detail-skeleton-dot { background: #e7edf4; animation: detailSkeletonPulse 1.1s ease-in-out infinite alternate; }
.todo-detail-skeleton.is-form-only { gap: 0; padding: 0; }
.todo-detail-skeleton.is-form-only .detail-skeleton-card { padding: 0; border: 0; border-radius: 0; box-shadow: none; }
@keyframes detailSkeletonPulse { from { opacity: .56; } to { opacity: 1; } }
</style>
