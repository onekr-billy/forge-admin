<template>
  <div class="go-project-template-market-page">
    <div class="page-header go-float-up">
      <div>
        <h2 class="page-title go-text-neon">
          <span class="title-accent">//</span>
          模板市场
        </h2>
        <p class="page-subtitle">这里只展示公开模板，所有人都可查看并复制为新项目</p>
      </div>
      <div class="toolbar-actions">
        <n-input
          v-model:value="filters.templateName"
          clearable
          placeholder="按模板名称检索"
          class="search-input"
          @keydown.enter.prevent="handleSearch"
        >
          <template #prefix>
            <n-icon><SearchIcon /></n-icon>
          </template>
        </n-input>
        <n-button @click="handleResetSearch">重置</n-button>
        <n-button @click="loadTemplateList">
          <template #icon>
            <n-icon><ArrowRedoIcon /></n-icon>
          </template>
          刷新
        </n-button>
      </div>
    </div>

    <div v-if="loadingTemplates" class="content-loading">
      <n-spin size="large">
        <template #description>
          <span style="color: #64748b; margin-top: 12px; display: block;">正在加载模板市场...</span>
        </template>
      </n-spin>
    </div>

    <template v-else>
      <n-grid
        v-if="templateCards.length"
        :x-gap="20"
        :y-gap="20"
        cols="1 s:2 m:2 l:3 xl:4 xxl:4"
        responsive="screen"
      >
        <n-grid-item v-for="(item, index) in templateCards" :key="item.id">
          <div class="go-float-up" :style="{ animationDelay: `${index * 0.05}s` }">
            <div class="template-card">
              <div class="card-image" @click="resizeHandle(item)">
                <n-image
                  object-fit="contain"
                  height="170"
                  preview-disabled
                  :src="item.imageSrc"
                  :alt="item.title"
                  :fallback-src="errorImg"
                  class="!w-full"
                />
                <div class="card-actions">
                  <n-tooltip placement="top" trigger="hover">
                    <template #trigger>
                      <div class="action-dot" @click.stop="handleUseTemplate(item)" title="使用模板">
                        <n-icon size="12"><HammerIcon /></n-icon>
                      </div>
                    </template>
                    <span>使用模板</span>
                  </n-tooltip>
                  <n-tooltip placement="top" trigger="hover">
                    <template #trigger>
                      <div class="action-dot" @click.stop="handlePreview(item)" title="预览">
                        <n-icon size="12"><BrowsersOutlineIcon /></n-icon>
                      </div>
                    </template>
                    <span>预览</span>
                  </n-tooltip>
                </div>
              </div>

              <div class="card-body">
                <div class="card-title-row">
                  <span class="card-indicator released"></span>
                  <n-text class="go-ellipsis-1 card-title" :title="item.title">
                    {{ item.title }}
                  </n-text>
                </div>
                <div class="card-meta">
                  <span class="meta-badge released">公开模板</span>
                  <span class="meta-time">{{ item.createTime || '' }}</span>
                </div>
                <div class="card-desc go-ellipsis-1">{{ item.label || '模板市场' }}</div>
              </div>
            </div>
          </div>
        </n-grid-item>
      </n-grid>
      <div v-else class="content-empty">
        <n-empty description="模板市场暂无数据" />
      </div>

      <div class="list-pagination">
        <n-pagination
          :item-count="pagination.itemCount"
          :page="pagination.page"
          :page-size="pagination.pageSize"
          :page-sizes="[8, 12, 16, 24]"
          show-size-picker
          @update:page="handlePageChange"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </template>

    <n-modal v-model:show="showUseModal" preset="card" title="使用模板" style="width: 520px">
      <n-form label-placement="top">
        <n-form-item label="新项目名称">
          <n-input v-model:value="useForm.projectName" placeholder="请输入新项目名称" />
        </n-form-item>
      </n-form>
      <template #action>
        <div class="modal-actions">
          <n-button @click="showUseModal = false">取消</n-button>
          <n-button type="primary" :loading="submittingUseTemplate" @click="handleSubmitUseTemplate">创建项目</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { icon } from '@/plugins'
import { PreviewEnum, ChartEnum } from '@/enums/pageEnum'
import { fetchPathByName, routerTurnByPath, requireErrorImg } from '@/utils'
import { getTemplateMarketPageApi, copyTemplateToProjectApi, type ForgeTemplate } from '@/api/project/template'

const { SearchIcon, ArrowRedoIcon, HammerIcon, BrowsersOutlineIcon } = icon.ionicons5

const loadingTemplates = ref(false)
const showUseModal = ref(false)
const submittingUseTemplate = ref(false)
const currentTemplate = ref<TemplateCard | null>(null)
const templateCards = ref<TemplateCard[]>([])
const errorImg = requireErrorImg()

const filters = reactive({
  templateName: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 12,
  itemCount: 0
})

const useForm = reactive({
  projectName: ''
})

type TemplateCard = {
  id: number | string
  title: string
  label: string
  createTime?: string
  indexImg?: string
  imageSrc: string
}

