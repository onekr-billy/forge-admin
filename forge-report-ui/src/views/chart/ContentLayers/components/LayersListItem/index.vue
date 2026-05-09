<template>
  <div class="go-content-layers-list-item" :class="{ hover, select, 'list-mini': selectText }">
    <div class="go-flex-center item-content">
      <n-image
        class="list-img"
        object-fit="contain"
        preview-disabled
        :src="imageInfo"
        :fallback-src="requireErrorImg()"
      ></n-image>
      <n-ellipsis style="margin-right: auto">
        <span class="list-text">
          {{ props.componentData.chartConfig.title }}
        </span>
      </n-ellipsis>
      <layers-status :isGroup="isGroup" :hover="hover" :status="status"></layers-status>
    </div>
    <div :class="{ 'select-modal': select }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, PropType, ref } from 'vue'
import { requireErrorImg } from '@/utils'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { LayerModeEnum } from '@/store/modules/chartLayoutStore/chartLayoutStore.d'
import { fetchImages } from '@/packages'
import { CreateComponentType, CreateComponentGroupType } from '@/packages/index.d'
import { LayersStatus } from '../LayersStatus/index'

const props = defineProps({
  componentData: {
    type: Object as PropType<CreateComponentType | CreateComponentGroupType>,
    required: true
  },
  isGroup: {
    type: Boolean,
    default: false
  },
  layerMode: {
    type: String as PropType<LayerModeEnum>,
    default: LayerModeEnum.THUMBNAIL
  }
})

// 全局颜色
const designStore = useDesignStore()
const chartEditStore = useChartEditStore()
const imageInfo = ref('')

// 获取图片
const fetchImageUrl = async () => {
  imageInfo.value = await fetchImages(props.componentData.chartConfig)
}
fetchImageUrl()

// 颜色
const themeColor = computed(() => {
  return designStore.getAppTheme
})

// 计算当前选中目标
const select = computed(() => {
  const id = props.componentData.id
  return chartEditStore.getTargetChart.selectId.find((e: string) => e === id)
})

// 悬浮对象
const hover = computed(() => {
  return props.componentData.id === chartEditStore.getTargetChart.hoverId
})

// 组件状态 隐藏/锁定
const status = computed(() => {
  return props.componentData.status
})

// 是否选中文本
const selectText = computed(() => {
  return props.layerMode === LayerModeEnum.TEXT
})
</script>

<style lang="scss" scoped>
$centerHeight: 52px;
$centerMiniHeight: 34px;
$textSize: 11px;

@include go(content-layers-list-item) {
  position: relative;
  height: $centerHeight;
  width: calc(100% - 12px);
  margin: 6px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid rgba(148, 163, 184, 0.11);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.045), transparent 48%),
    rgba(15, 23, 42, 0.46);
  @extend .go-transition-quick;

  &.hover,
  &:hover {
    background:
      linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.14), transparent 56%),
      rgba(15, 23, 42, 0.54);
    border-color: rgba(var(--app-theme-rgb), 0.24);
    box-shadow:
      0 10px 22px rgba(0, 0, 0, 0.18),
      inset 0 0 0 1px rgba(var(--app-theme-rgb), 0.06);
  }
  &:hover {
    @include deep() {
      .icon-item {
        opacity: 1;
      }
    }
  }

  .select-modal,
  .item-content {
    position: absolute;
    top: 0;
    left: 0;
  }
  .item-content {
    z-index: 1;
    padding: 6px;
    justify-content: start !important;
    width: calc(100% - 12px);
    height: calc(100% - 12px);
  }

  .select-modal {
    width: 100%;
    height: 100%;
    opacity: 0.16;
    background:
      linear-gradient(90deg, v-bind('themeColor'), transparent),
      v-bind('themeColor');
    border-radius: 8px;
  }

  .list-img {
    flex-shrink: 0;
    width: 42px;
    height: 38px;
    border-radius: 6px;
    overflow: hidden;
    border: 1px solid rgba(var(--app-theme-rgb), 0.14) !important;
    padding: 2px;
    background: rgba(2, 6, 23, 0.42);
  }

  .list-text {
    padding-left: 8px;
    font-size: $textSize;
    color: rgba(226, 232, 240, 0.9);
    font-weight: 600;
  }

  /* 选中样式 */
  &.select {
    border: 1px solid v-bind('themeColor');
    background-color: rgba(0, 0, 0, 0);
    box-shadow:
      0 0 16px rgba(var(--app-theme-rgb), 0.16),
      inset 3px 0 0 v-bind('themeColor');
  }

  // mini样式
  &.list-mini {
    height: $centerMiniHeight;
  }
}
</style>
