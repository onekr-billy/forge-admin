<template>
  <div class="go-footer">
    <slot>
    </slot>
  </div>
</template>

<script setup lang="ts">
</script>

<style lang="scss" scoped>
@include go(footer) {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  padding: 0 24px;
  height: $--footer-height;
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
  @include fetch-bg-color('background-color1');
}
</style>
