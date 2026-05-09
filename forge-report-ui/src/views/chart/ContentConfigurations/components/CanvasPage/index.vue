<template>
  <div class="go-canvas-setting">
    <div class="canvas-overview">
      <div>
        <span class="overview-label">画布尺寸</span>
        <strong>{{ canvasConfig.width }} x {{ canvasConfig.height }}</strong>
      </div>
      <n-button size="tiny" secondary @click="fitCanvasHandle">适配视图</n-button>
    </div>

    <section class="canvas-section size-section">
      <div class="section-title">
        <span>SIZE</span>
        <strong>基础尺寸</strong>
      </div>
      <div class="size-grid">
        <label>
          <span>宽度</span>
          <n-input-number
            size="small"
            v-model:value="canvasConfig.width"
            :disabled="editCanvas.lockScale"
            :validator="validator"
            @update:value="changeSizeHandle"
          ></n-input-number>
        </label>
        <label>
          <span>高度</span>
          <n-input-number
            size="small"
            v-model:value="canvasConfig.height"
            :disabled="editCanvas.lockScale"
            :validator="validator"
            @update:value="changeSizeHandle"
          ></n-input-number>
        </label>
      </div>
    </section>

    <section class="canvas-section background-section">
      <div class="section-title">
        <span>BACKGROUND</span>
        <strong>背景设置</strong>
      </div>
      <div class="background-layout">
        <div class="upload-box">
          <n-upload
            v-model:file-list="uploadFileListRef"
            :show-file-list="false"
            :customRequest="customRequest"
            :onBeforeUpload="beforeUploadHandle"
          >
            <n-upload-dragger>
              <img v-if="canvasConfig.backgroundImage" class="upload-show" :src="canvasConfig.backgroundImage" alt="背景" />
              <div class="upload-img" v-show="!canvasConfig.backgroundImage">
                <img src="@/assets/images/canvas/noImage.png" />
                <n-text class="upload-desc" depth="3">
                  png / jpg / gif，{{ backgroundImageSize }}M 内
                </n-text>
              </div>
            </n-upload-dragger>
          </n-upload>
        </div>
        <div class="background-controls">
          <label>
            <span>背景颜色</span>
            <div class="picker-height">
              <n-color-picker
                v-if="!switchSelectColorLoading"
                size="small"
                v-model:value="canvasConfig.background"
                :showPreview="true"
                :swatches="swatchesColors"
              ></n-color-picker>
            </div>
          </label>
          <label>
            <span>应用类型</span>
            <n-select
              size="small"
              v-model:value="selectColorValue"
              :disabled="!canvasConfig.backgroundImage"
              :options="selectColorOptions"
              @update:value="selectColorValueHandle"
            />
          </label>
          <div class="background-actions">
            <n-button class="clear-btn" size="small" :disabled="!canvasConfig.backgroundImage" @click="clearImage">
              清除背景
            </n-button>
            <n-button class="clear-btn" size="small" :disabled="!canvasConfig.background" @click="clearColor">
              清除颜色
            </n-button>
          </div>
        </div>
      </div>
    </section>

    <section class="canvas-section preview-section">
      <div class="section-title">
        <span>PREVIEW</span>
        <strong>适配方式</strong>
      </div>
      <div class="preview-type-grid">
        <button
          v-for="item in previewTypeList"
          :key="item.key"
          class="preview-type-card"
          :class="{ active: canvasConfig.previewScaleType === item.key }"
          type="button"
          @click="selectPreviewType(item.key)"
        >
          <n-icon size="18">
            <component :is="item.icon"></component>
          </n-icon>
          <span>{{ item.title }}</span>
        </button>
      </div>
    </section>

    <!-- 滤镜 -->
    <section class="canvas-section filter-section">
      <div class="section-title">
        <span>FILTER</span>
        <strong>滤镜效果</strong>
      </div>
      <styles-setting :isCanvas="true" :chartStyles="canvasConfig"></styles-setting>
    </section>

    <!-- 主题选择和全局配置 -->
    <n-tabs class="tabs-box" size="small" type="segment">
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
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { backgroundImageSize } from '@/settings/designSetting'
import { swatchesColors } from '@/settings/chartThemes/index'
import { FileTypeEnum } from '@/enums/fileTypeEnum'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasConfigEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { StylesSetting } from '@/components/Pages/ChartItemSetting'
import { UploadCustomRequestOptions } from 'naive-ui'
import { fileToUrl, loadAsyncComponent } from '@/utils'
import { PreviewScaleEnum } from '@/enums/styleEnum'
import { icon } from '@/plugins'

