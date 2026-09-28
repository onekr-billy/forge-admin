<template>
  <view class="ai-tabs">
    <view class="ai-tabs-header">
      <button
        v-for="(tab, index) in tabs" 
        :key="index"
        class="ai-tabs-tab"
        :class="{ 'ai-tabs-tab--active': activeIndex === index, 'is-disabled': tab.disabled === true }"
        :disabled="tab.disabled === true"
        @click="handleTabClick(index)"
      >
        <text class="ai-tabs-tab-text">{{tab.label || tab}}</text>
      </button>
    </view>
    <view class="ai-tabs-content">
      <slot></slot>
    </view>
  </view>
</template>

<script setup>
import { computed, provide } from 'vue'

const props = defineProps({
  tabs: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const activeIndex = computed(() => props.modelValue)

const handleTabClick = (index) => {
  if (props.tabs[index]?.disabled === true) return
  emit('update:modelValue', index)
  emit('change', index)
}

provide('activeIndex', activeIndex)
</script>

<style lang="scss" scoped>
.ai-tabs {
  &-header {
    gap: 24px;
    padding: 0 2px;
    display: flex;
    overflow-x: auto;
    border-bottom: 1px solid var(--border-light);
    background: transparent;
    white-space: nowrap;
  }
  
  &-tab {
    position: relative;
    flex: 1 0 auto;
    display: flex;
    min-width: auto;
    min-height: 44px;
    align-items: center;
    justify-content: center;
    margin: 0;
    padding: 0 2px 8px;
    border: 0;
    border-radius: 0;
    color: var(--text-secondary);
    background: transparent;
    line-height: 1.4;
    
    &--active {
      color: var(--primary-color);
      background: transparent;
      box-shadow: none;
    }

    &--active::before {
      position: absolute;
      right: 0;
      bottom: 0;
      left: 0;
      height: 2px;
      border-radius: 2px 2px 0 0;
      background: var(--primary-color);
      content: '';
    }
  }
  &-tab::after { border: 0; }
  &-tab:active { opacity: .8; }
  &-tab.is-disabled { opacity: .45; }
  
  &-tab-text {
    color: var(--text-secondary);
    font-size: 14px;
    
    .ai-tabs-tab--active & {
      color: var(--primary-color);
      font-weight: 700;
    }
  }
}
</style>
