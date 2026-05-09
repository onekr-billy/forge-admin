<template>
  <n-modal v-model:show="modelShowRef" @afterLeave="closeHandle">
    <div class="go-system-setting">
      <div class="setting-header">
        <div>
          <span class="setting-kicker">SYSTEM PREFERENCES</span>
          <h3>系统设置</h3>
          <p>调整工作台显示、工具栏和画布操作行为</p>
        </div>
        <button class="close-button" @click="closeHandle">
          <n-icon size="18">
            <close-icon></close-icon>
          </n-icon>
        </button>
      </div>

      <div class="setting-body">
        <!-- 分割线 -->
        <template v-for="item in list" :key="item.key">
          <div v-if="item.type === 'divider'" class="setting-section-divider">
            <span>{{ sectionTitle(item.key) }}</span>
          </div>

          <div v-else class="setting-row">
            <div class="setting-copy">
              <div class="setting-name-line">
                <span class="item-left">{{ item.name }}</span>
                <n-tooltip v-if="item.tip" trigger="hover">
                  <template #trigger>
                    <n-icon class="tip-icon" size="16">
                      <help-outline-icon></help-outline-icon>
                    </n-icon>
                  </template>
                  <span>{{ item.tip }}</span>
                </n-tooltip>
              </div>
              <p class="item-right">{{ item.desc }}</p>
            </div>
            <div class="setting-control">
            <!-- 数据操作 -->
            <template v-if="item.type === 'switch'">
              <n-switch
                v-model:value="item.value"
                size="small"
                @update:value="handleChange($event, item)"
              ></n-switch>
            </template>

            <template v-else-if="item.type === 'number'">
              <n-input-number
                v-model:value="item.value"
                class="input-num-width"
                size="small"
                :step="item.step || null"
                :suffix="item.suffix || null"
                :min="item.min || 0"
                @update:value="handleChange($event, item)"
              ></n-input-number>
            </template>

            <template v-else-if="item.type === 'select'">
              <n-select
                class="select-min-width"
                v-model:value="item.value"
                size="small"
                :options="item.options"
                @update:value="handleChange($event, item)"
              />
            </template>
            </div>
          </div>
        </template>
      </div>

      <div class="setting-footer">
        <span>设置会自动保存到本地偏好</span>
        <n-button size="small" type="primary" secondary @click="closeHandle">完成</n-button>
      </div>
    </div>
  </n-modal>
</template>

<script lang="ts" setup>
import { reactive, ref, watch } from 'vue'
import { ListType } from './index.d'
import { useSettingStore } from '@/store/modules/settingStore/settingStore'
import { SettingStoreEnums, ToolsStatusEnum } from '@/store/modules/settingStore/settingStore.d'
import { icon } from '@/plugins'

const props = defineProps({
  modelShow: Boolean
})

const emit = defineEmits(['update:modelShow'])
const { HelpOutlineIcon, CloseIcon } = icon.ionicons5
const settingStore = useSettingStore()
const modelShowRef = ref(false)

const list = reactive<ListType[]>([
  {
    key: SettingStoreEnums.ASIDE_ALL_COLLAPSED,
    value: settingStore.getAsideAllCollapsed,
    type: 'switch',
    name: '菜单折叠',
    desc: '首页菜单折叠时隐藏至界面外'
  },
  {
    key: SettingStoreEnums.HIDE_PACKAGE_ONE_CATEGORY,
    value: settingStore.getHidePackageOneCategory,
    type: 'switch',
    name: '隐藏分类',
    desc: '工作空间表单分类只有单项时隐藏'
  },
  {
    key: SettingStoreEnums.CHANGE_LANG_RELOAD,
    value: settingStore.getChangeLangReload,
    type: 'switch',
    name: '切换语言',
    desc: '切换语言重新加载页面',
    tip: '若遇到部分区域语言切换失败，则建议开启'
  },
  {
    key: 'divider1',
    type: 'divider',
    name: '',
    desc: '',
    value: ''
  },
  {
    key: SettingStoreEnums.CHART_TOOLS_STATUS_HIDE,
    value: settingStore.getChartToolsStatusHide,
    type: 'switch',
    name: '隐藏工具栏',
    desc: '鼠标移入时，会展示切换到展开模式',
  },
  {
    key: SettingStoreEnums.CHART_TOOLS_STATUS,
    value: settingStore.getChartToolsStatus,
    type: 'select',
    name: '工具栏展示',
    desc: '工作空间工具栏展示方式',
    options: [
      {
        label: '侧边栏',
        value: ToolsStatusEnum.ASIDE
      },
      {
        label: '底部 Dock',
        value: ToolsStatusEnum.DOCK
      }
    ]
  },
  {
    key: 'divider0',
    type: 'divider',
    name: '',
    desc: '',
    value: ''
  },
  {
    key: SettingStoreEnums.CHART_MOVE_DISTANCE,
    value: settingStore.getChartMoveDistance,
    type: 'number',
    name: '移动距离',
    min: 1,
    step: 1,
    suffix: 'px',
    desc: '工作空间方向键控制移动距离'
  },
  {
    key: SettingStoreEnums.CHART_ALIGN_RANGE,
    value: settingStore.getChartAlignRange,
    type: 'number',
    name: '吸附距离',
    min: 10,
    step: 2,
    suffix: 'px',
    desc: '工作空间移动图表时的吸附距离'
  }
])

