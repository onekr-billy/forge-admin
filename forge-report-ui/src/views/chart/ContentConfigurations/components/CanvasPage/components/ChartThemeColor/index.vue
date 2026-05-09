<template>
  <div class="go-chart-theme-color">
    <n-card class="card-box" size="small" hoverable embedded @click="createColorHandle">
      <n-text class="theme-card-head custom-head">
        <span>自定义颜色</span>
        <n-icon size="16">
          <add-icon></add-icon>
        </n-icon>
      </n-text>
    </n-card>

    <n-card
      v-for="(value, key) in comChartColors"
      :key="key"
      class="card-box"
      :class="{ selected: key === selectName }"
      size="small"
      hoverable
      embedded
      @click="selectTheme(key)"
    >
      <div class="theme-card-head">
        <n-ellipsis class="theme-name">{{ value.name }} </n-ellipsis>
        <div class="swatch-row">
        <span
          class="theme-color-item"
          v-for="colorItem in fetchShowColors(value.color)"
          :key="colorItem"
          :style="{ backgroundColor: colorItem }"
        ></span>
        </div>
      </div>
      <div class="theme-bottom" :style="{ backgroundImage: colorBackgroundImage(value) }"></div>
    </n-card>
    <!-- 自定义颜色 modal -->
    <create-color v-model:modelShow="createColorModelShow"></create-color>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import cloneDeep from 'lodash/cloneDeep'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasConfigEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { chartColors, ChartColorsNameType } from '@/settings/chartThemes/index'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { loadAsyncComponent, colorCustomMerge } from '@/utils'
import { icon } from '@/plugins'

type FormateCustomColorType = {
  [T: string]: {
    color: string[]
    name: string
  }
}

const CreateColor = loadAsyncComponent(() => import('../CreateColor/index.vue'))

const { SquareIcon, AddIcon } = icon.ionicons5
const chartEditStore = useChartEditStore()

// 全局颜色
const designStore = useDesignStore()
const createColorModelShow = ref(false)

// 合并默认颜色和自定义颜色
const comChartColors = computed(() => {
  return colorCustomMerge(chartEditStore.getEditCanvasConfig.chartCustomThemeColorInfo)
})

// 颜色
const themeColor = computed(() => {
  return designStore.getAppTheme
})

// 选中名称
const selectName = computed(() => {
  return chartEditStore.getEditCanvasConfig.chartThemeColor
})

// 创建颜色
const createColorHandle = () => {
  createColorModelShow.value = true
}

// 底色
const colorBackgroundImage = (item: { color: string[] }) => {
  return `linear-gradient(to right, ${item.color[0]} 0%, ${item.color[5]} 100%)`
}

// 获取用来展示的色号
const fetchShowColors = (colors: Array<string>) => {
  return cloneDeep(colors).splice(0, 6)
}

// 设置主体色（在 ContentEdit > List 中进行注入）
const selectTheme = (theme: ChartColorsNameType) => {
  chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.CHART_THEME_COLOR, theme)
}
</script>

<style lang="scss" scoped>
$radius: 10px;
$itemRadius: 6px;

@include go('chart-theme-color') {
  .card-box {
    cursor: pointer;
    margin-top: 10px;
    padding: 0;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    background:
      linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.07), transparent),
      rgba(15, 23, 42, 0.28);
    border-radius: $radius;
    overflow: hidden;
    transition: all 0.22s ease;

    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.22);
      transform: translateY(-1px);
      box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22);
    }

    &.selected {
      border: 1px solid v-bind('themeColor');
      box-shadow:
        0 0 0 1px rgba(var(--app-theme-rgb), 0.1),
        0 0 18px rgba(var(--app-theme-rgb), 0.16);
    }
    &:first-child {
      margin-top: 5px;
    }
    .theme-card-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      min-height: 34px;
    }

    .custom-head {
      color: var(--app-theme, $--color-primary);
      font-weight: 700;
    }

    .theme-name {
      flex: 0 0 82px;
      text-align: left;
      font-size: 12px;
      font-weight: 600;
      @include fetch-color(1);
    }

    .swatch-row {
      flex: 1;
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 4px;
    }

    .theme-color-item {
      display: inline-block;
      width: 100%;
      height: 22px;
      border-radius: $itemRadius;
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.16);
    }

    .theme-bottom {
      position: absolute;
      left: 0;
      bottom: 0px;
      width: 100%;
      height: 2px;
    }
  }
}
</style>
