<template>
  <wd-select-picker
    v-model="selectedValue"
    type="radio"
    :columns="options"
    value-key="value"
    label-key="label"
    :title="title"
    :filterable="filterable"
    :filter-placeholder="filterPlaceholder"
    :show-confirm="false"
    :safe-area-inset-bottom="true"
    :z-index="10010"
    root-portal
    @confirm="handleConfirm"
  >
    <view class="dropdown-trigger">
      <slot name="trigger">
        <view class="default-trigger">
          <text>{{ selectedLabel || placeholder }}</text>
          <wd-icon name="arrow-down" size="16px" color="#94a3b8" />
        </view>
      </slot>
    </view>
  </wd-select-picker>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number, Boolean], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  title: { type: String, default: '请选择' },
  filterable: { type: Boolean, default: false },
  filterPlaceholder: { type: String, default: '搜索选项' },
})

const emit = defineEmits(['update:modelValue', 'change'])

const selectedValue = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const selectedLabel = computed(() => {
  const selected = props.options.find(option => option.value === props.modelValue)
  return selected?.label || ''
})

function handleConfirm(event) {
  emit('change', event?.value)
}
</script>

<style lang="scss" scoped>
.dropdown-trigger {
  cursor: pointer;
}

.default-trigger {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--forge-color-border, #e2e8f0);
  border-radius: var(--forge-radius-control, 12rpx);
  color: var(--forge-color-text-regular, #475569);
  background: #fff;
  box-sizing: border-box;
}
</style>
