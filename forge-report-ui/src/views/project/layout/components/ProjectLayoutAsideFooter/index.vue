<template>
  <div class="go-aside-footer">
    <div class="footer-info" v-show="!collapsed">
      <span class="footer-version">v1.3.2</span>
    </div>
  </div>
</template>
<script setup lang="ts">
const props = defineProps({ collapsed: Boolean })
</script>

<style lang="scss" scoped>
@include go('aside-footer') {
  padding: 0;

  .footer-info {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 8px 0;
  }

  .footer-version {
    font-size: 11px;
    @include fetch-color(4);
    letter-spacing: 1px;
  }
}
</style>
