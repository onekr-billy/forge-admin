<template>
  <div class="go-project-my-template-page">
    <div class="page-header go-float-up">
      <div>
        <h2 class="page-title go-text-neon">
          <span class="title-accent">//</span>
          我的模板
        </h2>
        <p class="page-subtitle">私有模板仅自己可见，发布后会转为公开模板并进入模板市场</p>
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
          <span style="color: #64748b; margin-top: 12px; display: block;">正在加载模板列表...</span>
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
                      <div class="action-dot" @click.stop="editHandle(item)" title="编辑">
                        <n-icon size="12"><HammerIcon /></n-icon>
                      </div>
                    </template>
                    <span>编辑</span>
                  </n-tooltip>
                  <n-dropdown
                    trigger="click"
                    placement="bottom-end"
                    size="small"
                    content-class="template-action-dropdown"
                    :theme-overrides="actionDropdownThemeOverrides"
                    :options="actionOptions(item)"
                    @select="key => handleSelect(key, item)"
                  >
                    <div class="action-dot" @click.stop>
                      <n-icon size="12"><EllipsisHorizontalCircleSharpIcon /></n-icon>
                    </div>
                  </n-dropdown>
                </div>
              </div>

              <div class="card-body">
                <div class="card-title-row">
                  <span class="card-indicator" :class="{ released: item.isPublic }"></span>
                  <n-text class="go-ellipsis-1 card-title" :title="item.title">
                    {{ item.title }}
                  </n-text>
                </div>
                <div class="card-meta">
                  <span class="meta-badge" :class="{ released: item.isPublic }">
                    {{ item.isPublic ? '公开模板' : '私有模板' }}
                  </span>
                  <span class="meta-time">{{ item.createTime || '' }}</span>
                </div>
                <div class="card-desc go-ellipsis-1">{{ item.label || '模板' }}</div>
              </div>
            </div>
          </div>
        </n-grid-item>
      </n-grid>
      <div v-else class="content-empty">
        <n-empty description="当前条件下暂无模板" />
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

    <n-modal v-model:show="showTemplateModal" preset="card" :title="modalTitle" style="width: 560px">
      <n-form label-placement="top">
        <n-form-item v-if="currentAction" label="模板名称">
          <n-input v-model:value="modalForm.templateName" placeholder="请输入模板名称" />
        </n-form-item>
        <n-form-item v-if="currentAction" label="模板说明">
          <n-input v-model:value="modalForm.remark" type="textarea" placeholder="请输入模板说明" />
        </n-form-item>
        <n-descriptions v-else :column="1" bordered>
          <n-descriptions-item label="模板名称">{{ currentTemplate?.title || '--' }}</n-descriptions-item>
          <n-descriptions-item label="模板说明">{{ currentTemplate?.remark || '--' }}</n-descriptions-item>
          <n-descriptions-item label="创建时间">{{ currentTemplate?.createTime || '--' }}</n-descriptions-item>
          <n-descriptions-item label="拥有者">{{ currentTemplate?.ownerName || '--' }}</n-descriptions-item>
          <n-descriptions-item label="可见范围">{{ currentTemplate?.isPublic ? '公开模板' : '私有模板' }}</n-descriptions-item>
        </n-descriptions>
      </n-form>
      <template #action>
        <div class="modal-actions">
          <n-button @click="showTemplateModal = false">{{ currentAction ? '取消' : '关闭' }}</n-button>
          <n-button v-if="currentAction" type="primary" :loading="submittingTemplate" @click="handleSubmitTemplate">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { icon } from '@/plugins'
import { goDialog } from '@/utils'
import { DialogEnum } from '@/enums/pluginEnum'
import { PreviewEnum, ChartEnum } from '@/enums/pageEnum'
import { fetchPathByName, routerTurnByPath, getLocalStorage, requireErrorImg, setSessionStorage, getSessionStorage } from '@/utils'
import { StorageEnum } from '@/enums/storageEnum'
import { getProjectDetailApi } from '@/api/project'
import { copyTemplateToProjectApi, deleteTemplateApi, getMyTemplatePageApi, publishTemplateToMarketApi, type ForgeTemplate } from '@/api/project/template'
import type { Chartype } from '../items/index.d'

