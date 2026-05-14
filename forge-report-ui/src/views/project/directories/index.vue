<template>
  <div class="go-project-directories">
    <div class="page-header go-float-up">
      <div>
        <h2 class="page-title go-text-neon">
          <span class="title-accent">//</span>
          {{ $t('project.directory_manage') }}
        </h2>
        <p class="page-subtitle">统一维护报表目录树，支持多级结构和目录移动</p>
      </div>
    </div>

    <div class="directory-layout">
      <section class="directory-tree-panel">
        <div class="panel-toolbar">
          <div>
            <div class="panel-title">目录树</div>
            <div class="panel-desc">选择目录后可进行新增子目录、编辑、移动和删除</div>
          </div>
          <div class="toolbar-actions">
            <n-button quaternary circle size="small" @click="openCreateRootDirectory">
              <template #icon>
                <n-icon><AddIcon /></n-icon>
              </template>
            </n-button>
            <n-button quaternary circle size="small" :disabled="!currentDirectory" @click="openCreateChildDirectory">
              <template #icon>
                <n-icon><FolderOpenIcon /></n-icon>
              </template>
            </n-button>
            <n-button quaternary circle size="small" :disabled="!currentDirectory" @click="openEditDirectory">
              <template #icon>
                <n-icon><PencilIcon /></n-icon>
              </template>
            </n-button>
            <n-button quaternary circle size="small" :disabled="!currentDirectory" @click="openMoveDirectory">
              <template #icon>
                <n-icon><StackedMoveIcon /></n-icon>
              </template>
            </n-button>
            <n-button quaternary circle size="small" :disabled="!currentDirectory" @click="handleDeleteDirectory">
              <template #icon>
                <n-icon><TrashIcon /></n-icon>
              </template>
            </n-button>
            <n-button quaternary circle size="small" @click="loadDirectoryTree(selectedDirectoryKey)">
              <template #icon>
                <n-icon><ArrowRedoIcon /></n-icon>
              </template>
            </n-button>
          </div>
        </div>

        <div class="tree-body">
          <n-spin :show="loadingDirectories">
            <n-tree
              block-line
              key-field="key"
              label-field="label"
              children-field="children"
              :data="directoryTreeNodes"
              :selected-keys="selectedDirectoryKey ? [selectedDirectoryKey] : []"
              @update:selected-keys="handleDirectorySelect"
            />
          </n-spin>
        </div>
      </section>

      <section class="directory-detail-panel">
        <template v-if="currentDirectory">
          <div class="detail-header">
            <div>
              <div class="detail-title">{{ currentDirectory.directoryName }}</div>
              <div class="detail-subtitle">目录详情</div>
            </div>
            <div class="detail-actions">
              <n-button size="small" @click="openEditDirectory">编辑</n-button>
              <n-button size="small" @click="openMoveDirectory">移动</n-button>
            </div>
          </div>

          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">目录名称</span>
              <span class="detail-value">{{ currentDirectory.directoryName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">上级目录</span>
              <span class="detail-value">{{ currentParentName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">排序</span>
              <span class="detail-value">{{ currentDirectory.sort ?? 0 }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">备注</span>
              <span class="detail-value">{{ currentDirectory.remark || '--' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">创建时间</span>
              <span class="detail-value">{{ currentDirectory.createTime || '--' }}</span>
            </div>
          </div>
        </template>

        <div v-else class="detail-empty">
          <n-empty description="还没有目录，请先新建一级目录" />
        </div>
      </section>
    </div>

    <n-modal v-model:show="showDirectoryModal" preset="card" :title="directoryModalTitle" style="width: 520px">
      <n-form ref="directoryFormRef" :model="directoryForm" :rules="directoryRules" label-placement="top">
        <n-form-item label="目录名称" path="directoryName">
          <n-input v-model:value="directoryForm.directoryName" maxlength="100" placeholder="请输入目录名称" />
        </n-form-item>
        <n-form-item label="上级目录">
          <n-input :value="directoryParentLabel" disabled />
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number v-model:value="directoryForm.sort" :min="0" :precision="0" class="w-full" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="directoryForm.remark" type="textarea" maxlength="200" show-count placeholder="请输入备注" />
        </n-form-item>
      </n-form>
      <template #action>
        <div class="modal-actions">
          <n-button @click="showDirectoryModal = false">取消</n-button>
          <n-button type="primary" :loading="submittingDirectory" @click="handleSubmitDirectory">保存</n-button>
        </div>
      </template>
    </n-modal>

    <n-modal v-model:show="showMoveDirectoryModal" preset="card" title="移动目录" style="width: 520px">
      <n-form label-placement="top">
        <n-form-item label="当前目录">
          <n-input :value="currentDirectory?.directoryName || ''" disabled />
        </n-form-item>
        <n-form-item label="目标上级目录">
          <n-tree-select
            v-model:value="moveDirectoryForm.targetParentId"
            :options="moveDirectoryOptions"
            clearable
            filterable
            placeholder="请选择目标上级目录，选顶级目录表示移动到最外层"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <div class="modal-actions">
          <n-button @click="showMoveDirectoryModal = false">取消</n-button>
          <n-button type="primary" :loading="submittingMoveDirectory" @click="handleSubmitMoveDirectory">确认移动</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInst, FormRules } from 'naive-ui'
import { createProjectDirectoryApi, deleteProjectDirectoryApi, getProjectDirectoryTreeApi, moveProjectDirectoryApi, type ReportDirectory, updateProjectDirectoryApi } from '@/api/project'
import { icon } from '@/plugins'
import { DialogEnum } from '@/enums/pluginEnum'
import { goDialog } from '@/utils'

type DirectoryTreeNode = {
  key: string
  label: string
  children?: DirectoryTreeNode[]
}

const { AddIcon, FolderOpenIcon, PencilIcon, TrashIcon, ArrowRedoIcon } = icon.ionicons5
const { StackedMoveIcon } = icon.carbon

const loadingDirectories = ref(false)
const showDirectoryModal = ref(false)
const showMoveDirectoryModal = ref(false)
const submittingDirectory = ref(false)
const submittingMoveDirectory = ref(false)
const selectedDirectoryKey = ref('')
const directoryTreeRaw = ref<ReportDirectory[]>([])
const directoryLookup = ref<Map<string, ReportDirectory>>(new Map())

const directoryModalMode = ref<'create-root' | 'create-child' | 'edit'>('create-root')
const directoryFormRef = ref<FormInst | null>(null)
const directoryForm = reactive({
  id: undefined as string | number | undefined,
  parentId: '0' as string | number,
  directoryName: '',
  sort: 0,
  remark: ''
})
const moveDirectoryForm = reactive({
  targetParentId: '0' as string | number | undefined
})

const directoryRules: FormRules = {
  directoryName: [{ required: true, message: '请输入目录名称', trigger: ['input', 'blur'] }]
}

const buildDirectoryLookup = (directories: ReportDirectory[]) => {
  const lookup = new Map<string, ReportDirectory>()
  const walk = (items: ReportDirectory[]) => {
    items.forEach(item => {
      lookup.set(String(item.id), item)
      if (Array.isArray(item.children) && item.children.length) {
        walk(item.children)
      }
    })
  }
  walk(directories)
  return lookup
}

const buildTreeNodes = (directories: ReportDirectory[]): DirectoryTreeNode[] => {
  return (directories || []).map(directory => ({
    key: String(directory.id),
    label: directory.directoryName,
    children: buildTreeNodes(directory.children || [])
  }))
}

const collectSubtreeKeys = (directory?: ReportDirectory | null) => {
  const keys = new Set<string>()
  const walk = (node?: ReportDirectory) => {
    if (!node) {
      return
    }
    keys.add(String(node.id))
    ;(node.children || []).forEach(child => walk(child))
  }
  walk(directory || undefined)
  return keys
}

const mapMoveTreeOptions = (directories: ReportDirectory[], disabledKeys: Set<string>) => {
  return (directories || []).map(directory => ({
    label: directory.directoryName,
    key: String(directory.id),
    disabled: disabledKeys.has(String(directory.id)),
    children: mapMoveTreeOptions(directory.children || [], disabledKeys)
  }))
}

const buildMoveTreeOptions = (directories: ReportDirectory[], disabledKeys: Set<string>) => {
  const children = mapMoveTreeOptions(directories, disabledKeys)
  return [
    {
      label: '顶级目录',
      key: '0',
      children
    }
  ]
}

const pickFirstDirectoryKey = (directories: ReportDirectory[]): string => {
  if (!directories.length) {
    return ''
  }
  return String(directories[0].id)
}

const directoryTreeNodes = computed(() => buildTreeNodes(directoryTreeRaw.value))
const currentDirectory = computed(() => directoryLookup.value.get(selectedDirectoryKey.value) || null)
const currentParentName = computed(() => {
  const parentId = currentDirectory.value?.parentId
  if (!parentId || String(parentId) === '0') {
    return '顶级目录'
  }
  return directoryLookup.value.get(String(parentId))?.directoryName || '顶级目录'
})
const directoryModalTitle = computed(() => {
  if (directoryModalMode.value === 'create-root') {
    return '新建一级目录'
  }
  if (directoryModalMode.value === 'create-child') {
    return '新建子目录'
  }
  return '编辑目录'
})
const directoryParentLabel = computed(() => {
  if (directoryModalMode.value === 'create-root') {
    return '顶级目录'
  }
  if (directoryModalMode.value === 'create-child') {
    return currentDirectory.value?.directoryName || '顶级目录'
  }
  if (!directoryForm.parentId || String(directoryForm.parentId) === '0') {
    return '顶级目录'
  }
  return directoryLookup.value.get(String(directoryForm.parentId))?.directoryName || '顶级目录'
})
const moveDirectoryOptions = computed(() => buildMoveTreeOptions(directoryTreeRaw.value, collectSubtreeKeys(currentDirectory.value)))

const resetDirectoryForm = () => {
  directoryForm.id = undefined
  directoryForm.parentId = '0'
  directoryForm.directoryName = ''
  directoryForm.sort = 0
  directoryForm.remark = ''
}

const loadDirectoryTree = async (preferredKey?: string) => {
  try {
    loadingDirectories.value = true
    const res = await getProjectDirectoryTreeApi()
    directoryTreeRaw.value = res?.data || []
    directoryLookup.value = buildDirectoryLookup(directoryTreeRaw.value)
    const nextKey = preferredKey && directoryLookup.value.has(String(preferredKey))
      ? String(preferredKey)
      : pickFirstDirectoryKey(directoryTreeRaw.value)
    selectedDirectoryKey.value = nextKey
  } catch (error: any) {
    window.$message.error(error?.message || '获取目录树失败')
  } finally {
    loadingDirectories.value = false
  }
}

const handleDirectorySelect = (keys: Array<string | number>) => {
  selectedDirectoryKey.value = keys?.length ? String(keys[0]) : ''
}

const openCreateRootDirectory = () => {
  directoryModalMode.value = 'create-root'
  resetDirectoryForm()
  showDirectoryModal.value = true
}

const openCreateChildDirectory = () => {
  if (!currentDirectory.value) {
    return
  }
  directoryModalMode.value = 'create-child'
  resetDirectoryForm()
  directoryForm.parentId = String(currentDirectory.value.id)
  showDirectoryModal.value = true
}

const openEditDirectory = () => {
  if (!currentDirectory.value) {
    return
  }
  directoryModalMode.value = 'edit'
  directoryForm.id = currentDirectory.value.id
  directoryForm.parentId = currentDirectory.value.parentId ? String(currentDirectory.value.parentId) : '0'
  directoryForm.directoryName = currentDirectory.value.directoryName
  directoryForm.sort = currentDirectory.value.sort || 0
  directoryForm.remark = currentDirectory.value.remark || ''
  showDirectoryModal.value = true
}

const openMoveDirectory = () => {
  if (!currentDirectory.value) {
    return
  }
  moveDirectoryForm.targetParentId = currentDirectory.value.parentId ? String(currentDirectory.value.parentId) : '0'
  showMoveDirectoryModal.value = true
}

const handleSubmitDirectory = async () => {
  await directoryFormRef.value?.validate()
  submittingDirectory.value = true
  try {
    if (directoryModalMode.value === 'edit') {
      await updateProjectDirectoryApi({
        id: directoryForm.id,
        directoryName: directoryForm.directoryName.trim(),
        sort: directoryForm.sort,
        remark: directoryForm.remark
      })
      window.$message.success('目录更新成功')
      showDirectoryModal.value = false
      await loadDirectoryTree(String(directoryForm.id))
      return
    }

    const res = await createProjectDirectoryApi({
      parentId: directoryModalMode.value === 'create-root' ? '0' : directoryForm.parentId,
      directoryName: directoryForm.directoryName.trim(),
      sort: directoryForm.sort,
      remark: directoryForm.remark
    })
    window.$message.success('目录创建成功')
    showDirectoryModal.value = false
    await loadDirectoryTree(res?.data?.id ? String(res.data.id) : selectedDirectoryKey.value)
  } catch (error: any) {
    window.$message.error(error?.message || '目录保存失败')
  } finally {
    submittingDirectory.value = false
  }
}

const handleSubmitMoveDirectory = async () => {
  if (!currentDirectory.value) {
    return
  }
  submittingMoveDirectory.value = true
  try {
    await moveProjectDirectoryApi({
      id: currentDirectory.value.id,
      targetParentId: moveDirectoryForm.targetParentId || '0'
    })
    window.$message.success('目录移动成功')
    showMoveDirectoryModal.value = false
    await loadDirectoryTree(String(currentDirectory.value.id))
  } catch (error: any) {
    window.$message.error(error?.message || '目录移动失败')
  } finally {
    submittingMoveDirectory.value = false
  }
}

const handleDeleteDirectory = () => {
  if (!currentDirectory.value) {
    return
  }
  const directory = currentDirectory.value
  goDialog({
    type: DialogEnum.DELETE,
    message: `确认删除目录“${directory.directoryName}”吗？`,
    promise: true,
    onPositiveCallback: () => deleteProjectDirectoryApi(directory.id),
    promiseResCallback: async () => {
      window.$message.success('目录删除成功')
      await loadDirectoryTree()
    }
  })
}

onMounted(async () => {
  await loadDirectoryTree()
})
</script>

<style scoped lang="scss">
@include go(project-directories) {
  padding: 0 32px 32px;
  min-height: calc(100vh - 2px);
}

.page-header {
  padding: 28px 0 24px;
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

.directory-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 20px;
  min-height: calc(100vh - 180px);
}

.directory-tree-panel,
.directory-detail-panel {
  border: 1px solid rgba(var(--app-theme-rgb), 0.08);
  border-radius: $--border-radius-lg;
  @include fetch-bg-color('background-color1');
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
}

.panel-toolbar,
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.panel-title,
.detail-title {
  font-size: 15px;
  font-weight: 600;
}

.panel-desc,
.detail-subtitle {
  margin-top: 4px;
  font-size: 12px;
  @include fetch-color(3);
}

.toolbar-actions,
.detail-actions,
.modal-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-body {
  padding: 12px;
  overflow: auto;
}

.directory-detail-panel {
  display: flex;
  flex-direction: column;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border-radius: $--border-radius-sm;
  background: rgba(var(--app-theme-rgb), 0.04);
}

.detail-label {
  font-size: 12px;
  @include fetch-color(3);
}

.detail-value {
  font-size: 14px;
  word-break: break-all;
}

.detail-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.w-full {
  width: 100%;
}

@media (max-width: 1200px) {
  .directory-layout {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
