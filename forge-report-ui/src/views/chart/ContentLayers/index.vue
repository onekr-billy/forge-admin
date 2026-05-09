<template>
  <content-box
    class="go-content-layers"
    :class="{ scoped: !chartLayoutStore.getLayers }"
    title="图层"
    :depth="2"
    :showTop="false"
    :disabledScroll="true"
    @back="backHandle"
    @mousedown="boxMousedownHandle($event)"
  >
    <div class="layers-shell">
      <div class="layers-toolbar">
        <div class="layers-title">
          <span>LAYER</span>
          <strong>图层管理</strong>
        </div>
        <n-button-group class="layers-mode-group">
          <n-button
            v-for="(item, index) in layerModeList"
            :key="index"
            ghost
            size="small"
            :type="layerMode === item.value ? 'primary' : 'tertiary'"
            @click="changeLayerType(item.value)"
          >
            <n-tooltip :show-arrow="false" trigger="hover">
              <template #trigger>
                <n-icon size="14" :component="item.icon" />
              </template>
              {{ item.label }}
            </n-tooltip>
          </n-button>
        </n-button-group>
      </div>

      <div class="layers-summary">
        <span>{{ reverseList.length }} 个图层</span>
        <i></i>
      </div>

      <div class="layers-body">
        <n-space v-if="reverseList.length === 0" justify="center">
          <n-text class="not-layer-text">暂无图层~</n-text>
        </n-space>

        <n-scrollbar v-else>
          <!-- https://github.com/SortableJS/vue.draggable.next -->
          <draggable item-key="id" v-model="layerList" ghostClass="ghost" @change="onMoveCallback">
            <template #item="{ element }">
              <div class="go-content-layer-box">
                <!-- 组合 -->
                <layers-group-list-item
                  v-if="element.isGroup"
                  :componentGroupData="element"
                  :layer-mode="layerMode"
                ></layers-group-list-item>
                <!-- 单组件 -->
                <layers-list-item
                  v-else
                  :componentData="element"
                  :layer-mode="layerMode"
                  @mousedown="mousedownHandle($event, element)"
                  @mouseenter="mouseenterHandle(element)"
                  @mouseleave="mouseleaveHandle(element)"
                  @contextmenu="handleContextMenu($event, element, optionsHandle)"
                ></layers-list-item>
              </div>
            </template>
          </draggable>
        </n-scrollbar>
      </div>
    </div>
  </content-box>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Draggable from 'vuedraggable'
import cloneDeep from 'lodash/cloneDeep'
import { ContentBox } from '../ContentBox/index'
import { useChartLayoutStore } from '@/store/modules/chartLayoutStore/chartLayoutStore'
import { ChartLayoutStoreEnum, LayerModeEnum } from '@/store/modules/chartLayoutStore/chartLayoutStore.d'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { CreateComponentType, CreateComponentGroupType } from '@/packages/index.d'
import { MenuOptionsItemType } from '@/views/chart/hooks/useContextMenu.hook.d'
import { useContextMenu } from '@/views/chart/hooks/useContextMenu.hook'
import { MenuEnum, MouseEventButton, WinKeyboard, MacKeyboard } from '@/enums/editPageEnum'

import { LayersListItem } from './components/LayersListItem/index'
import { LayersGroupListItem } from './components/LayersGroupListItem/index'

import { icon } from '@/plugins'

const { ListIcon } = icon.ionicons5
const { LaptopIcon } = icon.carbon
const chartLayoutStore = useChartLayoutStore()
const chartEditStore = useChartEditStore()
const { handleContextMenu, onClickOutSide } = useContextMenu()

const layerModeList = [
  { label: '缩略图', icon: LaptopIcon, value: LayerModeEnum.THUMBNAIL },
  { label: '文本列表', icon: ListIcon, value: LayerModeEnum.TEXT }
]

const layerList = ref<any>([])
const layerMode = ref<LayerModeEnum>(chartLayoutStore.getLayerType)

// 逆序展示
const reverseList = computed(() => {
  const list: Array<CreateComponentType | CreateComponentGroupType> = cloneDeep(chartEditStore.getComponentList)
  return list.reverse()
})

watch(
  () => reverseList.value,
  newValue => {
    layerList.value = newValue
  }
)

// 右键事件
const optionsHandle = (
  targetList: MenuOptionsItemType[],
  allList: MenuOptionsItemType[],
  targetInstance: CreateComponentType
) => {
  // 多选处理
  if (chartEditStore.getTargetChart.selectId.length > 1) {
    return targetList.filter(i => i.key === MenuEnum.GROUP)
  }
  const statusMenuEnums: MenuEnum[] = []
  // 处理锁定与隐藏
  if (targetInstance.status.lock) {
    statusMenuEnums.push(MenuEnum.LOCK)
  } else {
    statusMenuEnums.push(MenuEnum.UNLOCK)
  }
  if (targetInstance.status.hide) {
    statusMenuEnums.push(MenuEnum.HIDE)
  } else {
    statusMenuEnums.push(MenuEnum.SHOW)
  }
  return targetList.filter(item => !statusMenuEnums.includes(item.key as MenuEnum))
}

