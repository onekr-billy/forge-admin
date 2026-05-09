<template>
  <div class="go-config-item-box">
    <n-text class="item-left" depth="2">
      {{ name }}
      <n-space :size="5">
        <slot name="name"></slot>
      </n-space>
    </n-text>
    <div
      class="item-right"
      :class="{ 'item-right--alone': alone }"
      :style="{ ...itemRightStyle }"
    >
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  name: {
    type: String,
    required: false
  },
  alone: {
    type: Boolean,
    default: false,
    required: false
  },
  itemRightStyle: {
    type: Object,
    default: () => {},
    required: false
  }
})
</script>

<style lang="scss" scoped>
$leftWidth: 64px;
@include go('config-item-box') {
  position: relative;
  display: flex;
  gap: 8px;
  margin: 14px 0;
  .item-left {
    flex: 0 0 $leftWidth;
    text-align: left;
    margin-top: 4px;
    font-size: 12px;
    letter-spacing: 0.3px;
    color: rgba(203, 213, 225, 0.64);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .item-right {
    flex: 1;
    min-width: 0;
    display: grid;
    grid-template-columns: 1fr;
    grid-column-gap: 10px;
    grid-row-gap: 4px;
  }
}
</style>