const { SearchIcon, ArrowRedoIcon, EllipsisHorizontalCircleSharpIcon, HammerIcon, BrowsersOutlineIcon, SendIcon, TrashIcon, DuplicateOutlineIcon } = icon.ionicons5

const loadingTemplates = ref(false)
const showTemplateModal = ref(false)
const submittingTemplate = ref(false)
const currentAction = ref<'publish' | 'copy' | null>(null)
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

const modalForm = reactive({
  templateName: '',
  remark: ''
})

type TemplateCard = Chartype & {
  imageSrc: string
  isPublic: boolean
  publishStatus?: string
  templateScope?: string
  ownerName?: string
  remark?: string
  sourceProjectId?: number | string
}

const buildImageSrc = async (url?: string) => {
  if (!url) return errorImg
  if (url.startsWith('data:') || url.startsWith('blob:') || url.startsWith('http://') || url.startsWith('https://') || !url.includes('/api/file/')) {
    return url
  }
  try {
    const token = getLocalStorage(StorageEnum.GO_ACCESS_TOKEN_STORE)
    const res = await fetch(url, { headers: { Authorization: token ? `Bearer ${token}` : '' } })
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
    label: item.remark || item.ownerName || '模板',
    isPublic: item.templateScope === '1' || item.publishStatus === '1',
    publishUrl: item.publishUrl,
    createTime: item.createTime,
    indexImg: item.indexImg,
    imageSrc: await buildImageSrc(item.indexImg),
    publishStatus: item.publishStatus,
    templateScope: item.templateScope,
    ownerName: item.ownerName,
    remark: item.remark,
    sourceProjectId: item.sourceProjectId
  })))
  return list as TemplateCard[]
}

const loadTemplateList = async () => {
  try {
    loadingTemplates.value = true
    const res = await getMyTemplatePageApi({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      templateName: filters.templateName || undefined
    })
    const pageData = res?.data
    templateCards.value = await mapTemplateCards(pageData?.records || [])
    pagination.itemCount = pageData?.total || 0
  } catch (error: any) {
    window.$message.error(error?.message || '获取模板列表失败')
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
  currentAction.value = null
  showTemplateModal.value = true
}

const editHandle = async (cardData: TemplateCard) => {
  try {
    const res = await getProjectDetailApi(cardData.sourceProjectId || cardData.id)
    const project = res?.data
    if (!project?.componentData) {
      window.$message.warning('该模板暂无可编辑内容')
      return
    }
    const parsed = JSON.parse(project.componentData)
    const sessionStorageInfo = getSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST) || []
    const repeatIndex = sessionStorageInfo.findIndex((e: { id: string | number }) => String(e.id) === String(cardData.sourceProjectId || cardData.id))
    const saveData = { ...parsed, id: String(cardData.sourceProjectId || cardData.id) }
    if (repeatIndex !== -1) sessionStorageInfo.splice(repeatIndex, 1, saveData)
    else sessionStorageInfo.push(saveData)
    setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, sessionStorageInfo)
    const path = fetchPathByName(ChartEnum.CHART_HOME_NAME, 'href')
    routerTurnByPath(path, [String(cardData.sourceProjectId || cardData.id)], undefined, true)
  } catch (error: any) {
    window.$message.error(error?.message || '加载模板详情失败')
  }
}

const handlePreview = (cardData: TemplateCard) => {
  const p = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
  if (p) routerTurnByPath(p, [String(cardData.id)], undefined, true)
}

const actionOptions = (cardData: TemplateCard) => [
  { label: '预览', key: 'preview', icon: () => h(BrowsersOutlineIcon) },
  { label: '基于模板新建项目', key: 'copy', icon: () => h(DuplicateOutlineIcon) },
  { label: '删除', key: 'delete', icon: () => h(TrashIcon) },
  { label: cardData.isPublic ? '重新发布公开模板' : '发布为公开模板', key: 'publish', icon: () => h(SendIcon) }
]

