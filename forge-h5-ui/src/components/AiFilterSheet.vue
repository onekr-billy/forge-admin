<template>
  <AiPopupSheet
    :model-value="modelValue"
    :title="title"
    :description="description"
    max-height="82vh"
    body-max-height="calc(82dvh - 132px)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <view class="ai-filter-sheet__fields"><slot /></view>
    <template #footer>
      <view class="ai-filter-sheet__actions">
        <AiButton variant="secondary" block @click="emit('reset')">重置</AiButton>
        <AiButton block @click="emit('apply')">查看结果</AiButton>
      </view>
    </template>
  </AiPopupSheet>
</template>

<script setup>
import AiButton from '@/components/AiButton.vue'
import AiPopupSheet from '@/components/AiPopupSheet.vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '筛选条件' },
  description: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'reset', 'apply'])
</script>

<style lang="scss" scoped>
.ai-filter-sheet__fields { display: flex; flex-direction: column; gap: 16px; padding: 4px 0 12px; }
.ai-filter-sheet__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
</style>
