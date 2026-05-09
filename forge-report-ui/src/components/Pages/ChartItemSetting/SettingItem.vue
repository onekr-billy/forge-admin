<template>
  <div class="go-setting-item" :style="{ width: width + 'px' }">
    <slot ></slot>
    <n-text class="name" depth="3">{{ name }}</n-text>
  </div>
</template>

<script setup lang="ts">
defineProps({
  name: {
    type: String,
    required: false
  },
  width: {
    type: Number,
    required: false
  }
})
</script>

<style lang="scss" scoped>
@include go(setting-item) {
  display: flex;
  flex-direction: column;
  min-width: 110px;
  text-align: start;
  margin-bottom: 4px;
  .name {
    margin-top: 3px;
    font-size: 11px;
    letter-spacing: 0.3px;
    color: rgba(148, 163, 184, 0.55);
  }
}
</style>