const actionDropdownThemeOverrides = {
  padding: '4px',
  borderRadius: '12px',
  fontSizeSmall: '12px',
  optionHeightSmall: '30px',
  optionIconSizeSmall: '13px',
  optionIconPrefixWidthSmall: '26px',
  optionPrefixWidthSmall: '18px',
  optionSuffixWidthSmall: '8px',
  optionColorHover: 'rgba(255, 255, 255, 0.06)',
  optionColorActive: 'rgba(var(--app-theme-rgb), 0.14)'
}

const handleSelect = async (key: string, cardData: TemplateCard) => {
  if (key === 'preview') handlePreview(cardData)
  if (key === 'copy') {
    currentAction.value = 'copy'
    currentTemplate.value = cardData
    modalForm.templateName = `${cardData.title}-副本`
    modalForm.remark = cardData.remark || ''
    showTemplateModal.value = true
  }
  if (key === 'delete') {
    goDialog({
      type: DialogEnum.DELETE,
      promise: true,
      onPositiveCallback: () => deleteTemplateApi(cardData.id),
      promiseResCallback: async () => {
        window.$message.success('删除成功')
        await loadTemplateList()
      }
    })
  }
  if (key === 'publish') {
    currentAction.value = 'publish'
    currentTemplate.value = cardData
    showTemplateModal.value = true
  }
}

const modalTitle = computed(() => {
  if (currentAction.value === 'copy') return '基于模板创建新项目'
  if (currentAction.value === 'publish') return '发布为公开模板'
  return '模板详情'
})

const handleSubmitTemplate = async () => {
  if (!currentTemplate.value) return
  submittingTemplate.value = true
  try {
    if (currentAction.value === 'copy') {
      const res = await copyTemplateToProjectApi({
        templateId: currentTemplate.value.id,
        projectName: modalForm.templateName
      })
      const projectId = res?.data?.projectId || currentTemplate.value.id
      const path = fetchPathByName(ChartEnum.CHART_HOME_NAME, 'href')
      routerTurnByPath(path, [String(projectId)], undefined, true)
      showTemplateModal.value = false
      return
    }
    if (currentAction.value === 'publish') {
      await publishTemplateToMarketApi(currentTemplate.value.id, `${window.location.origin}${window.location.pathname}`)
      window.$message.success(currentTemplate.value.isPublic ? '重新发布成功' : '已转为公开模板')
      showTemplateModal.value = false
      await loadTemplateList()
    }
  } catch (error: any) {
    window.$message.error(error?.message || '操作失败')
  } finally {
    submittingTemplate.value = false
    currentAction.value = null
  }
}

onMounted(loadTemplateList)
</script>

<style lang="scss" scoped>
@include go(project-my-template-page) {
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

:global(.template-action-dropdown) {
  backdrop-filter: blur(18px);
}

:global(.template-action-dropdown .n-dropdown-menu) {
  min-width: 164px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(13, 18, 28, 0.96);
  box-shadow:
    0 16px 40px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(var(--app-theme-rgb), 0.06);
}

:global(.template-action-dropdown .n-dropdown-option-body) {
  font-weight: 500;
}

:global(.template-action-dropdown .n-dropdown-option-body__label) {
  letter-spacing: 0.01em;
}

:global(.template-action-dropdown .n-dropdown-option-body__prefix) {
  opacity: 0.86;
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
  background: $--color-warn;
  box-shadow: 0 0 6px rgba(255, 165, 2, 0.5);
}

.card-indicator.released {
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
  background: rgba(255, 165, 2, 0.1);
  color: $--color-warn;
  border: 1px solid rgba(255, 165, 2, 0.15);
}

.meta-badge.released {
  background: rgba(46, 213, 115, 0.08);
  color: $--color-success;
  border-color: rgba(46, 213, 115, 0.15);
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
