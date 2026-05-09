<template>
  <!-- 工作台相关 -->
  <div class="go-chart go-chart-workbench">
    <div class="chart-orbit chart-orbit-a"></div>
    <div class="chart-orbit chart-orbit-b"></div>
    <n-layout class="chart-layout">
      <layout-header-pro>
        <template #left>
          <header-left-btn></header-left-btn>
        </template>
        <template #center>
          <header-title></header-title>
        </template>
        <template #ri-left>
          <header-right-btn></header-right-btn>
        </template>
      </layout-header-pro>
      <n-layout-content class="chart-main" content-style="overflow:hidden; display: flex">
        <div v-show="getCharts || getLayers" class="chart-left-stack">
          <content-charts v-if="getCharts"></content-charts>
          <content-layers v-if="getLayers"></content-layers>
        </div>
        <content-configurations class="chart-stage-shell"></content-configurations>
      </n-layout-content>
    </n-layout>
  </div>
  <!-- 右键 -->
  <n-dropdown
    placement="bottom-start"
    trigger="manual"
    size="small"
    :x="mousePosition.x"
    :y="mousePosition.y"
    :options="menuOptions"
    :show="chartEditStore.getRightMenuShow"
    :on-clickoutside="onClickOutSide"
    @select="handleMenuSelect"
  ></n-dropdown>
  <!-- 加载蒙层 -->
  <content-load></content-load>
</template>

<script setup lang="ts">
import { toRefs } from 'vue'
import { loadAsyncComponent } from '@/utils'
import { LayoutHeaderPro } from '@/layout/components/LayoutHeaderPro'
import { useContextMenu } from './hooks/useContextMenu.hook'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { useChartHistoryStore } from '@/store/modules/chartHistoryStore/chartHistoryStore'
import { useChartLayoutStore } from '@/store/modules/chartLayoutStore/chartLayoutStore'

const chartHistoryStoreStore = useChartHistoryStore()
const chartEditStore = useChartEditStore()
const { getCharts, getLayers } = toRefs(useChartLayoutStore())

// 记录初始化
chartHistoryStoreStore.canvasInit(chartEditStore.getEditCanvas)
// 注意：项目数据加载已移至 ContentEdit/hooks/useLayout.hook.ts 中
// 在 DOM 就绪（editLayoutDom 已绑定）之后再加载，确保 computedScale 能正确执行

const HeaderLeftBtn = loadAsyncComponent(() => import('./ContentHeader/headerLeftBtn/index.vue'))
const HeaderRightBtn = loadAsyncComponent(() => import('./ContentHeader/headerRightBtn/index.vue'))
const HeaderTitle = loadAsyncComponent(() => import('./ContentHeader/headerTitle/index.vue'))
const ContentLayers = loadAsyncComponent(() => import('./ContentLayers/index.vue'))
const ContentCharts = loadAsyncComponent(() => import('./ContentCharts/index.vue'))
const ContentConfigurations = loadAsyncComponent(() => import('./ContentConfigurations/index.vue'))
const ContentLoad = loadAsyncComponent(() => import('./ContentLoad/index.vue'))

// 右键
const {
  menuOptions,
  onClickOutSide,
  mousePosition,
  handleMenuSelect
} = useContextMenu()
</script>

<style lang="scss" scoped>
@include go("chart") {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  @include background-image("background-image");
}

@include go("chart-workbench") {
  position: relative;
  isolation: isolate;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -3;
    pointer-events: none;
    background:
      radial-gradient(circle at 14% 10%, rgba(var(--app-theme-rgb), 0.16), transparent 28%),
      radial-gradient(circle at 86% 18%, rgba(167, 139, 250, 0.14), transparent 30%),
      linear-gradient(135deg, rgba(2, 6, 23, 0.92), rgba(10, 14, 23, 0.72));
  }

  &::after {
    content: '';
    position: absolute;
    inset: 56px 0 0;
    z-index: -2;
    pointer-events: none;
    background-image:
      linear-gradient(rgba(var(--app-theme-rgb), 0.045) 1px, transparent 1px),
      linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.045) 1px, transparent 1px);
    background-size: 36px 36px;
    mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.85), transparent 86%);
  }

  .chart-layout,
  .chart-main {
    background: transparent;
  }

  .chart-left-stack {
    overflow: hidden;
    display: flex;
    flex: 0 0 auto;
    width: auto;
    padding: 12px 0 12px 12px;
    gap: 8px;
  }

  .chart-stage-shell {
    flex: 1;
    min-width: 0;
    padding: 12px;
    padding-left: 8px;
    background: transparent;
  }

  .chart-orbit {
    position: absolute;
    pointer-events: none;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    transform: rotate(-12deg);
    z-index: -1;
  }

  .chart-orbit-a {
    width: 520px;
    height: 180px;
    right: -120px;
    top: 92px;
  }

  .chart-orbit-b {
    width: 360px;
    height: 120px;
    left: -90px;
    bottom: 110px;
    border-color: rgba(167, 139, 250, 0.07);
  }
}
</style>
