<template>
  <view class="ai-page-shell">
    <view class="ai-page-shell__content" :class="{ 'ai-page-shell__content--safe': safeBottom }">
      <slot />
    </view>
  </view>
</template>

<script setup>
defineProps({
  grid: {
    type: Boolean,
    default: true
  },
  safeBottom: {
    type: Boolean,
    default: true
  }
})
</script>

<style lang="scss" scoped>
.ai-page-shell {
  position: relative;
  min-height: var(--forge-page-height, 100vh);
  overflow: hidden;
  box-sizing: border-box;
  background: var(--page-bg);
}

.ai-page-shell__content {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
  min-height: var(--forge-page-height, 100vh);
}

.ai-page-shell__content--safe {
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
