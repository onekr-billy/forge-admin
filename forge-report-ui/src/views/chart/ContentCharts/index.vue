<template>
  <!-- 左侧所有组件的展示列表 -->
  <content-box class="go-content-charts" title="组件库" :depth="1" :backIcon="false" :showTop="false">
    <template #icon>
      <n-icon size="14" :depth="2">
        <bar-chart-icon></bar-chart-icon>
      </n-icon>
    </template>

    <!-- 图表 -->
    <aside>
      <div class="library-shell" :class="{ 'ai-mode': isAIMode }">
        <div class="library-toolbar">
          <div class="library-title">
            <span>ASSET</span>
            <strong>{{ isAIMode ? 'AI 助手' : '组件素材' }}</strong>
          </div>

          <div class="library-actions">
            <template v-if="isAIMode">
              <n-tooltip placement="bottom" trigger="hover">
                <template #trigger>
                  <n-button class="library-action-btn" size="small" quaternary @click="clearAIChat">
                    <template #icon>
                      <n-icon size="15"><trash-icon /></n-icon>
                    </template>
                  </n-button>
                </template>
                清空对话
              </n-tooltip>
              <n-tooltip placement="bottom" trigger="hover">
                <template #trigger>
                  <n-button class="library-action-btn" size="small" quaternary @click="closeAIPanel">
                    <template #icon>
                      <n-icon size="15"><close-icon /></n-icon>
                    </template>
                  </n-button>
                </template>
                关闭AI助手
              </n-tooltip>
            </template>
          </div>
        </div>

        <div v-if="!isAIMode" class="library-search-row">
          <charts-search :menuOptions="menuOptions"></charts-search>
        </div>

        <div v-if="!isAIMode" class="package-strip">
          <div class="package-strip-scroll">
            <button
              v-for="item in menuOptions"
              :key="item.key"
              class="package-pill"
              :class="{ active: selectValue === item.key }"
              type="button"
              @click="selectPackage(item)"
            >
              <span class="package-icon">
                <component :is="item.icon"></component>
              </span>
              <span class="package-label">{{ item.label }}</span>
            </button>
          </div>
          <n-popover trigger="click" :show-arrow="false" placement="bottom-end" :to="false">
            <template #trigger>
              <button class="strip-expand-btn" type="button">
                <n-icon size="18"><chevron-down-icon /></n-icon>
              </button>
            </template>
            <div class="strip-popover-menu">
              <div
                v-for="item in menuOptions"
                :key="item.key"
                class="strip-popover-item"
                :class="{ active: selectValue === item.key }"
                @click="selectPackage(item)"
              >
                <span class="strip-popover-item-icon"><component :is="item.icon" /></span>
                <span class="strip-popover-item-label">{{ item.label }}</span>
              </div>
            </div>
          </n-popover>
        </div>

        <div class="library-body">
          <!-- AI 模式 -->
          <AIChatPanel v-if="isAIMode" />
          <!-- 普通模式 -->
          <template v-else>
            <fg-skeleton :load="!selectOptions" round text :repeat="2" style="width: 90%"></fg-skeleton>
            <charts-option-content
              v-if="selectOptions"
              :selectOptions="selectOptions"
              :key="selectValue"
            ></charts-option-content>
          </template>
        </div>
      </div>
    </aside>
  </content-box>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ContentBox } from '../ContentBox/index'
import { ChartsOptionContent } from './components/ChartsOptionContent'
import { ChartsSearch } from './components/ChartsSearch'
import { useAsideHook } from './hooks/useAside.hook'
import { useAIStore } from '@/store/modules/aiStore/aiStore'
import { icon } from '@/plugins'
import AIChatPanel from '@/components/FgAI/AIChatPanel.vue'

const { TrashIcon, CloseIcon, ChevronDownIcon } = icon.ionicons5

const { BarChartIcon, selectOptions, selectValue, clickItemHandle, menuOptions } = useAsideHook()
const aiStore = useAIStore()
const isAIMode = computed(() => aiStore.getAIPanelVisible)

function closeAIPanel() {
  aiStore.setAIPanelVisible(false)
}

function clearAIChat() {
  aiStore.clearChat()
}

function selectPackage(item: any) {
  selectValue.value = String(item.key)
  clickItemHandle(item.key, item)
}
</script>