const { ColorPaletteIcon } = icon.ionicons5
const { ScaleIcon, FitToScreenIcon, FitToHeightIcon, FitToWidthIcon } = icon.carbon

const chartEditStore = useChartEditStore()
const canvasConfig = chartEditStore.getEditCanvasConfig
const editCanvas = chartEditStore.getEditCanvas

const uploadFileListRef = ref()
const switchSelectColorLoading = ref(false)
const selectColorValue = ref(0)

const ChartThemeColor = loadAsyncComponent(() => import('./components/ChartThemeColor/index.vue'))
const VChartThemeColor = loadAsyncComponent(() => import('./components/VChartThemeColor/index.vue'))

// 默认应用类型
const selectColorOptions = [
  {
    label: '应用颜色',
    value: 0
  },
  {
    label: '应用背景',
    value: 1
  }
]

const globalTabList = [
  {
    key: 'ChartTheme',
    title: '默认主题',
    icon: ColorPaletteIcon,
    render: ChartThemeColor
  },
  {
    key: 'VChartTheme',
    title: 'VChart主题',
    icon: ColorPaletteIcon,
    render: VChartThemeColor
  },
]

const previewTypeList = [
  {
    key: PreviewScaleEnum.FIT,
    title: '自适应',
    icon: ScaleIcon,
    desc: '自适应比例展示，页面会有留白'
  },
  {
    key: PreviewScaleEnum.SCROLL_Y,
    title: 'Y轴滚动',
    icon: FitToWidthIcon,
    desc: 'X轴铺满，Y轴自适应滚动'
  },
  {
    key: PreviewScaleEnum.SCROLL_X,
    title: 'X轴滚动',
    icon: FitToHeightIcon,
    desc: 'Y轴铺满，X轴自适应滚动'
  },
  {
    key: PreviewScaleEnum.FULL,
    title: '铺满',
    icon: FitToScreenIcon,
    desc: '强行拉伸画面，填充所有视图'
  }
]

watch(
  () => canvasConfig.selectColor,
  newValue => {
    selectColorValue.value = newValue ? 0 : 1
  },
  {
    immediate: true
  }
)

// 画布尺寸规则
const validator = (x: number) => x > 50

// 修改尺寸
const changeSizeHandle = () => {
  chartEditStore.computedScale()
}

// 上传图片前置处理
//@ts-ignore
const beforeUploadHandle = async ({ file }) => {
  uploadFileListRef.value = []
  const type = file.file.type
  const size = file.file.size

  if (size > 1024 * 1024 * backgroundImageSize) {
    window['$message'].warning(`图片超出 ${backgroundImageSize}M 限制，请重新上传！`)
    return false
  }
  if (type !== FileTypeEnum.PNG && type !== FileTypeEnum.JPEG && type !== FileTypeEnum.GIF) {
    window['$message'].warning('文件格式不符合，请重新上传！')
    return false
  }
  return true
}

// 应用颜色
const selectColorValueHandle = (value: number) => {
  canvasConfig.selectColor = value == 0
}

// 清除背景
const clearImage = () => {
  chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.BACKGROUND_IMAGE, undefined)
  chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.SELECT_COLOR, true)
}

// 启用/关闭 颜色（强制更新）
const switchSelectColorHandle = () => {
  switchSelectColorLoading.value = true
  setTimeout(() => {
    switchSelectColorLoading.value = false
  })
}

// 清除颜色
const clearColor = () => {
  chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.BACKGROUND, undefined)
  if (canvasConfig.backgroundImage) {
    chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.SELECT_COLOR, false)
  }
  switchSelectColorHandle()
}

// 自定义上传操作
const customRequest = (options: UploadCustomRequestOptions) => {
  const { file } = options
  nextTick(() => {
    if (file.file) {
      const ImageUrl = fileToUrl(file.file)
      chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.BACKGROUND_IMAGE, ImageUrl)
      chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.SELECT_COLOR, false)
    } else {
      window['$message'].error('添加图片失败，请稍后重试！')
    }
  })
}

// 选择适配方式
const selectPreviewType = (key: PreviewScaleEnum) => {
  chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.PREVIEW_SCALE_TYPE, key)
}

const fitCanvasHandle = () => {
  chartEditStore.computedScale()
}
</script>

<style lang="scss" scoped>
$uploadWidth: 326px;
$uploadHeight: 193px;

