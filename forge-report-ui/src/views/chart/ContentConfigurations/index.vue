<template>
  <n-layout class="chart-stage-layout" has-sider sider-placement="right">
    <n-layout-content class="chart-stage-content">
      <div class="stage-status-bar">
        <span class="status-kicker">CANVAS</span>
        <span class="status-copy">拖拽编排 · Ctrl/⌘ + 滚轮缩放 · 空格拖动画布</span>
      </div>
      <!-- 图表拖拽区域 -->
      <content-edit></content-edit>
    </n-layout-content>
    <n-layout-sider
      class="chart-config-sider"
      collapse-mode="transform"
      :collapsed-width="0"
      :width="350"
      :collapsed="collapsed"
      :native-scrollbar="false"
      show-trigger="bar"
      @collapse="collapsedHandle"
      @expand="expandHandle"
      >
        <content-box class="go-content-configurations go-boderbox" :show-top="false" :depth="2">
        <div class="inspector-head">
          <span class="inspector-kicker">{{ selectTarget ? 'SELECTED' : 'CANVAS' }}</span>
          <strong>{{ inspectorTitle }}</strong>
          <p>{{ inspectorDesc }}</p>
        </div>
        <!-- 页面配置 -->
        <n-tabs v-if="!selectTarget" class="tabs-box" size="small" type="segment">
          <n-tab-pane
            v-for="item in globalTabList"
            :key="item.key"
            :name="item.key"
            size="small"
            display-directive="show:lazy"
          >
            <template #tab>
              <n-space>
                <span>{{ item.title }}</span>
                <n-icon size="16" class="icon-position">
                  <component :is="item.icon"></component>
                </n-icon>
              </n-space>
            </template>
            <component :is="item.render"></component>
          </n-tab-pane>
        </n-tabs>

        <!-- 编辑 -->
        <n-tabs v-if="selectTarget" v-model:value="tabsSelect" class="tabs-box" size="small" type="segment">
          <n-tab-pane
            v-for="item in selectTarget.isGroup ? chartsDefaultTabList : chartsTabList"
            :key="item.key"
            :name="item.key"
            size="small"
            display-directive="show:lazy"
          >
            <template #tab>
              <n-space>
                <span>{{ item.title }}</span>
                <n-icon size="16" class="icon-position">
                  <component :is="item.icon"></component>
                </n-icon>
              </n-space>
            </template>
            <component :is="item.render"></component>
          </n-tab-pane>
        </n-tabs>
      </content-box>
    </n-layout-sider>
  </n-layout>
</template>

<script setup lang="ts">
import { ref, toRefs, watch, computed } from 'vue'
import { icon } from '@/plugins'
import { loadAsyncComponent } from '@/utils'
import { ContentBox } from '../ContentBox/index'
import { TabsEnum } from './index.d'
import { useChartLayoutStore } from '@/store/modules/chartLayoutStore/chartLayoutStore'
import { ChartLayoutStoreEnum } from '@/store/modules/chartLayoutStore/chartLayoutStore.d'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'

const { getDetails } = toRefs(useChartLayoutStore())
const { setItem } = useChartLayoutStore()
const chartEditStore = useChartEditStore()

const { ConstructIcon, FlashIcon, DesktopOutlineIcon, LeafIcon, RocketIcon } = icon.ionicons5

const ContentEdit = loadAsyncComponent(() => import('../ContentEdit/index.vue'))
const CanvasPage = loadAsyncComponent(() => import('./components/CanvasPage/index.vue'))
const ChartSetting = loadAsyncComponent(() => import('./components/ChartSetting/index.vue'))
const ChartData = loadAsyncComponent(() => import('./components/ChartData/index.vue'))
const ChartEvent = loadAsyncComponent(() => import('./components/ChartEvent/index.vue'))
const ChartAnimation = loadAsyncComponent(() => import('./components/ChartAnimation/index.vue'))

const collapsed = ref<boolean>(getDetails.value)
const tabsSelect = ref<TabsEnum>(TabsEnum.CHART_SETTING)

const collapsedHandle = () => {
  collapsed.value = true
  setItem(ChartLayoutStoreEnum.DETAILS, true)
}

const expandHandle = () => {
  collapsed.value = false
  setItem(ChartLayoutStoreEnum.DETAILS, false)
}

const selectTarget = computed(() => {
  const selectId = chartEditStore.getTargetChart.selectId
  // 排除多个
  if (selectId.length !== 1) return undefined
  const target = chartEditStore.componentList[chartEditStore.fetchTargetIndex()]
  if (target?.isGroup) {
    // eslint-disable-next-line vue/no-side-effects-in-computed-properties
    tabsSelect.value = TabsEnum.CHART_SETTING
  }
  return target
})

const inspectorTitle = computed(() => {
  return selectTarget.value?.chartConfig?.title || '页面配置'
})

const inspectorDesc = computed(() => {
  if (!selectTarget.value) return '画布尺寸、背景、主题与全局渲染配置'
  return selectTarget.value.isGroup ? '分组基础属性、动画与层级控制' : '组件样式、数据、事件与动画控制'
})

