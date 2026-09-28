<template>
  <wd-button
    class="ai-button"
    :class="[`ai-button--${variant}`, `ai-button--${size}`]"
    :type="wotType"
    :size="wotSize"
    :plain="plain"
    :round="false"
    :block="block"
    :loading="loading"
    :disabled="disabled || loading"
    @click="handleClick"
  >
    <view class="ai-button__content">
      <view v-if="!loading && $slots.leftIcon" class="ai-button__icon"><slot name="leftIcon" /></view>
      <text class="ai-button__text"><slot /></text>
      <view v-if="!loading && $slots.rightIcon" class="ai-button__icon"><slot name="rightIcon" /></view>
    </view>
  </wd-button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: value => ['primary', 'secondary', 'outline', 'ghost', 'danger'].includes(value),
  },
  size: {
    type: String,
    default: 'md',
    validator: value => ['sm', 'md', 'lg'].includes(value),
  },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  block: { type: Boolean, default: false },
})

const emit = defineEmits(['click'])
const wotType = computed(() => ({
  primary: 'primary',
  secondary: 'default',
  outline: 'info',
  ghost: 'text',
  danger: 'error',
})[props.variant] || 'primary')
const wotSize = computed(() => ({ sm: 'small', md: 'medium', lg: 'large' })[props.size] || 'medium')
const plain = computed(() => ['secondary', 'outline', 'danger'].includes(props.variant))

function handleClick(event) {
  if (!props.disabled && !props.loading) emit('click', event)
}
</script>

<style lang="scss" scoped>
.ai-button {
  min-width: 0;
  margin: 0;
  border-radius: var(--forge-radius-control, 12rpx) !important;
  font-weight: 700;
  box-shadow: none !important;
}

.ai-button--sm { min-height: 44px; }
.ai-button--md { min-height: 44px; }
.ai-button--lg { min-height: 48px; border-radius: 999px !important; }
.ai-button--secondary { color: var(--forge-color-text-secondary, #475569) !important; border-color: transparent !important; background: #f1f5f9 !important; }
.ai-button--outline { color: var(--forge-color-primary, #3b82f6) !important; border-color: var(--forge-color-primary, #3b82f6) !important; background: #fff !important; }
.ai-button--ghost { color: var(--forge-color-primary, #3b82f6) !important; background: transparent !important; }
.ai-button--danger { color: var(--forge-color-danger, #ef4444) !important; border-color: rgba(239, 68, 68, .28) !important; background: #fff !important; }
.ai-button__content { display: flex; min-width: 0; min-height: inherit; align-items: center; justify-content: center; gap: 6px; }
.ai-button__text { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-button__icon { display: flex; flex: 0 0 auto; align-items: center; justify-content: center; }

@media (hover: hover) {
  .ai-button--secondary:hover,
  .ai-button--outline:hover { background: var(--forge-surface-muted, #f8fafc) !important; }
  .ai-button--ghost:hover { opacity: .82; }
}
</style>
