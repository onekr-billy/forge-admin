<template>
  <view
    class="ai-search-bar"
    :class="{ 'ai-search-bar--focused': focused, 'ai-search-bar--not-clearable': !clearable }"
  >
    <wd-search
      class="ai-search-bar__control"
      :model-value="modelValue"
      :placeholder="placeholder"
      :cancel-txt="cancelText"
      :hide-cancel="!(showCancel || focused)"
      :disabled="disabled"
      :focus="autoFocus"
      placeholder-left
      light
      focus-when-clear
      @update:model-value="handleModelUpdate"
      @change="handleInput"
      @search="handleSearch"
      @clear="handleClear"
      @cancel="handleCancel"
      @focus="handleFocus"
      @blur="handleBlur"
    />
  </view>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '搜索'
  },
  cancelText: {
    type: String,
    default: '取消'
  },
  showCancel: {
    type: Boolean,
    default: false
  },
  clearable: {
    type: Boolean,
    default: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  autoFocus: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'input', 'search', 'clear', 'cancel', 'focus', 'blur'])
const focused = ref(false)

function handleModelUpdate(value) {
  emit('update:modelValue', value)
}

function handleInput(event) {
  const value = event?.value ?? event?.detail?.value ?? event
  emit('input', value)
}

function handleSearch(event) {
  emit('search', event?.value ?? props.modelValue)
}

function handleClear() {
  emit('clear')
}

function handleCancel() {
  emit('update:modelValue', '')
  emit('cancel')
}

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
.ai-search-bar {
  width: 100%;
}

:deep(.wd-search.ai-search-bar__control) {
  padding: 0;
  background: transparent;
}

:deep(.wd-search__block) {
  display: flex;
  height: 44px;
  min-width: 0;
  align-items: center;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #f1f5f9;
  transition: border-color .16s ease, background-color .16s ease;
}

.ai-search-bar--focused :deep(.wd-search__block) {
  border-color: var(--primary-color, #3b82f6);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, .1);
}

:deep(.wd-search__field) {
  display: flex;
  height: 100%;
  min-width: 0;
  flex: 1;
  align-items: center;
  background: transparent;
}

:deep(.wd-search__search-left-icon) {
  position: static;
  display: flex;
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
  color: var(--text-muted, #94a3b8);
  font-size: 16px;
  transform: none;
}

:deep(.wd-search__input) {
  height: 100%;
  min-width: 0;
  flex: 1;
  padding: 0;
  color: #1e293b;
  font-size: 14px;
  font-weight: 400;
  line-height: normal;
  box-sizing: border-box;
}

:deep(.wd-search__input .uni-input-wrapper),
:deep(.wd-search__input .uni-input-form),
:deep(.wd-search__input .uni-input-input) {
  height: 42px;
  min-width: 0;
  line-height: 42px;
}

:deep(.wd-search__input .uni-input-placeholder) {
  display: flex;
  height: 42px;
  align-items: center;
}

:deep(.wd-search__placeholder-txt) {
  color: #94a3b8;
  font-size: 14px;
  font-weight: 400;
  line-height: normal;
}

:deep(.wd-search__search-icon),
:deep(.wd-search__clear) { display: flex; align-items: center; justify-content: center; }

:deep(.wd-search__cancel) {
  color: var(--primary-color, #3b82f6);
  font-size: 13px;
  font-weight: 500;
}

.ai-search-bar--not-clearable :deep(.wd-search__clear) {
  display: none;
}
</style>
