<template>
  <view
    class="ai-tag"
    :class="[
      `ai-tag--${normalizedType}`,
      `ai-tag--${variant}`,
      `ai-tag--${normalizedSize}`,
      { 'ai-tag--round': round }
    ]"
    :style="customStyle"
  >
    <text class="ai-tag__text"><slot /></text>
    <button v-if="closable" class="ai-tag__close" type="button" @click.stop="emit('close')">
      <text>×</text>
    </button>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'default'
  },
  variant: {
    type: String,
    default: 'soft',
    validator: value => ['solid', 'soft', 'outline'].includes(value)
  },
  size: {
    type: String,
    default: 'md'
  },
  color: {
    type: String,
    default: ''
  },
  closable: {
    type: Boolean,
    default: false
  },
  round: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close'])

const normalizedType = computed(() => {
  if (props.type === 'error') return 'danger'
  if (['primary', 'success', 'warning', 'danger', 'default'].includes(props.type)) return props.type
  return 'default'
})

const normalizedSize = computed(() => {
  const sizeMap = {
    small: 'sm',
    medium: 'md',
    large: 'lg'
  }
  return sizeMap[props.size] || props.size
})

const customStyle = computed(() => {
  if (!props.color) return {}
  return props.variant === 'solid'
    ? {
        backgroundColor: props.color,
        borderColor: props.color,
        color: '#fff'
      }
    : {
        borderColor: props.color,
        color: props.color
      }
})
</script>

<style lang="scss" scoped>
.ai-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  max-width: 100%;
  border: 1px solid transparent;
  border-radius: 8rpx;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1;
  transition: opacity 0.2s ease, transform 0.2s ease;

  &--round {
    border-radius: 8rpx;
  }

  &--sm {
    gap: 8rpx;
    min-height: 36rpx;
    padding: 4rpx 12rpx;
    font-size: 22rpx;
  }

  &--md {
    gap: 10rpx;
    padding: 8rpx 18rpx;
    font-size: 24rpx;
  }

  &--lg {
    gap: 12rpx;
    padding: 12rpx 26rpx;
    font-size: 28rpx;
  }

  &--primary {
    &.ai-tag--solid {
      color: #fff;
      background: var(--primary-color);
    }
    &.ai-tag--soft {
      color: var(--primary-color);
      border-color: transparent;
      background: var(--primary-soft);
    }
    &.ai-tag--outline {
      color: var(--primary-color);
      border-color: var(--forge-color-primary-border);
      background: transparent;
    }
  }

  &--success {
    &.ai-tag--solid {
      color: #fff;
      background: #10b981;
    }
    &.ai-tag--soft {
      color: #10b981;
      border-color: transparent;
      background: #e8f8ed;
    }
    &.ai-tag--outline {
      color: #10b981;
      border-color: rgba(110, 231, 183, 0.9);
      background: transparent;
    }
  }

  &--warning {
    &.ai-tag--solid {
      color: #fff;
      background: #f97316;
    }
    &.ai-tag--soft {
      color: #f97316;
      border-color: rgba(253, 230, 138, 0.9);
      background: rgba(254, 243, 199, 0.82);
    }
    &.ai-tag--outline {
      color: #f97316;
      border-color: rgba(252, 211, 77, 0.9);
      background: transparent;
    }
  }

  &--danger {
    &.ai-tag--solid {
      color: #fff;
      background: #ef4444;
    }
    &.ai-tag--soft {
      color: #ef4444;
      border-color: rgba(254, 205, 211, 0.9);
      background: rgba(255, 228, 230, 0.82);
    }
    &.ai-tag--outline {
      color: #ef4444;
      border-color: rgba(253, 164, 175, 0.9);
      background: transparent;
    }
  }

  &--default {
    &.ai-tag--solid {
      color: #fff;
      background: #1e293b;
    }
    &.ai-tag--soft {
      color: #475569;
      border-color: rgba(226, 232, 240, 0.9);
      background: rgba(241, 245, 249, 0.82);
    }
    &.ai-tag--outline {
      color: #475569;
      border-color: rgba(203, 213, 225, 0.9);
      background: transparent;
    }
  }
}

.ai-tag__text {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-tag__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28rpx;
  height: 28rpx;
  margin: 0 -4rpx 0 0;
  padding: 0;
  border: 0;
  border-radius: 999rpx;
  background: transparent;
  color: currentColor;
  font-size: 26rpx;
  font-weight: 500;
  line-height: 1;

  &::after {
    border: 0;
  }
}
</style>