// 缩小
const backHandle = () => {
  chartLayoutStore.setItem(ChartLayoutStoreEnum.LAYERS, false)
}

// 移动结束处理
const onMoveCallback = (val: any) => {
  const { oldIndex, newIndex } = val.moved
  if (newIndex - oldIndex > 0) {
    // 从上往下
    const moveTarget = chartEditStore.getComponentList.splice(-(oldIndex + 1), 1)[0]
    chartEditStore.getComponentList.splice(-newIndex, 0, moveTarget)
  } else {
    // 从下往上
    const moveTarget = chartEditStore.getComponentList.splice(-(oldIndex + 1), 1)[0]
    if (newIndex === 0) {
      chartEditStore.getComponentList.push(moveTarget)
    } else {
      chartEditStore.getComponentList.splice(-newIndex, 0, moveTarget)
    }
  }
}

const boxMousedownHandle = (e: MouseEvent) => {
  const box = document.querySelector('.go-content-layer-box')
  if ((e.target as any).contains(box)) {
    chartEditStore.setTargetSelectChart()
  }
}

// 点击事件
const mousedownHandle = (e: MouseEvent, item: CreateComponentType) => {
  onClickOutSide()
  // 若此时按下了 CTRL, 表示多选
  const id = item.id
  if (e.buttons === MouseEventButton.LEFT && window.$KeyboardActive?.ctrl) {
    // 若已选中，则去除
    if (chartEditStore.targetChart.selectId.includes(id)) {
      const exList = chartEditStore.targetChart.selectId.filter(e => e !== id)
      chartEditStore.setTargetSelectChart(exList)
    } else {
      chartEditStore.setTargetSelectChart(id, true)
    }
    return
  }
  chartEditStore.setTargetSelectChart(id)
}

// 进入事件
const mouseenterHandle = (item: CreateComponentType) => {
  chartEditStore.setTargetHoverChart(item.id)
}

// 移出事件
const mouseleaveHandle = (item: CreateComponentType) => {
  chartEditStore.setTargetHoverChart(undefined)
}

// 修改图层展示方式
const changeLayerType = (value: LayerModeEnum) => {
  layerMode.value = value
  chartLayoutStore.setItem(ChartLayoutStoreEnum.LAYER_TYPE, value)
}
</script>

<style lang="scss" scoped>
$wight: 184px;
@include go('content-layers') {
  width: 220px;
  overflow: hidden;
  @extend .go-transition;
  border-radius: 10px;
  border-color: rgba(var(--app-theme-rgb), 0.1);
  background:
    linear-gradient(180deg, rgba(var(--app-theme-rgb), 0.07), transparent 166px),
    rgba(8, 13, 22, 0.42);

  .layers-shell {
    height: calc(100vh - #{$--header-height} - 24px);
    display: flex;
    flex-direction: column;
    min-height: 0;
  }

  .layers-toolbar {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 10px 10px 8px 12px;
    border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.08);
  }

  .layers-title {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;

    span {
      font-size: 9px;
      line-height: 1;
      letter-spacing: 0.8px;
      color: rgba(148, 163, 184, 0.75);
    }

    strong {
      font-size: 15px;
      line-height: 18px;
      color: rgba(226, 232, 240, 0.96);
      font-weight: 650;
    }
  }

  .layers-summary {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    color: rgba(148, 163, 184, 0.78);
    font-size: 11px;
    line-height: 1;
    background: rgba(2, 6, 23, 0.16);

    i {
      flex: 1;
      height: 1px;
      background: linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.24), transparent);
    }
  }

  .layers-body {
    flex: 1;
    min-height: 0;
    overflow: hidden;
    padding: 4px 4px 8px;
  }

  .not-layer-text {
    position: relative;
    top: 24px;
    white-space: nowrap;
    opacity: 0.4;
    font-size: 12px;
  }

  &.scoped {
    width: 0;
    border-width: 0;
  }

  .ghost {
    opacity: 0;
  }

  .go-layer-mode-active {
    color: #51d6a9;
  }

  :deep(.layers-mode-group) {
    display: flex;
    padding: 2px;
    border-radius: 8px;
    background: rgba(15, 23, 42, 0.38);
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
  }

  :deep(.n-button) {
    border-radius: 6px;
    height: 28px;
  }

  :deep(.go-content-layer-box) {
    margin: 6px 4px;
  }
}
</style>
