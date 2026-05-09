<template>
  <div class="go-content-charts-item-animation-patch">
    <div
      ref="contentChartsItemBoxRef"
      class="go-content-charts-item-box"
      :class="[chartMode === ChartModeEnum.DOUBLE ? 'double' : 'single']"
    >
      <!-- 每一项组件的渲染 -->
      <div
        class="item-box"
        v-for="(item, index) in menuOptions"
        :key="item.title"
        draggable="true"
        @dragstart.capture="!item.disabled && dragStartHandle($event, item)"
        @dragend="!item.disabled && dragendHandle()"
        @dblclick="dblclickHandle(item)"
        @click="clickHandle(item)"
      >
        <span class="asset-index">{{ index + 1 }}</span>
        <div class="list-center go-flex-center go-transition" draggable="false">
          <FgIconify v-if="item.icon" class="list-img" :icon="item.icon" color="#999" width="48" style="height: auto" />
          <chart-glob-image v-else class="list-img" :chartConfig="item" />
        </div>
        <div class="asset-meta">
          <n-text class="asset-title" depth="2">
            <n-ellipsis>{{ item.title }}</n-ellipsis>
          </n-text>
          <span class="asset-hint">{{ item.disabled ? '开发中' : '拖拽 / 双击' }}</span>
        </div>
        <!-- 遮罩 -->
        <div v-if="item.disabled" class="list-model"></div>
        <!-- 工具栏 -->
        <div v-if="isShowTools(item)" class="list-tools go-transition" @click="deleteHandle(item, index)">
          <n-button text type="default" color="#ffffff">
            <template #icon>
              <n-icon>
                <TrashIcon />
              </n-icon>
            </template>
            <span>删除</span>
          </n-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { PropType, watch, ref, Ref, computed, nextTick } from 'vue'
import { ChartGlobImage } from '@/components/Pages/ChartGlobImage'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasTypeEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { ChartModeEnum } from '@/store/modules/chartLayoutStore/chartLayoutStore.d'
import { useChartLayoutStore } from '@/store/modules/chartLayoutStore/chartLayoutStore'
import { usePackagesStore } from '@/store/modules/packagesStore/packagesStore'
import { componentInstall, loadingStart, loadingFinish, loadingError, JSONStringify, goDialog } from '@/utils'
import { DragKeyEnum } from '@/enums/editPageEnum'
import { createComponent } from '@/packages'
import { ConfigType, CreateComponentType, PackagesCategoryEnum } from '@/packages/index.d'
import { ChatCategoryEnum } from '@/packages/components/Photos/index.d'
import { fetchConfigComponent, fetchChartComponent } from '@/packages/index'
import { FgIconify } from '@/components/FgIconify'
import { icon } from '@/plugins'

import omit from 'lodash/omit'

const chartEditStore = useChartEditStore()
const { TrashIcon } = icon.ionicons5

const emit = defineEmits(['deletePhoto'])
const props = defineProps({
  menuOptions: {
    type: Array as PropType<ConfigType[]>,
    default: () => []
  }
})

const chartLayoutStore = useChartLayoutStore()
const contentChartsItemBoxRef = ref()

// 判断工具栏展示
const isShowTools = (item: ConfigType) => {
  return !item.disabled && item.package === PackagesCategoryEnum.PHOTOS && item.category === ChatCategoryEnum.PRIVATE
}

// 组件展示状态
const chartMode: Ref<ChartModeEnum> = computed(() => {
  return chartLayoutStore.getChartType
})

// 拖拽处理
const dragStartHandle = (e: DragEvent, item: ConfigType) => {
  if (item.disabled) return
  // 动态注册图表组件
  componentInstall(item.chartKey, fetchChartComponent(item))
  componentInstall(item.conKey, fetchConfigComponent(item))
  // 将配置项绑定到拖拽属性上
  e!.dataTransfer!.setData(DragKeyEnum.DRAG_KEY, JSONStringify(omit(item, ['image'])))
  // 修改状态
  chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_CREATE, true)
}

// 拖拽结束
const dragendHandle = () => {
  chartEditStore.setEditCanvas(EditCanvasTypeEnum.IS_CREATE, false)
}

// 双击添加
const dblclickHandle = async (item: ConfigType) => {
  if (item.disabled) return
  try {
    loadingStart()
    // 动态注册图表组件
    componentInstall(item.chartKey, fetchChartComponent(item))
    componentInstall(item.conKey, fetchConfigComponent(item))
    // 创建新图表组件
    let newComponent: CreateComponentType = await createComponent(item)
    if (item.redirectComponent) {
      item.dataset && (newComponent.option.dataset = item.dataset)
      newComponent.chartConfig.title = item.title
      newComponent.chartConfig.chartFrame = item.chartFrame
    }
    // 添加
    chartEditStore.addComponentList(newComponent, false, true)
    // 选中
    chartEditStore.setTargetSelectChart(newComponent.id)
    loadingFinish()
  } catch (error) {
    loadingError()
    window['$message'].warning(`图表正在研发中, 敬请期待...`)
  }
}

// 单击事件
const clickHandle = (item: ConfigType) => {
  item?.configEvents?.addHandle(item)
}

const deleteHandle = (item: ConfigType, index: number) => {
  goDialog({
    message: '是否删除此图片？',
    transformOrigin: 'center',
    onPositiveCallback: () => {
      const packagesStore = usePackagesStore()
      emit('deletePhoto', item, index)
      packagesStore.deletePhotos(item, index)
    }
  })
}

