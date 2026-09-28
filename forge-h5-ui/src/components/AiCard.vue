<template>
  <wd-card class="ai-card" :title="title" type="rectangle" :style="{ borderRadius, border: border ? '' : '0' }">
    <view class="ai-card__body" :style="{ padding }"><slot /></view>
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </wd-card>
</template>

<script setup>
defineProps({
  borderRadius: { type: String, default: '20px' },
  border: { type: Boolean, default: true },
  title: { type: String, default: '' },
  padding: { type: String, default: '20px' },
})
</script>

<style lang="scss" scoped>
.ai-card { overflow: hidden; margin: 0; border: 1px solid var(--forge-color-border-subtle, #f1f5f9); background: var(--forge-color-surface, #fff); box-shadow: var(--shadow-soft); }
.ai-card__body { box-sizing: border-box; }
:deep(.wd-card__content) { padding: 0; }
:deep(.wd-card__title-content) { border-color: var(--forge-color-border-subtle, #e2e8f0); }
</style>
