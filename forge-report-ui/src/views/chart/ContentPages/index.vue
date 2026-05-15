<template>
  <content-box
    class="go-content-pages"
    title="页面"
    :depth="2"
    :showTop="false"
    :disabledScroll="true"
  >
    <div class="pages-shell">
      <div class="pages-toolbar">
        <div class="pages-title">
          <span>PAGES</span>
          <strong>画布页面</strong>
        </div>
        <n-tooltip :show-arrow="false" trigger="hover">
          <template #trigger>
            <n-button type="primary" size="small" circle secondary @click="handleCreatePage">
              <template #icon>
                <n-icon size="16">
                  <add-icon />
                </n-icon>
              </template>
            </n-button>
          </template>
          新增页面
        </n-tooltip>
      </div>

      <div class="pages-summary">
        <span>{{ pages.length }} 个页面</span>
        <i></i>
      </div>

      <div class="pages-body">
        <n-scrollbar>
          <page-list-item
            v-for="(page, index) in pages"
            :key="page.id"
            :page="page"
            :index="index"
            :active="page.id === chartEditStore.getActivePageId"
            :home="page.id === chartEditStore.getHomePageId"
            :only-one="pages.length <= 1"
            :first="index === 0"
            :last="index === pages.length - 1"
            @select="handleSwitchPage(page.id)"
            @rename="handleRenamePage(page.id, $event)"
            @duplicate="handleDuplicatePage(page.id)"
            @delete="handleDeletePage(page.id)"
            @set-home="handleSetHome(page.id)"
            @move-up="handleMovePage(page.id, 'up')"
            @move-down="handleMovePage(page.id, 'down')"
          />
        </n-scrollbar>
      </div>
    </div>
  </content-box>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ContentBox } from '../ContentBox/index'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { useSync } from '@/views/chart/hooks/useSync.hook'
import { icon } from '@/plugins'
import PageListItem from './components/PageListItem.vue'
import type { ChartEditStorage } from '@/store/modules/chartEditStore/chartEditStore.d'

const { AddIcon } = icon.ionicons5
const chartEditStore = useChartEditStore()
const { updateComponent } = useSync()

const pages = computed(() => {
  return chartEditStore.getProjectPages
    .map(page => {
      if (page.id !== chartEditStore.getActivePageId) return page
      return {
        ...page,
        componentList: chartEditStore.getComponentList
      }
    })
    .sort((a, b) => (a.sort || 0) - (b.sort || 0))
})

const renderPageStorage = async (storage?: ChartEditStorage) => {
  if (!storage) return
  await updateComponent(storage, true)
  if (chartEditStore.getEditCanvas.editLayoutDom) {
    chartEditStore.computedScale()
  }
}

const handleCreatePage = async () => {
  chartEditStore.createPage(`页面 ${pages.value.length + 1}`)
  await renderPageStorage(chartEditStore.getStorageInfo())
}

const handleSwitchPage = async (pageId: string) => {
  if (pageId === chartEditStore.getActivePageId) return
  await renderPageStorage(chartEditStore.switchPage(pageId))
}

const handleRenamePage = (pageId: string, name: string) => {
  chartEditStore.renamePage(pageId, name)
}

const handleDuplicatePage = async (pageId: string) => {
  chartEditStore.duplicatePage(pageId)
  await renderPageStorage(chartEditStore.getStorageInfo())
}

const handleDeletePage = async (pageId: string) => {
  if (pages.value.length <= 1) return
  chartEditStore.deletePage(pageId)
  await renderPageStorage(chartEditStore.getStorageInfo())
}

const handleSetHome = (pageId: string) => {
  chartEditStore.setHomePage(pageId)
  window['$message']?.success('已设为首页')
}

const handleMovePage = (pageId: string, direction: 'up' | 'down') => {
  chartEditStore.movePage(pageId, direction)
}
</script>

<style lang="scss" scoped>
@include go('content-pages') {
  width: 212px;
  overflow: hidden;
  border-radius: 10px;
  border-color: rgba(var(--app-theme-rgb), 0.1);
  background:
    linear-gradient(180deg, rgba(var(--app-theme-rgb), 0.08), transparent 154px),
    rgba(8, 13, 22, 0.44);

  .pages-shell {
    height: calc(100vh - #{$--header-height} - 24px);
    display: flex;
    flex-direction: column;
    min-height: 0;
  }

  .pages-toolbar {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 10px 10px 8px 12px;
    border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.08);
  }

  .pages-title {
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

  .pages-summary {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 12px 3px;
    color: rgba(148, 163, 184, 0.72);
    font-size: 11px;

    i {
      flex: 1;
      height: 1px;
      background: linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.24), transparent);
    }
  }

  .pages-body {
    flex: 1;
    min-height: 0;
    padding-bottom: 8px;
  }
}
</style>