watch(() => props.modelShow, (newValue) => {
  modelShowRef.value = newValue
})

const closeHandle = () => {
  emit('update:modelShow', false)
}

const handleChange = (e: MouseEvent, item: ListType) => {
  settingStore.setItem(item.key, item.value)
}

const sectionTitle = (key: string) => {
  if (key === 'divider1') return '工作台工具栏'
  if (key === 'divider0') return '画布操作'
  return '更多设置'
}
</script>

<style lang="scss" scoped>
@include go("system-setting") {
  width: min(680px, 74vw);
  max-height: 82vh;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid rgba(var(--app-theme-rgb), 0.16);
  background:
    radial-gradient(circle at 0 0, rgba(var(--app-theme-rgb), 0.14), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.035), transparent 120px),
    rgba(10, 14, 23, 0.96);
  box-shadow:
    0 32px 90px rgba(0, 0, 0, 0.52),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);

  .setting-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 24px;
    padding: 22px 24px 18px;
    border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.1);

    .setting-kicker {
      font-size: 10px;
      letter-spacing: 1.6px;
      @include fetch-color(4);
    }

    h3 {
      margin: 5px 0 0;
      font-size: 20px;
      color: var(--app-theme, $--color-primary);
      text-shadow: 0 0 12px rgba(var(--app-theme-rgb), 0.24);
    }

    p {
      margin: 8px 0 0;
      font-size: 12px;
      @include fetch-color(3);
    }
  }

  .close-button {
    width: 34px;
    height: 34px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.1);
    background: rgba(15, 23, 42, 0.36);
    color: rgba(226, 232, 240, 0.78);
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.28);
      color: var(--app-theme, $--color-primary);
      background: rgba(var(--app-theme-rgb), 0.1);
      box-shadow: 0 0 14px rgba(var(--app-theme-rgb), 0.16);
    }
  }

  .setting-body {
    max-height: calc(82vh - 150px);
    overflow: auto;
    padding: 14px;

    &::-webkit-scrollbar {
      width: 5px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: 999px;
      background: rgba(var(--app-theme-rgb), 0.22);
    }
  }

  .setting-section-divider {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 14px 4px 8px;

    &::before {
      content: '';
      width: 4px;
      height: 14px;
      border-radius: 999px;
      background: var(--app-theme, $--color-primary);
      box-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.42);
    }

    span {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1px;
      color: var(--app-theme, $--color-primary);
    }
  }

  .setting-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 160px;
    align-items: center;
    gap: 18px;
    margin-bottom: 10px;
    padding: 14px;
    border-radius: 12px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    background:
      linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.06), transparent),
      rgba(2, 6, 23, 0.24);
    transition: all 0.2s ease;

    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.18);
      background:
        linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.1), transparent),
        rgba(15, 23, 42, 0.3);
    }
  }

  .setting-copy {
    min-width: 0;
  }

  .setting-name-line {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .item-left {
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0.4px;
    @include fetch-color(1);
  }

  .tip-icon {
    color: rgba(var(--app-theme-rgb), 0.75);
  }

  .item-right {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.5;
    @include fetch-color(3);
  }

  .setting-control {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }

  .input-num-width {
    width: 120px;
  }

  .select-min-width {
    width: 136px;
  }

  .setting-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 20px;
    border-top: 1px solid rgba(var(--app-theme-rgb), 0.1);
    background: rgba(2, 6, 23, 0.24);

    span {
      font-size: 12px;
      @include fetch-color(4);
    }
  }

  @include deep() {
    .n-switch.n-switch--active .n-switch__rail {
      box-shadow: 0 0 14px rgba(var(--app-theme-rgb), 0.22);
    }

    .n-input,
    .n-input-number,
    .n-base-selection {
      border-radius: 9px;
      background: rgba(2, 6, 23, 0.22);
    }

    .n-base-selection-label,
    .n-input-wrapper {
      min-height: 32px;
    }
  }
}
</style>