watch(
  () => chartMode.value,
  (newValue: ChartModeEnum) => {
    if (newValue === ChartModeEnum.DOUBLE) {
      nextTick(() => {
        contentChartsItemBoxRef.value.classList.add('miniAnimation')
      })
    }
  }
)
</script>

<style lang="scss" scoped>
/* 列表项宽度 */
$itemWidth: 100%;
$maxItemWidth: 170px;
$halfItemWidth: calc(50% - 5px);
/* 内容高度 */
$centerHeight: 78px;
$halfCenterHeight: 74px;

@include go('content-charts-item-animation-patch') {
  padding: 12px;
}

@include go('content-charts-item-box') {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  transition: all 0.7s linear;
  .item-box {
    position: relative;
    margin: 0;
    width: $itemWidth;
    overflow: hidden;
    border-radius: 8px;
    cursor: pointer;
    border: 1px solid rgba(148, 163, 184, 0.12);
    background:
      linear-gradient(135deg, rgba(255, 255, 255, 0.05), transparent 44%),
      rgba(15, 23, 42, 0.5);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
    min-height: 108px;
    transition:
      transform 0.2s ease,
      border-color 0.2s ease,
      box-shadow 0.2s ease,
      background 0.2s ease;

    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.28);
      box-shadow:
        0 14px 26px rgba(0, 0, 0, 0.24),
        0 0 16px rgba(var(--app-theme-rgb), 0.1);
      transform: translateY(-2px);
      .list-img {
        transform: scale(1.08);
      }
      .asset-hint {
        color: rgba(var(--app-theme-rgb), 0.95);
      }
      .list-tools {
        opacity: 1;
      }
    }

    .asset-index {
      position: absolute;
      top: 8px;
      right: 8px;
      z-index: 1;
      min-width: 22px;
      height: 20px;
      padding: 0 5px;
      border-radius: 6px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 10px;
      line-height: 20px;
      color: rgba(226, 232, 240, 0.76);
      background: rgba(2, 6, 23, 0.46);
      border: 1px solid rgba(255, 255, 255, 0.06);
    }

    .list-center {
      padding: 10px 0;
      height: $centerHeight;
      overflow: hidden;
      pointer-events: none;
      background:
        radial-gradient(circle at center, rgba(var(--app-theme-rgb), 0.08), transparent 62%),
        rgba(2, 6, 23, 0.16);

      :deep(img),
      :deep(svg),
      :deep(canvas) {
        pointer-events: none;
        user-select: none;
        -webkit-user-drag: none;
      }

      .list-img {
        height: 70px;
        max-width: 128px;
        border-radius: 6px;
        object-fit: contain;
        @extend .go-transition;
      }
    }

    .asset-meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 9px 10px 10px;
      min-width: 0;

      .asset-title {
        flex: 1;
        min-width: 0;
        font-size: 12px;
        line-height: 16px;
        font-weight: 600;
        color: rgba(226, 232, 240, 0.92);
      }

      .asset-hint {
        flex-shrink: 0;
        font-size: 10px;
        line-height: 16px;
        color: rgba(148, 163, 184, 0.72);
        transition: color 0.2s ease;
      }
    }

    .list-model {
      z-index: 1;
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background-color: rgba(0, 0, 0, 0);
    }
    .list-tools {
      position: absolute;
      display: flex;
      justify-content: center;
      align-items: center;
      bottom: 0;
      left: 0;
      margin: 0 4px 2px;
      height: 26px;
      width: calc(100% - 8px);
      opacity: 0;
      border-radius: 6px;
      backdrop-filter: blur(20px);
      background-color: rgba(255, 255, 255, 0.15);
      border: 1px solid rgba(255, 255, 255, 0.08);
      &:hover {
        background-color: rgba(232, 128, 128, 0.7);
      }
    }
  }
  &.single {
    .item-box {
      @extend .go-transition;
      display: grid;
      grid-template-columns: 94px minmax(0, 1fr);
      align-items: center;
      min-height: 82px;
      background:
        linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.08), transparent 44%),
        rgba(15, 23, 42, 0.46);
    }

    .list-center {
      height: 82px;
      padding: 0;
      background: rgba(2, 6, 23, 0.22);

      .list-img {
        height: 58px;
        max-width: 76px;
      }
    }

    .asset-meta {
      flex-direction: column;
      align-items: flex-start;
      justify-content: center;
      padding: 0 34px 0 10px;
      gap: 3px;
    }
  }
  &.double {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: stretch;
    gap: 10px;

    .item-box {
      width: 100%;
      max-width: none;
      .list-img {
        max-width: 76px;
      }
    }
    .list-center {
      height: $halfCenterHeight;
      padding: 8px 0 0;
      .list-img {
        height: 56px;
        width: auto;
        transition: all 0.2s;
        object-fit: contain;
      }
    }

    .asset-meta {
      display: block;
      padding: 8px 9px 10px;

      .asset-title {
        display: block;
      }

      .asset-hint {
        display: block;
        margin-top: 2px;
      }
    }
  }
  /* 缩小展示 */
  @keyframes miniAnimation {
    from {
      width: $itemWidth * 2;
    }
    to {
      width: $itemWidth;
    }
  }
  &.miniAnimation {
    animation: miniAnimation 0.5s;
  }
}
</style>
