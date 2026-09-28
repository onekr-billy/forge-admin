<template>
  <wd-popup
    :model-value="modelValue"
    position="bottom"
    :modal="mask"
    :close-on-click-modal="closeOnMask"
    :z-index="Number(zIndex)"
    :safe-area-inset-bottom="true"
    root-portal
    @update:model-value="emit('update:modelValue', $event)"
    @click-modal="emit('maskClick')"
    @close="emit('close')"
  >
    <view class="ai-popup-sheet__panel" :class="{ 'is-round': round }" :style="{ maxHeight }">
      <view v-if="showHandle" class="ai-popup-sheet__handle" />
      <slot name="header">
        <view class="ai-popup-sheet__head">
          <view class="ai-popup-sheet__title-block">
            <text v-if="title" class="ai-popup-sheet__title">{{ title }}</text>
            <text v-if="description" class="ai-popup-sheet__desc">{{ description }}</text>
          </view>
          <button v-if="showClose" class="ai-popup-sheet__close" @click="close">×</button>
        </view>
      </slot>
      <scroll-view v-if="scroll" class="ai-popup-sheet__body" scroll-y :show-scrollbar="false" :style="{ maxHeight: bodyMaxHeight }">
        <view class="ai-popup-sheet__content"><slot /></view>
      </scroll-view>
      <view v-else class="ai-popup-sheet__content"><slot /></view>
      <view v-if="$slots.footer" class="ai-popup-sheet__footer"><slot name="footer" /></view>
    </view>
  </wd-popup>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  placement: { type: String, default: 'bottom' },
  maxHeight: { type: String, default: '78vh' },
  bodyMaxHeight: { type: String, default: 'calc(78dvh - 128px)' },
  zIndex: { type: [Number, String], default: 9990 },
  mask: { type: Boolean, default: true },
  closeOnMask: { type: Boolean, default: true },
  showClose: { type: Boolean, default: true },
  showHandle: { type: Boolean, default: false },
  scroll: { type: Boolean, default: true },
  round: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue', 'close', 'maskClick'])
function close() { emit('update:modelValue', false) }
</script>

<style lang="scss" scoped>
.ai-popup-sheet__panel { display: flex; width: 100vw; min-height: 60px; flex-direction: column; overflow: hidden; padding: 20px 20px 0; background: #fff; box-shadow: var(--forge-shadow-float); box-sizing: border-box; }
.ai-popup-sheet__panel.is-round { border-radius: var(--forge-radius-popup, 24px) var(--forge-radius-popup, 24px) 0 0; }
.ai-popup-sheet__handle { width: 28px; height: 3px; margin: 0 auto 8px; border-radius: 2px; background: #cbd5e1; }
.ai-popup-sheet__head { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 18px; padding-bottom: 16px; border-bottom: 1px solid var(--border-light); }
.ai-popup-sheet__title-block { min-width: 0; flex: 1; }
.ai-popup-sheet__title, .ai-popup-sheet__desc { display: block; }
.ai-popup-sheet__title { color: var(--text-strong); font-size: 17px; font-weight: 700; line-height: 1.35; }
.ai-popup-sheet__desc { margin-top: 3px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.ai-popup-sheet__close { display: flex; width: 40px; height: 40px; align-items: center; justify-content: center; margin: -9px -9px 0 0; padding: 0; border: 0; border-radius: 50%; color: var(--text-muted); font-size: 20px; line-height: 1; background: transparent; }
.ai-popup-sheet__close::after { border: 0; }
.ai-popup-sheet__body { min-height: 0; flex: 1 1 auto; overflow-y: auto; }
.ai-popup-sheet__content { padding-bottom: 4px; }
.ai-popup-sheet__footer { flex: 0 0 auto; margin-top: 18px; padding: 16px 0; border-top: 1px solid var(--border-light); background: #fff; }
:deep(.wd-popup) { max-height: 100dvh; overflow: hidden; box-sizing: border-box; background: transparent; }

@media (min-width: 1024px) {
  .ai-popup-sheet__panel {
    width: min(720px, 100vw);
    margin: 0 auto;
  }
}
</style>
