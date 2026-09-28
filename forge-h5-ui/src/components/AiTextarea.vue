<template>
  <view
    class="ai-textarea"
    :class="{ 'is-error': !!error, 'is-disabled': disabled || readonly, 'is-focused': focused }"
    :style="{ minHeight }"
  >
    <wd-textarea
      class="ai-textarea__control"
      :model-value="modelValue"
      :placeholder="placeholder"
      :maxlength="Number(maxlength)"
      :disabled="disabled"
      :readonly="readonly"
      :focus="autofocus"
      :auto-height="autoHeight"
      :show-word-limit="showWordLimit"
      :clearable="clearable"
      :error="!!error"
      no-border
      @update:model-value="emit('update:modelValue', $event)"
      @input="emit('input', $event?.value ?? $event?.detail?.value ?? $event)"
      @focus="handleFocus"
      @blur="handleBlur"
      @confirm="emit('confirm', $event)"
    />
    <text v-if="error" class="ai-textarea__error">{{ error }}</text>
  </view>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  modelValue: { type: [String, Number], default: '' },
  placeholder: { type: String, default: '' },
  maxlength: { type: [String, Number], default: 500 },
  minHeight: { type: String, default: '96px' },
  disabled: { type: Boolean, default: false },
  readonly: { type: Boolean, default: false },
  autofocus: { type: Boolean, default: false },
  autoHeight: { type: Boolean, default: false },
  showWordLimit: { type: Boolean, default: true },
  clearable: { type: Boolean, default: false },
  error: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'input', 'focus', 'blur', 'confirm'])
const focused = ref(false)

function handleFocus(event) {
  focused.value = true
  emit('focus', event)
}

function handleBlur(event) {
  focused.value = false
  emit('blur', event)
}
</script>

<style lang="scss" scoped>
.ai-textarea {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid var(--forge-color-border-subtle, #f1f5f9);
  border-radius: 12px;
  background: #f8fafc;
  box-sizing: border-box;
  transition: border-color .16s ease, background-color .16s ease;
}

.ai-textarea.is-focused {
  border-color: var(--forge-color-primary, #3b82f6);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, .1);
}

.ai-textarea.is-error { border-color: var(--forge-color-danger, #ef4444); }
.ai-textarea.is-disabled { background: var(--forge-color-surface-subtle, #f8fafc); opacity: .76; }
.ai-textarea__control { width: 100%; }
.ai-textarea__error { display: block; padding: 2rpx 12rpx 8rpx; color: var(--forge-color-danger, #ef4444); font-size: 22rpx; }

:deep(.wd-textarea) {
  padding: 10px 12px;
  background: transparent;
}

:deep(.wd-textarea__inner) {
  height: 70px !important;
  min-height: 70px;
  color: var(--forge-color-text, #1e293b);
  font-size: 14px;
  line-height: 1.5;
}

:deep(.uni-textarea-textarea),
:deep(.uni-textarea-placeholder),
:deep(.wd-textarea__placeholder) {
  font-size: 14px !important;
  line-height: 1.5 !important;
}
</style>