<style lang="scss" scoped>
/* 整体宽度 */
$width: 376px;
/* 列表的宽度 */
$widthScoped: 76px;
/* 此高度与 ContentBox 组件关联 */
$topHeight: 0px;
$workbenchGapHeight: 24px;

@include go(content-charts) {
  width: $width;
  @extend .go-transition;
  border-radius: 10px;

  .library-shell {
    width: $width;
    height: calc(100vh - #{$--header-height} - #{$workbenchGapHeight} - #{$topHeight});
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background:
      linear-gradient(180deg, rgba(var(--app-theme-rgb), 0.08), transparent 172px),
      rgba(8, 13, 22, 0.34);

    .library-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      padding: 10px 12px 8px;
      border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.08);
    }

    .library-title {
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

    .library-actions {
      display: flex;
      align-items: center;
      gap: 4px;

      :deep(.library-action-btn) {
        width: 30px;
        height: 30px;
        border-radius: 8px;
        background: rgba(255, 255, 255, 0.04);
        border: 1px solid rgba(255, 255, 255, 0.06);
      }
    }

    .library-search-row {
      padding: 8px 12px 6px;
      :deep(.chart-search) {
        width: 100%;
        max-width: none;
      }
      :deep(.chart-search:focus-within) {
        width: 100%;
      }
    }

    .package-strip {
      position: relative;
      border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.08);

      &::after {
        content: '';
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        width: 48px;
        pointer-events: none;
        z-index: 2;
        background: linear-gradient(to right, transparent 0%, rgba(8, 13, 22, 0.96) 60%);
      }

      .strip-expand-btn {
        position: absolute;
        top: 50%;
        right: 4px;
        transform: translateY(-50%);
        z-index: 3;
        width: 20px;
        height: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        border: 1px solid rgba(148, 163, 184, 0.16);
        border-radius: 4px;
        background: rgba(15, 23, 42, 0.64);
        color: rgba(203, 213, 225, 0.78);
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover {
          color: #fff;
          border-color: rgba(var(--app-theme-rgb), 0.3);
          background: rgba(var(--app-theme-rgb), 0.12);
        }
      }

      .package-strip-scroll {
        display: flex;
        gap: 7px;
        padding: 4px 34px 10px 12px;
        overflow-x: auto;
        overflow-y: hidden;
        scrollbar-width: none;
        cursor: grab;

        &::-webkit-scrollbar {
          display: none;
        }
      }

      .package-pill {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        flex: 0 0 auto;
        height: 34px;
        padding: 0 11px 0 9px;
        border: 1px solid rgba(148, 163, 184, 0.12);
        border-radius: 8px;
        cursor: pointer;
        color: rgba(203, 213, 225, 0.74);
        background: rgba(15, 23, 42, 0.44);
        transition: all 0.22s ease;

        .package-icon {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 18px;
          height: 18px;
          font-size: 16px;
        }

        .package-label {
          max-width: 72px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          font-size: 12px;
          line-height: 1;
        }

        &:hover {
          color: rgba(255, 255, 255, 0.94);
          border-color: rgba(var(--app-theme-rgb), 0.24);
          transform: translateY(-1px);
        }

        &.active {
          color: #fff;
          border-color: rgba(var(--app-theme-rgb), 0.42);
          background:
            linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.28), rgba(var(--app-theme-rgb), 0.08)),
            rgba(15, 23, 42, 0.64);
          box-shadow: 0 8px 18px rgba(var(--app-theme-rgb), 0.1);
        }
      }
    }

    .library-body {
      flex: 1;
      min-height: 0;
      overflow: hidden;
      background: rgba(2, 6, 23, 0.14);
    }
  }

}
</style>

<style lang="scss">
.strip-popover-menu {
  min-width: 140px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;

  .strip-popover-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 10px;
    border-radius: 8px;
    cursor: pointer;
    color: rgba(203, 213, 225, 0.82);
    font-size: 13px;
    transition: all 0.18s ease;

    .strip-popover-item-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 18px;
      height: 18px;
      font-size: 16px;
      opacity: 0.7;
    }

    .strip-popover-item-label {
      white-space: nowrap;
    }

    &:hover {
      color: #fff;
      background: rgba(var(--app-theme-rgb), 0.12);
    }

    &.active {
      color: #fff;
      background: rgba(var(--app-theme-rgb), 0.2);

      .strip-popover-item-icon {
        opacity: 1;
      }
    }
  }
}
</style>
