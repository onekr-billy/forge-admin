<template>
  <aside class="permission-sidebar" aria-label="菜单目录导航">
    <div class="sidebar-search">
      <n-input
        v-model:value="keyword"
        clearable
        placeholder="搜索菜单或目录"
        aria-label="搜索菜单或目录"
        size="small"
      >
        <template #prefix>
          <i class="i-material-symbols:search" aria-hidden="true" />
        </template>
      </n-input>

      <n-dropdown
        trigger="click"
        placement="bottom-start"
        :options="globalBatchOptions"
        @select="key => emit('globalAction', key)"
      >
        <n-button
          size="small"
          secondary
          :disabled="loading || navigationTree.length === 0"
          class="batch-menu-button is-sidebar-batch"
        >
          <template #icon>
            <i class="i-material-symbols:admin-panel-settings" />
          </template>
          <span>全局授权</span>
          <i class="i-material-symbols:keyboard-arrow-down batch-menu-chevron" aria-hidden="true" />
        </n-button>
      </n-dropdown>

      <div class="sidebar-action-row" role="group" aria-label="目录快捷操作">
        <button
          type="button"
          class="sidebar-action-button"
          :disabled="batchActionDisabled"
          title="展开左侧所有可展开目录"
          aria-label="展开全部目录"
          @click="expandAll"
        >
          <i class="i-material-symbols:unfold-more" aria-hidden="true" />
          <span>展开全部</span>
        </button>
        <button
          type="button"
          class="sidebar-action-button"
          :disabled="batchActionDisabled"
          title="收起左侧所有目录"
          aria-label="收起全部目录"
          @click="collapseAll"
        >
          <i class="i-material-symbols:unfold-less" aria-hidden="true" />
          <span>收起全部</span>
        </button>
        <button
          type="button"
          class="sidebar-action-button is-primary"
          :disabled="batchActionDisabled"
          title="勾选全部菜单、按钮和接口权限"
          aria-label="全选权限"
          @click="emit('globalAction', 'global:all')"
        >
          <i class="i-material-symbols:done-all" aria-hidden="true" />
          <span>全选</span>
        </button>
        <button
          type="button"
          class="sidebar-action-button is-danger"
          :disabled="batchActionDisabled"
          title="取消全部菜单、按钮和接口权限"
          aria-label="清空权限"
          @click="emit('globalAction', 'global:clear')"
        >
          <i class="i-material-symbols:playlist-remove" aria-hidden="true" />
          <span>清空</span>
        </button>
      </div>
    </div>

    <div class="module-nav-list">
      <div v-if="loading" class="module-skeleton-list" aria-label="菜单目录加载中">
        <div v-for="index in 8" :key="`module-skeleton-${index}`" class="module-skeleton-item">
          <n-skeleton circle size="small" />
          <n-skeleton text :width="index % 3 === 0 ? '58%' : '72%'" />
          <n-skeleton circle size="small" class="module-skeleton-status" />
        </div>
      </div>

      <n-tree
        v-else-if="filteredTree.length"
        block-line
        selectable
        :data="filteredTree"
        :expanded-keys="expandedKeys"
        :selected-keys="selectedKey ? [selectedKey] : []"
        :render-label="renderLabel"
        key-field="key"
        label-field="label"
        children-field="children"
        @update:selected-keys="handleSelect"
        @update:expanded-keys="handleExpand"
      />

      <n-empty v-else description="没有匹配的菜单或目录" size="small" />
    </div>

    <div class="sidebar-legend">
      <span><i class="i-material-symbols:check-circle" />已满配</span>
      <span><i class="i-material-symbols:do-not-disturb-on" />部分配</span>
      <span><i class="i-material-symbols:radio-button-unchecked" />未配置</span>
    </div>
  </aside>
</template>

<script setup>
import { NCheckbox } from 'naive-ui'
import { computed, h, ref, watch } from 'vue'
import {
  collectPermissionNavigationExpandableKeys,
  filterPermissionNavigationTree,
  findPermissionNavigationMatch,
  findPermissionNavigationPath,
  navigationNodeStatus,
} from './role-permission-model'

