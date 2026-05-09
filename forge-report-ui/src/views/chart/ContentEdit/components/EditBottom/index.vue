<template>
  <div class="go-edit-bottom">
    <div class="bottom-cluster history-cluster">
      <span class="bottom-label">记录</span>
      <edit-history></edit-history>
      <n-text id="keyboard-dress-show" class="key-dress" depth="3"></n-text>
    </div>

    <div class="bottom-status">
      <span class="status-dot"></span>
      <span>编辑中</span>
    </div>

    <div class="bottom-cluster scale-cluster">
      <edit-shortcut-key />

      <div class="scale-select-wrap">
        <span class="bottom-label">缩放</span>
        <n-select
          ref="selectInstRef"
          class="scale-btn"
          v-model:value="filterValue"
          size="mini"
          :disabled="lockScale"
          :options="filterOptions"
          @update:value="selectHandle"
        ></n-select>
      </div>

      <n-slider
        class="scale-slider"
        v-model:value="sliderValue"
        :default-value="50"
        :min="10"
        :max="200"
        :step="5"
        :format-tooltip="sliderFormatTooltip"
        :disabled="lockScale"
        :marks="sliderMaks"
        @update:value="sliderHandle"
      ></n-slider>

      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button class="scale-lock-btn" @click="lockHandle" quaternary>
            <n-icon class="lock-icon" :class="{ color: lockScale }" size="18" :depth="2">
              <lock-closed-outline-icon v-if="lockScale"></lock-closed-outline-icon>
              <lock-open-outline-icon v-else></lock-open-outline-icon>
            </n-icon>
          </n-button>
        </template>
        <span>{{ lockScale ? '解锁' : '锁定' }}当前比例</span>
      </n-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SelectInst } from 'naive-ui'
import { reactive, ref, toRefs, watchEffect } from 'vue'
import { icon } from '@/plugins'
import { EditHistory } from '../EditHistory/index'
import EditShortcutKey from '../EditShortcutKey/index.vue'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasTypeEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { useChartLayoutStore } from '@/store/modules/chartLayoutStore/chartLayoutStore'
import { ChartLayoutStoreEnum } from '@/store/modules/chartLayoutStore/chartLayoutStore.d'

const { LockClosedOutlineIcon, LockOpenOutlineIcon } = icon.ionicons5

// 全局颜色
const designStore = useDesignStore()
const themeColor = ref(designStore.getAppTheme)
const chartLayoutStore = useChartLayoutStore()
const chartEditStore = useChartEditStore()
const { lockScale, scale } = toRefs(chartEditStore.getEditCanvas)
const selectInstRef = ref<SelectInst | null>(null)

// 缩放选项
let filterOptions = [
  {
    label: '200%',
    value: 200
  },
  {
    label: '150%',
    value: 150
  },
  {
    label: '100%',
    value: 100
  },
  {
    label: '50%',
    value: 50
  },
  {
    label: '自适应',
    value: 0
  }
]

// 选择值
const filterValue = ref('')

// 用户自选择
const selectHandle = (v: number) => {
  selectInstRef.value?.blur()
  if (v === 0) {
    chartLayoutStore.setItemUnHandle(ChartLayoutStoreEnum.RE_POSITION_CANVAS, true)
    chartEditStore.computedScale()
    return
  }
  chartEditStore.setScale(v / 100)
}

// 点击锁处理
const lockHandle = () => {
  chartEditStore.setEditCanvas(EditCanvasTypeEnum.LOCK_SCALE, !lockScale.value)
}

// 拖动
const sliderValue = ref(100)

// 拖动格式化
const sliderFormatTooltip = (v: string) => `${v}%`

// 拖动处理
const sliderHandle = (v: number) => {
  chartEditStore.setScale(v / 100)
}

const sliderMaks = reactive({
  100: ''
})

// 监听 scale 变化
watchEffect(() => {
  const value = (scale.value * 100).toFixed(0)
  filterValue.value = `${value}%`
  sliderValue.value = parseInt(value)
})
</script>

<style lang="scss" scoped>
$min-width: 500px;
$max-width: 670px;
@include go('edit-bottom') {
  width: 100%;
  min-width: $min-width;
  min-width: $max-width;
  height: 100%;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background:
    linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.08), transparent 36%, transparent 64%, rgba(167, 139, 250, 0.06)),
    rgba(10, 14, 23, 0.2);

  .bottom-cluster {
    height: 32px;
    display: flex;
    align-items: center !important;
    gap: 8px;
    padding: 0 10px;
    border-radius: 999px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.1);
    background: rgba(2, 6, 23, 0.28);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.025);
  }

  .bottom-label {
    font-size: 10px;
    letter-spacing: 1px;
    @include fetch-color(4);
  }

  .history-cluster {
    min-width: 210px;
    justify-content: flex-start;
  }

  .key-dress {
    min-width: 34px;
    font-size: 11px;
    color: var(--app-theme, $--color-primary);
  }

  .bottom-status {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 7px;
    height: 28px;
    padding: 0 12px;
    border-radius: 999px;
    border: 1px solid rgba(46, 213, 115, 0.14);
    background: rgba(46, 213, 115, 0.06);
    color: $--color-success;
    font-size: 11px;
    letter-spacing: 0.4px;
  }

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $--color-success;
    box-shadow: 0 0 8px rgba(46, 213, 115, 0.6);
  }

  .scale-cluster {
    min-width: 360px;
    justify-content: flex-end;
    align-items: center !important;
  }

  .scale-select-wrap {
    display: flex;
    align-items: center;
    gap: 7px;
  }

  .lock-icon {
    &.color {
      color: v-bind('themeColor');
    }
  }

  .scale-btn {
    width: 88px;
    font-size: 12px;
    @include deep() {
      .n-base-selection-label {
        padding: 3px 8px;
      }
    }
  }

  .scale-slider {
    width: 136px;
    height: 18px;
    display: flex;
    align-items: center;
    align-self: center;
    margin: 0 2px;
    transform: translateY(0);

    :deep(.n-slider) {
      height: 18px;
      display: flex;
      align-items: center;
    }

    :deep(.n-slider-rail) {
      height: 4px;
      top: auto;
    }

    :deep(.n-slider-handle) {
      width: 14px;
      height: 14px;
      margin-top: 0;
      border-width: 2px;
      box-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.24);
    }

    :deep(.n-slider-handle-wrapper) {
      top: 50%;
      transform: translate(-50%, -50%);
    }
  }

  .scale-lock-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 999px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.1);
    background: rgba(15, 23, 42, 0.32);
    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.28);
      background: rgba(var(--app-theme-rgb), 0.1);
      box-shadow: 0 0 12px rgba(var(--app-theme-rgb), 0.16);
    }
    :deep(.n-button__icon) {
      margin: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    :deep(.n-button__content),
    :deep(.n-icon) {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
    }

    :deep(.n-icon) {
      margin: 0;
    }

    .btn-text {
      font-size: 12px;
      margin-right: 3px;
    }
  }

  :deep(.n-button) {
    border-radius: 999px;
  }

  :deep(.n-base-selection) {
    --n-border-radius: 8px !important;
  }
}
</style>
