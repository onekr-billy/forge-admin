<template>
  <view v-if="visible" class="ai-loading-overlay" :style="{ zIndex }" aria-live="polite">
    <view class="ai-loading-overlay__panel">
      <wd-loading type="ring" color="#1677ff" :size="24" />
      <text class="ai-loading-overlay__text">{{ text }}</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  text: { type: String, default: '处理中...' },
  zIndex: { type: Number, default: 10000 },
})
</script>

<style lang="scss" scoped>
.ai-loading-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(29, 33, 41, .22);
}

.ai-loading-overlay__panel {
  display: flex;
  min-width: 132px;
  min-height: 88px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 20px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  background: #fff;
  box-sizing: border-box;
}

.ai-loading-overlay__text {
  color: #1d2129;
  font-size: 14px;
  line-height: 1.5;
}
</style>
