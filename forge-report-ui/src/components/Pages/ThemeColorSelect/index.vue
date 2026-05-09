<template>
  <n-button quaternary @click="modelShow = true" title="颜色">
    <n-icon size="20" :depth="1">
      <color-wand-icon></color-wand-icon>
    </n-icon>
  </n-button>
  <n-modal
    v-model:show="modelShow"
    :mask-closable="true"
    transform-origin="center"
  >
    <div class="color-panel">
      <div class="panel-header">
        <div class="header-left">
          <span class="header-diamond">&#9670;</span>
          <span class="header-title">主题颜色</span>
          <div class="header-preview" v-if="appThemeDetail">
            <span class="preview-dot" :style="{ background: designStore.appTheme }"></span>
            <span class="preview-name" :style="{ color: designStore.appTheme }">{{ appThemeDetail.name }}</span>
            <span class="preview-hex">{{ designStore.appTheme }}</span>
          </div>
          <div class="header-preview" v-else>
            <span class="preview-dot" style="background: var(--app-theme)"></span>
            <span class="preview-name" style="color: var(--app-theme)">电光青</span>
            <span class="preview-hex">var(--app-theme)</span>
          </div>
        </div>
        <n-icon size="22" class="panel-close" @click="modelShow = false">
          <close-icon></close-icon>
        </n-icon>
      </div>

      <div class="panel-body" ref="contentRef">
        <div class="color-section" v-if="modelShow">
          <div class="section-label">
            <span class="label-dot"></span> 推荐色板
          </div>
          <color-list :designColor="designColorRecommend" @colorSelectHandle="colorSelectHandle"></color-list>
        </div>

        <div class="section-divider"></div>

        <div class="color-section" v-if="modelShow">
          <div class="section-label">
            <span class="label-dot"></span> 中国色 · 全部 {{ designColorSplit.length }} / {{ designColor.length }}
          </div>
          <color-list :designColor="designColorSplit" @colorSelectHandle="colorSelectHandle"></color-list>
        </div>
      </div>

      <div class="panel-footer">
        <span class="footer-text">中国色 · zhongguose.com</span>
        <span class="footer-text">SCROLL 加载更多</span>
      </div>
    </div>
  </n-modal>
</template>

<script lang="ts" setup>
import { ref, computed, watch, toRefs } from 'vue'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { AppThemeColorType } from '@/store/modules/designStore/designStore.d'
import { icon } from '@/plugins'
import { loadAsyncComponent } from '@/utils'
import { useScroll } from '@vueuse/core'
import designColor from '@/settings/designColor.json'
import designColorRecommend from '@/settings/designColorRecommend.json'

const ColorList = loadAsyncComponent(() => import('./components/ColorList.vue'))
const { ColorWandIcon, CloseIcon } = icon.ionicons5

let splitNumber = 50

const designStore = useDesignStore()
const modelShow = ref(false)
const contentRef = ref<HTMLElement | null>(null)
const designColorSplit = ref(designColor.slice(0, splitNumber))

const { arrivedState } = useScroll(contentRef, { offset: { bottom: 200 } })
const { bottom } = toRefs(arrivedState)

const appThemeDetail = computed(() => designStore.getAppThemeDetail)

const colorSelectHandle = (color: AppThemeColorType) => {
  designStore.setAppColor(color)
}

watch(() => bottom.value, (newData: boolean) => {
  if (newData) {
    splitNumber = splitNumber + 50
    designColorSplit.value = designColor.slice(0, splitNumber)
  }
})

watch(() => modelShow.value, (show: boolean) => {
  if (!show) splitNumber = 50
})
</script>

<style lang="scss" scoped>
.color-panel {
  width: 94vw;
  max-width: 1100px;
  height: 85vh;
  display: flex;
  flex-direction: column;
  border-radius: $--border-radius-lg;
  overflow: hidden;
  @include fetch-bg-color('background-color');
  border: 1px solid rgba(var(--app-theme-rgb), 0.08);
  backdrop-filter: blur(20px);
  box-shadow:
    0 24px 64px rgba(0, 0, 0, 0.6),
    0 0 32px rgba(var(--app-theme-rgb), 0.04);
  position: relative;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background-image: radial-gradient(circle at 1px 1px, rgba(var(--app-theme-rgb), 0.03) 1px, transparent 0);
    background-size: 32px 32px;
    z-index: 0;
  }

  > * { position: relative; z-index: 1; }
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 28px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .header-diamond {
    font-size: 20px;
    color: $--color-primary;
    text-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.5);
  }

  .header-title {
    font-size: 18px;
    font-weight: 700;
    @include fetch-color();
    letter-spacing: 2px;
  }

  .header-preview {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 14px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid rgba(255, 255, 255, 0.05);
    margin-left: 10px;

    .preview-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      box-shadow: 0 0 8px currentColor;
      flex-shrink: 0;
    }

    .preview-name {
      font-size: 13px;
      font-weight: 600;
    }

    .preview-hex {
      font-size: 11px;
      @include fetch-color(3);
      font-family: 'Courier New', monospace;
    }
  }

  .panel-close {
    @include fetch-color(3);
    cursor: pointer;
    transition: all 0.2s;
    &:hover { color: $--color-red; transform: rotate(90deg); }
  }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 28px;

  .color-section {
    margin-bottom: 8px;
  }

  .section-label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 2px;
    @include fetch-color(3);
    margin-bottom: 16px;
    font-weight: 600;

    .label-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: $--color-accent;
      box-shadow: 0 0 6px $--color-accent-glow;
    }
  }

  .section-divider {
    height: 1px;
    margin: 12px 0 24px;
    background: linear-gradient(90deg, transparent, rgba(var(--app-theme-rgb), 0.08), transparent);
  }
}

.panel-footer {
  display: flex;
  justify-content: space-between;
  padding: 10px 28px 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.03);
  flex-shrink: 0;

  .footer-text {
    font-size: 10px;
    @include fetch-color(4);
    letter-spacing: 1px;
  }
}
</style>