const buildImageSrc = async (url?: string) => {
  if (!url) return errorImg
  if (url.startsWith('data:') || url.startsWith('blob:') || url.startsWith('http://') || url.startsWith('https://') || !url.includes('/api/file/')) {
    return url
  }
  try {
    const res = await fetch(url)
    if (!res.ok) return errorImg
    const blob = await res.blob()
    return URL.createObjectURL(blob)
  } catch {
    return errorImg
  }
}

const mapTemplateCards = async (records: ForgeTemplate[]) => {
  const list = await Promise.all((records || []).map(async item => ({
    id: item.id,
    title: item.templateName,
    label: item.remark || '模板市场',
    createTime: item.createTime,
    indexImg: item.indexImg,
    imageSrc: await buildImageSrc(item.indexImg)
  })))
  return list as TemplateCard[]
}

const loadTemplateList = async () => {
  try {
    loadingTemplates.value = true
    const res = await getTemplateMarketPageApi({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      templateName: filters.templateName || undefined
    })
    const pageData = res?.data
    templateCards.value = await mapTemplateCards(pageData?.records || [])
    pagination.itemCount = pageData?.total || 0
  } catch (error: any) {
    window.$message.error(error?.message || '获取模板市场失败')
  } finally {
    loadingTemplates.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadTemplateList()
}

const handleResetSearch = () => {
  filters.templateName = ''
  pagination.page = 1
  loadTemplateList()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  loadTemplateList()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.page = 1
  pagination.pageSize = pageSize
  loadTemplateList()
}

const resizeHandle = (cardData: TemplateCard) => {
  currentTemplate.value = cardData
}

const handlePreview = (cardData: TemplateCard) => {
  const p = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
  if (p) routerTurnByPath(p, [String(cardData.id)], undefined, true)
}

const handleUseTemplate = (cardData: TemplateCard) => {
  currentTemplate.value = cardData
  useForm.projectName = `${cardData.title}-项目`
  showUseModal.value = true
}

const handleSubmitUseTemplate = async () => {
  if (!currentTemplate.value) return
  if (!useForm.projectName) {
    window.$message.warning('请输入新项目名称')
    return
  }
  submittingUseTemplate.value = true
  try {
    const res = await copyTemplateToProjectApi({
      templateId: currentTemplate.value.id,
      projectName: useForm.projectName
    })
    const projectId = res?.data?.projectId || currentTemplate.value.id
    const path = fetchPathByName(ChartEnum.CHART_HOME_NAME, 'href')
    routerTurnByPath(path, [String(projectId)], undefined, true)
    showUseModal.value = false
  } catch (error: any) {
    window.$message.error(error?.message || '复制模板失败')
  } finally {
    submittingUseTemplate.value = false
  }
}

onMounted(loadTemplateList)
</script>

<style lang="scss" scoped>
@include go(project-template-market-page) {
  padding: 0 32px 32px;
  min-height: calc(100vh - 2px);
}

.page-header {
  padding: 28px 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 4px;

  .title-accent {
    color: $--color-accent;
    font-weight: 300;
    margin-right: 8px;
  }
}

.page-subtitle {
  margin: 0;
  font-size: 12px;
  @include fetch-color(3);
}

.toolbar-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-input {
  width: 260px;
}

.template-card {
  border-radius: $--border-radius-lg;
  overflow: hidden;
  @include fetch-bg-color('background-color1');
  border: 1px solid rgba(var(--app-theme-rgb), 0.04);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.template-card:hover {
  border-color: rgba(var(--app-theme-rgb), 0.2);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5), 0 0 20px rgba(var(--app-theme-rgb), 0.06), inset 0 0 0 1px rgba(var(--app-theme-rgb), 0.06);
  transform: translateY(-3px);
}

.card-image {
  text-align: center;
  position: relative;
  overflow: hidden;
  cursor: pointer;
  height: 170px;
  @include fetch-bg-color('background-color2');
}

.card-actions {
  position: absolute;
  top: 6px;
  right: 6px;
  opacity: 0;
  transform: translateX(4px);
  transition: all 0.25s ease;
  display: flex;
  gap: 4px;
}

.template-card:hover .card-actions {
  opacity: 1;
  transform: translateX(0);
}

.action-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  @include fetch-bg-color('background-color');
  border: 1px solid rgba(255, 255, 255, 0.06);
  @include fetch-color(2);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-dot:hover {
  background: rgba(var(--app-theme-rgb), 0.15);
  border-color: rgba(var(--app-theme-rgb), 0.3);
  color: $--color-primary;
}

.card-body {
  padding: 14px 16px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.card-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: $--color-success;
  box-shadow: 0 0 6px rgba(46, 213, 115, 0.5);
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  @include fetch-color();
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.meta-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(46, 213, 115, 0.08);
  color: $--color-success;
  border: 1px solid rgba(46, 213, 115, 0.15);
}

.meta-time {
  font-size: 10px;
  @include fetch-color(4);
  font-family: 'Courier New', monospace;
}

.card-desc {
  font-size: 12px;
  @include fetch-color(3);
}

.content-loading,
.content-empty {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.list-pagination {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
