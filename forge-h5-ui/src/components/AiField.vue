<template>
  <view class="ai-field" :class="[`ai-field--${layout}`]">
    <text v-if="label" class="ai-field__label">{{ label }}</text>
    <view class="ai-field__content">
      <view
        class="ai-field__control"
        :class="{
          'ai-field__control--error': !!error,
          'ai-field__control--disabled': disabled,
          'ai-field__control--focused': focused
        }"
      >
        <view v-if="$slots.leftIcon" class="ai-field__icon ai-field__icon--left">
          <slot name="leftIcon" />
        </view>

        <input
          class="ai-field__input"
          :type="inputType"
          :value="modelValue"
          :password="type === 'password' && !showPassword"
          :placeholder="placeholder"
          :disabled="disabled"
          :maxlength="maxlength"
          placeholder-class="ai-field__placeholder"
          @input="handleInput"
          @focus="focused = true"
          @blur="handleBlur"
          @confirm="$emit('confirm', $event)"
        />

        <view class="ai-field__actions">
          <button
            v-if="clearable && hasValue && !disabled"
            class="ai-field__action"
            type="button"
            @click="handleClear"
          >
            <text class="ai-field__clear">×</text>
          </button>
          <button
            v-if="type === 'password' && !disabled"
            class="ai-field__action ai-field__password"
            type="button"
            @click="showPassword = !showPassword"
          >
            <text>{{ showPassword ? '隐藏' : '显示' }}</text>
          </button>
          <view v-if="$slots.rightIcon" class="ai-field__icon ai-field__icon--right">
            <slot name="rightIcon" />
          </view>
        </view>
      </view>

      <text v-if="error" class="ai-field__error">{{ error }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  label: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'text'
  },
  clearable: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  layout: {
    type: String,
    default: 'vertical',
    validator: value => ['horizontal', 'vertical'].includes(value)
  },
  maxlength: {
    type: [String, Number],
    default: 140
  }
})

const emit = defineEmits(['update:modelValue', 'input', 'clear', 'confirm', 'blur', 'focus'])

const focused = ref(false)
const showPassword = ref(false)

const hasValue = computed(() => String(props.modelValue ?? '').length > 0)
const inputType = computed(() => (props.type === 'password' ? 'text' : props.type))

const handleInput = (event) => {
  const value = event.detail.value
  emit('update:modelValue', value)
  emit('input', value)
}

const handleBlur = (event) => {
  focused.value = false
  emit('blur', event)
}

const handleClear = () => {
  emit('update:modelValue', '')
  emit('clear')
}
</script>

<style lang="scss" scoped>
.ai-field {
  display: flex;
  flex-direction: column;
  gap: 10rpx;

  &--horizontal {
    flex-direction: row;
    align-items: center;
    gap: 28rpx;

    .ai-field__label {
      width: 156rpx;
      padding: 0;
      flex-shrink: 0;
      font-size: 30rpx;
      text-transform: none;
    }

    .ai-field__content {
      flex: 1;
      min-width: 0;
    }
  }
}

.ai-field__label {
  padding-left: 4rpx;
  color: #475569;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1.2;
}

.ai-field__content {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.ai-field__control {
  display: flex;
  align-items: center;
  min-height: 80rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--border-color);
  border-radius: 12rpx;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;

  &--focused {
    border-color: var(--primary-color);
    background: #fff;
    box-shadow: 0 0 0 3rpx rgba(37, 99, 235, 0.12);
  }

  &--error {
    border-color: #ef4444;
    box-shadow: 0 0 0 3rpx rgba(239, 68, 68, 0.1);
  }

  &--disabled {
    opacity: 0.62;
    background: #f7f8fa;
  }
}

.ai-field__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #94a3b8;

  &--left {
    margin-right: 14rpx;
  }

  &--right {
    margin-left: 8rpx;
  }
}

.ai-field__input {
  flex: 1;
  min-width: 0;
  height: 78rpx;
  color: #334155;
  font-size: 27rpx;
  font-weight: 500;
}

:deep(.ai-field__placeholder) {
  color: #94a3b8;
  font-weight: 400;
}

.ai-field__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8rpx;
  margin-left: 12rpx;
}

.ai-field__action {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 44rpx;
  height: 44rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 999rpx;
  background: transparent;
  color: #64748b;
  font-size: 24rpx;
  line-height: 1;

  &::after {
    border: 0;
  }
}

.ai-field__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34rpx;
  height: 34rpx;
  border-radius: 999rpx;
  background: #cbd5e1;
  color: #ffffff;
  font-size: 30rpx;
  line-height: 30rpx;
}

.ai-field__password {
  width: auto;
  padding: 0 8rpx;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 700;
}

.ai-field__error {
  padding: 0 16rpx;
  color: #f43f5e;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.3;
}
</style>
