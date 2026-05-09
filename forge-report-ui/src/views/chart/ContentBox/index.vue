<template>
  <!-- 每个小模块的公共样式 -->
  <div class="go-content-box" :class="[`bg-depth${depth}`, flex && 'flex']">
    <div v-if="showTop" class="top go-mt-0 go-flex-no-wrap">
      <n-space class="go-flex-no-wrap" :size="5">
        <n-ellipsis>
          <n-text>{{ title }}</n-text>
        </n-ellipsis>
        <div class="mt-1">
          <slot name="icon"></slot>
        </div>
      </n-space>
      <n-space class="go-flex-no-wrap" align="center" style="gap: 4px">
        <slot name="top-right"></slot>
        <n-icon v-show="backIcon" size="20" class="go-cursor-pointer go-d-block" @click="backHandle">
          <chevron-back-outline-icon></chevron-back-outline-icon>
        </n-icon>
      </n-space>
    </div>

    <div class="content" :class="{
      'content-height-show-top-bottom': showBottom || showTop,
      'content-height-show-both': showBottom && showTop
    }">
      <template v-if="disabledScroll">
        <slot></slot>
      </template>
      <template v-else-if="xScroll">
        <n-scrollbar x-scrollable>
          <n-scrollbar>
            <slot></slot>
          </n-scrollbar>
        </n-scrollbar>
      </template>

      <template v-else>
        <n-scrollbar>
          <slot></slot>
        </n-scrollbar>
      </template>
    </div>

    <div v-if="showBottom" class="bottom go-mt-0">
      <slot name="bottom"></slot>
    </div>
    <div class="aside">
      <slot name="aside"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { icon } from '@/plugins'
const { ChevronBackOutlineIcon } = icon.ionicons5

const emit = defineEmits(['back'])

defineProps({
  title: String,
  showTop: {
    type: Boolean,
    default: true
  },
  showBottom: {
    type: Boolean,
    default: false
  },
  flex: {
    type: Boolean,
    default: false
  },
  // back
  backIcon: {
    type: Boolean,
    default: true
  },
  // 背景深度
  depth: {
    type: Number,
    default: 1
  },
  // x 轴滚动
  xScroll: {
    type: Boolean,
    default: false
  },
  disabledScroll: {
    type: Boolean,
    default: false
  },
})

const backHandle = () => {
  emit('back')
}
</script>

<style lang="scss" scoped>
$topOrBottomHeight: 40px;
$workbenchGapHeight: 24px;

@include go(content-box) {
  height: calc(100vh - #{$--header-height} - #{$workbenchGapHeight});
  margin: 0;
  margin-bottom: 0;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(var(--app-theme-rgb), 0.08);
  background: rgba(10, 14, 23, 0.58);
  backdrop-filter: $--filter-blur-base;
  -webkit-backdrop-filter: $--filter-blur-base;
  box-shadow:
    0 14px 42px rgba(0, 0, 0, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.03);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.035), transparent 120px),
      radial-gradient(circle at 0 0, rgba(var(--app-theme-rgb), 0.08), transparent 220px);
    opacity: 0.8;
    z-index: 0;
  }

  > .top,
  > .content,
  > .bottom,
  > .aside {
    position: relative;
    z-index: 1;
  }

  &.bg-depth0 {
    @include fetch-bg-color('background-color1');

    .bottom,
    .top {
      @include fetch-bg-color('background-color1');
    }
  }

  &.bg-depth1 {
    @include fetch-bg-color('background-color1');

    .bottom,
    .top {
      @include fetch-bg-color('background-color2');
    }
  }

  &.bg-depth2 {
    @include fetch-bg-color('background-color2');

    .bottom,
    .top {
      @include fetch-bg-color('background-color3');
    }
  }

  &.bg-depth3 {
    @include fetch-bg-color('background-color3');

    .bottom,
    .top {
      @include fetch-bg-color('background-color4');
    }
  }

  &.flex {
    flex: 1;
  }

  .top,
  .bottom {
    display: flex;
    justify-content: space-between;
    flex-wrap: nowrap;
    align-items: center;
    height: $topOrBottomHeight;
    padding: 0 12px;
    border-top: 1px solid;
    border-color: rgba(var(--app-theme-rgb), 0.08);

    :deep(.n-text) {
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.5px;
      color: var(--app-theme, $--color-primary);
      text-shadow: 0 0 8px rgba(var(--app-theme-rgb), 0.28);
    }

    .mt-1 {
      margin-top: 2px;
    }
  }

  .top {
    border-bottom: 1px solid;
    border-color: rgba(var(--app-theme-rgb), 0.08);
  }

  .content {
    height: calc(100vh - #{$--header-height} - #{$workbenchGapHeight});
    overflow: hidden;
  }

  .aside {
    position: relative;
  }

  .content-height-show-top-bottom {
    height: calc(100vh - #{$--header-height} - #{$workbenchGapHeight} - #{$topOrBottomHeight});
  }

  .content-height-show-both {
    height: calc(100vh - #{$--header-height} - #{$workbenchGapHeight} - #{$topOrBottomHeight} - #{$topOrBottomHeight});
  }
}
</style>
