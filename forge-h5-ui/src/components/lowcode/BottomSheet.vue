<template>
  <AiPopupSheet
    :model-value="visible"
    placement="bottom"
    :title="title"
    :max-height="maxHeight"
    :body-max-height="bodyMaxHeight"
    :mask="true"
    :round="true"
    :show-handle="true"
    :show-close="true"
    :scroll="true"
    @update:model-value="emit('update:visible', $event)"
    @close="emit('close')"
  >
    <slot />
  </AiPopupSheet>
</template>

<script setup>
import AiPopupSheet from '@/components/AiPopupSheet.vue'

defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '' },
  maxHeight: { type: String, default: '64vh' },
  bodyMaxHeight: { type: String, default: 'calc(64vh - 172rpx - env(safe-area-inset-bottom))' },
})

const emit = defineEmits(['update:visible', 'close'])
</script>
