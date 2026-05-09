<template>
  <n-layout-header bordered class="go-header">
    <header class="go-header-box" :class="{ 'is-project': isProject }">
      <div class="header-item left">
        <n-space>
          <slot name="left"></slot>
        </n-space>
      </div>
      <div class="header-item center">
        <slot name="center"></slot>
      </div>
      <div class="header-item right">
        <n-space>
          <slot name="ri-left"> </slot>
          <slot name="ri-right"> </slot>
        </n-space>
      </div>
    </header>
  </n-layout-header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { PageEnum } from '@/enums/pageEnum'

const route = useRoute()

const isProject = computed(() => {
  return route.fullPath === PageEnum.BASE_HOME_ITEMS
})
</script>

<style lang="scss" scoped>
@include go(header) {
  backdrop-filter: $--filter-blur-base;
  -webkit-backdrop-filter: $--filter-blur-base;
  background:
    linear-gradient(90deg, rgba(10, 14, 23, 0.92), rgba(17, 24, 39, 0.76)),
    rgba(10, 14, 23, 0.76);
  border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.12);
  position: relative;
  z-index: 10;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(var(--app-theme-rgb), 0.35), transparent);
    pointer-events: none;
  }

  &-box {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    &.is-project {
      grid-template-columns: none;
    }
    .header-item {
      display: flex;
      align-items: center;
      &.left {
        justify-content: start;
        flex: 1;
      }
      &.center {
        justify-content: center;
      }
      &.right {
        justify-content: end;
      }
    }
    height: $--header-height;
    padding: 0 22px;
    max-width: $--max-width;
    margin: 0 auto;

    :deep(.n-button) {
      border-radius: 8px;
      transition: all 0.2s ease;
    }

    :deep(.n-button--primary-type) {
      box-shadow: 0 0 14px rgba(var(--app-theme-rgb), 0.18);
    }
  }
}
</style>