watch(getDetails, newData => {
  if (newData) {
    collapsedHandle()
  } else {
    expandHandle()
  }
})

// 页面设置
const globalTabList = [
  {
    key: TabsEnum.PAGE_SETTING,
    title: '页面配置',
    icon: DesktopOutlineIcon,
    render: CanvasPage
  }
]

const chartsDefaultTabList = [
  {
    key: TabsEnum.CHART_SETTING,
    title: '定制',
    icon: ConstructIcon,
    render: ChartSetting
  },
  {
    key: TabsEnum.CHART_ANIMATION,
    title: '动画',
    icon: LeafIcon,
    render: ChartAnimation
  }
]

const chartsTabList = [
  ...chartsDefaultTabList,
  {
    key: TabsEnum.CHART_DATA,
    title: '数据',
    icon: FlashIcon,
    render: ChartData
  },
  {
    key: TabsEnum.CHART_EVENT,
    title: '事件',
    icon: RocketIcon,
    render: ChartEvent
  }
]
</script>

<style lang="scss" scoped>
@include go(content-configurations) {
  overflow: hidden;
  border-radius: 10px 0 0 10px;

  .inspector-head {
    margin: 12px 12px 4px;
    padding: 14px;
    border-radius: 12px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.12);
    background:
      linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.12), transparent),
      rgba(2, 6, 23, 0.28);

    .inspector-kicker {
      display: block;
      margin-bottom: 5px;
      font-size: 10px;
      letter-spacing: 1.4px;
      @include fetch-color(4);
    }

    strong {
      display: block;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 16px;
      color: var(--app-theme, $--color-primary);
      text-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.24);
    }

    p {
      margin: 6px 0 0;
      font-size: 11px;
      line-height: 1.5;
      @include fetch-color(3);
    }
  }

  .tabs-box {
    padding: 8px 12px 12px;
    .icon-position {
      padding-top: 2px;
    }
  }

  :deep(.n-tabs-nav) {
    background: rgba(15, 23, 42, 0.28);
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    border-radius: 10px;
    padding: 3px;
  }

  :deep(.n-tabs-tab) {
    border-radius: 8px;
    font-size: 12px;
    letter-spacing: 0.4px;
    padding: 4px 14px;
    transition: all 0.2s ease;
  }

  :deep(.n-tabs-tab--active) {
    background: rgba(var(--app-theme-rgb), 0.12);
    box-shadow: 0 0 18px rgba(var(--app-theme-rgb), 0.12);
  }

  :deep(.n-tabs-pane-wrapper) {
    padding-top: 10px;
  }

  :deep(.n-form-item) {
    margin-bottom: 10px;
  }

  :deep(.n-input),
  :deep(.n-input-number),
  :deep(.n-base-selection),
  :deep(.n-color-picker-trigger) {
    border-radius: 8px;
  }

  :deep(.n-button) {
    border-radius: 8px;
    transition: all 0.2s ease;
  }

  :deep(.n-card) {
    border-radius: 10px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    background: rgba(15, 23, 42, 0.2);
  }

  :deep(.n-collapse-item) {
    border-radius: 10px;
    overflow: hidden;
    margin-bottom: 8px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.07);
    background: rgba(15, 23, 42, 0.18);

    .n-collapse-item__header {
      padding: 10px 14px;
      font-size: 13px;
      font-weight: 600;
      color: rgba(226, 232, 240, 0.86);
    }

    .n-collapse-item__content-inner {
      padding: 8px 14px 14px;
    }
  }

  :deep(.n-collapse-item--active) {
    border-color: rgba(var(--app-theme-rgb), 0.14);
  }

  :deep(.n-tag) {
    border-radius: 6px;
    padding: 2px 10px;
  }

  :deep(.n-table) {
    border-radius: 8px;
    overflow: hidden;
  }
}

.chart-stage-layout {
  height: 100%;
  background: transparent;

  :deep(.n-layout-scroll-container) {
    display: flex;
  }
}

.chart-stage-content {
  position: relative;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid rgba(var(--app-theme-rgb), 0.08);
  background:
    radial-gradient(circle at 50% 20%, rgba(var(--app-theme-rgb), 0.08), transparent 36%),
    rgba(2, 6, 23, 0.18);
}

.stage-status-bar {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: calc(100% - 56px);
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(var(--app-theme-rgb), 0.12);
  background: rgba(10, 14, 23, 0.66);
  backdrop-filter: blur(14px);
  pointer-events: none;
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.24);

  .status-kicker {
    color: var(--app-theme, $--color-primary);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 1px;
  }

  .status-copy {
    font-size: 11px;
    white-space: nowrap;
    @include fetch-color(3);
  }
}

.chart-config-sider {
  margin-left: 10px;
  border-radius: 10px;
  overflow: hidden;
  background: transparent;

  :deep(.n-layout-toggle-button) {
    border-color: rgba(var(--app-theme-rgb), 0.14);
    background: rgba(10, 14, 23, 0.86);
    color: var(--app-theme, $--color-primary);
    box-shadow: 0 0 14px rgba(var(--app-theme-rgb), 0.16);
  }
}
</style>