@include go(canvas-setting) {
  padding-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;

  .canvas-overview {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px;
    border-radius: 10px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.1);
    background:
      linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.08), transparent),
      rgba(15, 23, 42, 0.28);

    .overview-label {
      display: block;
      margin-bottom: 3px;
      font-size: 10px;
      letter-spacing: 1px;
      @include fetch-color(4);
    }

    strong {
      font-size: 17px;
      color: var(--app-theme, $--color-primary);
      font-family: 'Courier New', monospace;
      font-weight: 700;
      text-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.24);
    }
  }

  .canvas-section {
    padding: 14px;
    border-radius: 10px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    background: rgba(15, 23, 42, 0.16);

    .section-title {
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin-bottom: 14px;

      span {
        font-size: 9px;
        line-height: 1;
        letter-spacing: 1px;
        @include fetch-color(4);
      }

      strong {
        font-size: 14px;
        line-height: 17px;
        color: rgba(226, 232, 240, 0.92);
        font-weight: 650;
      }
    }
  }

  .size-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;

    label {
      display: flex;
      flex-direction: column;
      gap: 5px;

      > span {
        font-size: 11px;
        @include fetch-color(3);
        padding-left: 2px;
      }
    }
  }

  .background-layout {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .upload-box {
    cursor: pointer;
    @include deep() {
      .n-upload-dragger {
        padding: 5px;
        width: $uploadWidth;
        border-radius: 10px;
        border-color: rgba(var(--app-theme-rgb), 0.12);
        background:
          radial-gradient(circle at center, rgba(var(--app-theme-rgb), 0.06), transparent 66%),
          rgba(2, 6, 23, 0.14);
        transition: border-color 0.22s ease;

        &:hover {
          border-color: rgba(var(--app-theme-rgb), 0.24);
        }
      }
    }
    .upload-show {
      width: -webkit-fill-available;
      height: $uploadHeight;
      border-radius: 6px;
      object-fit: cover;
    }
    .upload-img {
      display: flex;
      flex-direction: column;
      align-items: center;
      img {
        height: 140px;
        opacity: 0.6;
      }
      .upload-desc {
        padding: 10px 0;
      }
    }
  }

  .background-controls {
    display: flex;
    flex-direction: column;
    gap: 10px;

    label {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;

      > span {
        font-size: 12px;
        @include fetch-color(3);
        white-space: nowrap;
        flex-shrink: 0;
      }

      .picker-height {
        min-height: 35px;
        min-width: 120px;
        display: flex;
        align-items: center;
        justify-content: flex-end;
      }
    }

    .background-actions {
      display: flex;
      gap: 8px;

      .clear-btn {
        flex: 1;
        height: 32px;
        border-radius: 8px;
        font-size: 12px;
        border: 1px solid rgba(var(--app-theme-rgb), 0.1);
        background: rgba(15, 23, 42, 0.28);
        color: rgba(203, 213, 225, 0.7);
        transition: all 0.2s ease;

        &:hover:not(:disabled) {
          border-color: rgba(var(--app-theme-rgb), 0.24);
          color: rgba(255, 255, 255, 0.9);
        }

        &:disabled {
          opacity: 0.35;
        }
      }
    }
  }

  .preview-type-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;

    .preview-type-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 12px 8px 10px;
      border-radius: 10px;
      border: 1px solid rgba(148, 163, 184, 0.1);
      background: rgba(15, 23, 42, 0.22);
      cursor: pointer;
      color: rgba(203, 213, 225, 0.64);
      transition: all 0.22s ease;

      span {
        font-size: 11px;
        line-height: 1;
      }

      &:hover {
        color: rgba(255, 255, 255, 0.88);
        border-color: rgba(var(--app-theme-rgb), 0.2);
        background: rgba(var(--app-theme-rgb), 0.06);
        transform: translateY(-1px);
      }

      &.active {
        color: #fff;
        border-color: rgba(var(--app-theme-rgb), 0.38);
        background:
          linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.22), rgba(var(--app-theme-rgb), 0.06)),
          rgba(15, 23, 42, 0.48);
        box-shadow: 0 6px 16px rgba(var(--app-theme-rgb), 0.1);
      }
    }
  }

  .icon-position {
    padding-top: 2px;
  }

  .tabs-box {
    margin-top: 4px;
    padding: 10px 2px 4px;
    border-radius: 10px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    background: rgba(15, 23, 42, 0.16);

    @include deep() {
      .n-tabs-nav {
        border-radius: 10px;
        padding: 2px 4px;
      }
    }
  }
}
</style>