defineOptions({ name: 'RolePermissionNavigation' })

const props = defineProps({
  navigationTree: {
    type: Array,
    default: () => [],
  },
  selectedKey: {
    type: String,
    default: '',
  },
  checkedKeys: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  globalBatchOptions: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:selected-key', 'update:checked-keys', 'globalAction'])
const keyword = ref('')
const expandedKeys = ref([])

const filteredTree = computed(() => filterPermissionNavigationTree(props.navigationTree, keyword.value))
const batchActionDisabled = computed(() => props.loading || props.navigationTree.length === 0)

watch([() => props.navigationTree, keyword], () => {
  const currentPath = findPermissionNavigationPath(filteredTree.value, props.selectedKey)
  if (keyword.value.trim()) {
    expandedKeys.value = collectPermissionNavigationExpandableKeys(filteredTree.value)
    return
  }
  expandCurrentPath(currentPath)
}, { immediate: true })

watch(() => props.selectedKey, (selectedKey) => {
  expandCurrentPath(findPermissionNavigationPath(filteredTree.value, selectedKey))
})

watch(keyword, (value) => {
  if (!value.trim())
    return
  const match = findPermissionNavigationMatch(props.navigationTree, value)
  if (match && String(match.key) !== String(props.selectedKey))
    emit('update:selected-key', String(match.key))
})

function handleSelect(keys) {
  const key = keys?.[0]
  if (key !== undefined && key !== null)
    emit('update:selected-key', String(key))
}

function handleExpand(keys) {
  expandedKeys.value = Array.isArray(keys) ? [...keys] : []
}

function expandAll() {
  expandedKeys.value = collectPermissionNavigationExpandableKeys(filteredTree.value)
}

function collapseAll() {
  expandedKeys.value = []
}

function expandCurrentPath(path) {
  if (path.length <= 1)
    return
  expandedKeys.value = [...new Set([...expandedKeys.value, ...path.slice(0, -1)])]
}

function handleCheck(option, checked) {
  const resourceIds = option?.resourceIds || []
  if (resourceIds.length === 0)
    return

  const next = new Map(props.checkedKeys.map(id => [String(id), id]))
  for (const id of resourceIds) {
    if (checked)
      next.set(String(id), id)
    else
      next.delete(String(id))
  }
  emit('update:checked-keys', [...next.values()])
}

function renderLabel({ option }) {
  const status = navigationNodeStatus(option, props.checkedKeys)
  const isPage = option.nodeType === 'page'
  const icon = isPage
    ? option.navigationHidden ? 'i-material-symbols:visibility-off-outline' : 'i-material-symbols:description'
    : 'i-material-symbols:folder-open'

  return h('div', { class: ['permission-tree-label', isPage ? 'is-page' : 'is-directory'] }, [
    h(NCheckbox, {
      'class': 'permission-tree-checkbox',
      'size': 'small',
      'checked': status === 'all',
      'indeterminate': status === 'partial',
      'aria-label': `${status === 'all' ? '取消' : '勾选'}${option.label}`,
      'onUpdate:checked': checked => handleCheck(option, checked),
      'onClick': event => event.stopPropagation(),
    }),
    h('i', { class: ['permission-tree-icon', icon] }),
    h('span', { class: 'permission-tree-name' }, option.label),
    isPage && option.navigationHidden
      ? h('span', {
          class: 'permission-tree-hidden',
          title: '该菜单不会显示在用户导航中，但权限资源仍可单独授权',
        }, '导航隐藏')
      : null,
    h('span', { class: ['permission-tree-status', `is-${status}`] }, status === 'all' ? '已选' : status === 'partial' ? '部分' : ''),
  ])
}
</script>

<style scoped>
.permission-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--border-light, #e2e8f0);
  background: var(--bg-primary, #fff);
}

.sidebar-search {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 0 0 auto;
  padding: 12px;
  border-bottom: 1px solid var(--border-light, #f1f5f9);
}

.batch-menu-button {
  min-width: 0;
  width: 100%;
}

.batch-menu-button :deep(.n-button__content) {
  min-width: 0;
  gap: 5px;
}

.batch-menu-button span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-menu-chevron {
  flex: 0 0 auto;
  margin-left: 2px;
  color: var(--text-tertiary, #64748b);
  font-size: 16px;
}

.sidebar-action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
}

.sidebar-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  gap: 3px;
  min-height: 28px;
  padding: 3px 2px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary, #475569);
  cursor: pointer;
  font-size: 11px;
  line-height: 1.2;
  transition:
    color 150ms ease,
    background 150ms ease,
    border-color 150ms ease;
}

.sidebar-action-button:hover:not(:disabled) {
  border-color: var(--border-light, #e2e8f0);
  background: var(--bg-secondary, #f8fafc);
  color: var(--text-primary, #0f172a);
}

.sidebar-action-button.is-primary {
  color: var(--primary-color, #4f46e5);
}

.sidebar-action-button.is-danger {
  color: var(--error-color, #ef4444);
}

.sidebar-action-button:disabled {
  color: var(--text-disabled, #cbd5e1);
  cursor: not-allowed;
}

.sidebar-action-button i {
  flex: 0 0 auto;
  font-size: 14px;
}

.module-nav-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
  scrollbar-gutter: stable;
}

.module-skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.module-skeleton-item {
  display: flex;
  align-items: center;
  min-height: 38px;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--bg-secondary, #f8fafc);
}

.module-skeleton-item :deep(.n-skeleton:nth-child(2)) {
  flex: 1;
  min-width: 0;
}

.module-skeleton-status {
  margin-left: auto;
}

.module-nav-list :deep(.n-tree) {
  --n-node-color-active: var(--bg-secondary, #f8fafc);
  --n-node-color-hover: var(--bg-secondary, #f8fafc);
  --n-node-text-color: var(--text-secondary, #475569);
  --n-node-text-color-active: var(--text-primary, #0f172a);
}

.permission-tree-label {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
  width: 100%;
  line-height: 1.35;
}

.permission-tree-checkbox {
  flex: 0 0 auto;
}

.permission-tree-icon {
  flex: 0 0 auto;
  color: var(--text-tertiary, #94a3b8);
  font-size: 16px;
}

.permission-tree-label.is-directory .permission-tree-icon {
  color: var(--primary-color, #4f46e5);
}

.permission-tree-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-tree-hidden {
  flex: 0 0 auto;
  padding: 1px 5px;
  border: 1px solid var(--border-light, #e2e8f0);
  border-radius: 4px;
  color: var(--text-tertiary, #64748b);
  font-size: 10px;
  font-weight: 600;
}

.permission-tree-status {
  flex: 0 0 auto;
  margin-left: auto;
  min-width: 24px;
  color: var(--text-tertiary, #94a3b8);
  font-size: 10px;
  line-height: 1;
  text-align: right;
}

.permission-tree-status.is-all {
  color: var(--success-color, #10b981);
}

.permission-tree-status.is-partial {
  color: var(--warning-color, #f59e0b);
}

.permission-tree-status.is-none {
  color: transparent;
}

.sidebar-legend {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 10px 12px;
  border-top: 1px solid var(--border-light, #f1f5f9);
  background: var(--bg-secondary, #f8fafc);
  color: var(--text-tertiary, #64748b);
  font-size: 11px;
}

.sidebar-legend span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
}

.sidebar-legend i {
  font-size: 13px;
}

.sidebar-legend span:nth-child(1) i {
  color: var(--success-color, #10b981);
}

.sidebar-legend span:nth-child(2) i {
  color: var(--warning-color, #f59e0b);
}

.sidebar-legend span:nth-child(3) i {
  color: var(--text-disabled, #cbd5e1);
}

@media (max-width: 900px) {
  .permission-sidebar {
    max-height: 210px;
    border-right: 0;
    border-bottom: 1px solid var(--border-light, #e2e8f0);
  }
}
</style>
