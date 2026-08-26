<template>
  <section class="flow-task-card-list">
    <div class="task-list-toolbar">
      <div class="task-list-titlebar">
        <div class="task-list-title">
          {{ title }}
        </div>
        <div class="task-list-divider" />
        <template v-if="selectedKeys.length > 0">
          <span class="task-list-selected">已选 {{ selectedKeys.length }} 项</span>
          <slot name="batch-actions" :selected-keys="selectedKeys" />
          <button class="task-list-clear" type="button" @click="clearSelection">
            清空
          </button>
        </template>
        <span v-else class="task-list-count">共 {{ pagination?.itemCount ?? items.length }} 项</span>
      </div>

      <div class="task-list-tools">
        <n-input
          v-if="showSearch"
          :value="searchValue"
          :placeholder="searchPlaceholder"
          clearable
          class="task-list-search"
          @update:value="emit('update:searchValue', $event)"
          @keydown.enter="emit('search')"
        >
          <template #prefix>
            <i class="i-material-symbols:search" />
          </template>
        </n-input>
        <slot name="filters" />
        <n-button quaternary class="task-list-icon-btn" title="刷新" aria-label="刷新列表" @click="emit('refresh')">
          <n-icon :size="18">
            <RefreshOutline />
          </n-icon>
        </n-button>
      </div>
    </div>

    <div class="task-table-header">
      <div class="task-table-cell task-table-entity">
        <button
          v-if="selectable"
          type="button"
          class="task-table-check"
          :class="{ checked: allCurrentPageSelected, indeterminate: partiallySelected }"
          aria-label="选择当前页"
          @click.stop="toggleCurrentPage(!allCurrentPageSelected)"
        >
          <i v-if="allCurrentPageSelected" class="i-material-symbols:check-small-rounded" />
          <i v-else-if="partiallySelected" class="i-material-symbols:remove-rounded" />
        </button>
        <span>{{ entityTitle }}</span>
      </div>
      <div class="task-table-cell">
        {{ statusTitle }}
      </div>
      <div class="task-table-cell">
        {{ nodeTitle }}
      </div>
      <div class="task-table-cell">
        {{ userTitle }}
      </div>
      <div class="task-table-cell task-table-actions-head">
        {{ actionTitle }}
      </div>
    </div>

    <n-spin :show="loading" class="task-table-spin">
      <div v-if="items.length > 0" class="task-table-body">
        <article
          v-for="item in items"
          :key="getRowKey(item)"
          class="task-table-row"
          :class="{ selected: isSelected(item), unread: isUnread(item) }"
          @click="emit('rowClick', item)"
        >
          <div class="task-table-cell task-table-entity">
            <button
              v-if="selectable"
              type="button"
              class="task-table-check"
              :class="{ checked: isSelected(item) }"
              aria-label="选择行"
              @click.stop="toggleRow(item, !isSelected(item))"
            >
              <i v-if="isSelected(item)" class="i-material-symbols:check-small-rounded" />
            </button>
            <div class="task-table-title-block">
              <button type="button" class="task-table-title" @click.stop="emit('rowClick', item)">
                <slot name="title" :row="item">
                  {{ item.title || item.taskName || '-' }}
                </slot>
              </button>
              <div class="task-table-subtitle">
                <slot name="identifier" :row="item">
                  {{ getRowIdentifier(item) }}
                </slot>
              </div>
              <div v-if="$slots.summary" class="task-table-summary">
                <slot name="summary" :row="item" />
              </div>
            </div>
          </div>

          <div class="task-table-cell task-table-status">
            <slot name="status" :row="item" />
          </div>

          <div class="task-table-cell task-table-node">
            <slot name="node" :row="item">
              {{ item.currentNode || item.taskName || item.nodeName || '-' }}
            </slot>
          </div>

          <div class="task-table-cell task-table-user">
            <slot name="user" :row="item">
              <span>{{ item.startUserName || item.userName || '-' }}</span>
              <small>{{ item.createTime || item.submitTime || '-' }}</small>
            </slot>
          </div>

          <div v-if="$slots.actions" class="task-table-cell task-table-actions" @click.stop>
            <slot name="actions" :row="item" />
          </div>
          <div v-else class="task-table-cell task-table-actions" />
        </article>
      </div>
      <div v-else class="task-table-body empty">
        <n-empty class="task-list-empty" :description="emptyText" size="small" />
      </div>
    </n-spin>

    <div v-if="pagination && pagination.itemCount > 0" class="task-list-pagination">
      <div class="task-list-total">
        共 {{ pagination.itemCount }} 条记录
      </div>
      <n-pagination
        :page="pagination.page"
        :page-size="pagination.pageSize"
        :item-count="pagination.itemCount"
        :page-sizes="pagination.pageSizes || [10, 20, 50]"
        :show-size-picker="pagination.showSizePicker !== false"
        @update:page="emit('update:page', $event)"
        @update:page-size="emit('update:pageSize', $event)"
      />
    </div>
  </section>
</template>

<script setup>
import { RefreshOutline } from '@vicons/ionicons5'
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '列表' },
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  pagination: { type: Object, default: null },
  selectedKeys: { type: Array, default: () => [] },
  rowKey: { type: [String, Function], default: 'id' },
  searchValue: { type: String, default: '' },
  searchPlaceholder: { type: String, default: '通过名称搜索' },
  emptyText: { type: String, default: '暂无数据' },
  selectable: { type: Boolean, default: true },
  showSearch: { type: Boolean, default: true },
  unreadKey: { type: String, default: '' },
  entityTitle: { type: String, default: '任务详情' },
  statusTitle: { type: String, default: '当前状态' },
  nodeTitle: { type: String, default: '当前节点' },
  userTitle: { type: String, default: '申请人' },
  actionTitle: { type: String, default: '操作' },
})

const emit = defineEmits([
  'update:selectedKeys',
  'update:searchValue',
  'update:page',
  'update:pageSize',
  'search',
  'refresh',
  'rowClick',
])

const selectedSet = computed(() => new Set(props.selectedKeys))
const currentPageKeys = computed(() => props.items.map(item => getRowKey(item)).filter(key => key !== undefined && key !== null))
const allCurrentPageSelected = computed(() => currentPageKeys.value.length > 0 && currentPageKeys.value.every(key => selectedSet.value.has(key)))
const partiallySelected = computed(() => currentPageKeys.value.some(key => selectedSet.value.has(key)) && !allCurrentPageSelected.value)

function getRowKey(row) {
  if (typeof props.rowKey === 'function')
    return props.rowKey(row)
  return row?.[props.rowKey]
}

function isSelected(row) {
  return selectedSet.value.has(getRowKey(row))
}

function isUnread(row) {
  return props.unreadKey ? row?.[props.unreadKey] === 0 : false
}

function getRowIdentifier(row) {
  return row?.businessKey || row?.processInstanceId || row?.taskId || row?.id || '-'
}

function toggleRow(row, checked) {
  const key = getRowKey(row)
  if (key === undefined || key === null)
    return
  const next = new Set(props.selectedKeys)
  if (checked)
    next.add(key)
  else
    next.delete(key)
  emit('update:selectedKeys', [...next])
}

function toggleCurrentPage(checked) {
  const next = new Set(props.selectedKeys)
  currentPageKeys.value.forEach((key) => {
    if (checked)
      next.add(key)
    else
      next.delete(key)
  })
  emit('update:selectedKeys', [...next])
}

function clearSelection() {
  emit('update:selectedKeys', [])
}
</script>

<style scoped>
.flow-task-card-list {
  box-sizing: border-box;
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--border-light, #e2e8f0);
  border-radius: 6px;
  background: var(--bg-primary, #fff);
  box-shadow: 0 1px 2px rgb(15 23 42 / 3%);
}

:deep(.n-spin-container),
:deep(.n-spin-content) {
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  min-height: 0;
}

.task-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 12px;
  min-height: 50px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-light, #e2e8f0);
  background: var(--bg-primary, #fff);
}

.task-list-titlebar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.task-list-title {
  color: var(--text-primary, #1e293b);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.task-list-divider {
  width: 1px;
  height: 12px;
  background: var(--border-default, #cbd5e1);
}

.task-list-count,
.task-list-selected {
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.task-list-clear {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--primary-color, #2563eb);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
}

.task-list-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.task-list-search {
  width: 240px;
}

.task-list-search :deep(.n-input__input-el) {
  font-size: 12px;
}

.task-list-icon-btn {
  width: 30px;
  min-width: 30px;
  height: 30px;
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 2px;
  background: var(--bg-primary, #fff);
  color: var(--text-secondary, #475569);
  transition:
    border-color 160ms ease,
    color 160ms ease;
}

.task-list-icon-btn:hover {
  border-color: var(--primary-color, #2563eb);
  color: var(--primary-color, #2563eb);
}

:deep(.task-list-icon-btn .n-button__content) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

:deep(.task-list-icon-btn .n-icon) {
  color: currentColor;
}

:deep(.task-list-icon-btn i) {
  font-size: 18px;
  line-height: 1;
}

.task-table-header,
.task-table-row {
  display: grid;
  grid-template-columns:
    minmax(280px, 4fr)
    minmax(112px, 1.35fr)
    minmax(132px, 1.55fr)
    minmax(150px, 1.7fr)
    minmax(150px, 1.7fr);
  gap: 16px;
  align-items: center;
  min-width: 920px;
}

.task-table-header {
  flex: 0 0 auto;
  padding: 9px 14px;
  border-bottom: 1px solid var(--border-light, #e2e8f0);
  background: color-mix(in srgb, var(--bg-secondary, #f8fafc) 82%, var(--bg-primary, #fff));
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
  font-weight: 500;
}

.task-table-spin {
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.task-table-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
}

.task-table-body.empty {
  align-items: center;
  justify-content: center;
}

.task-table-row {
  flex: 0 0 auto;
  padding: 11px 14px;
  border-bottom: 1px solid var(--border-light, #eef2f7);
  background: var(--bg-primary, #fff);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease;
}

.task-table-row:hover {
  background: var(--bg-secondary, #f8fafc);
}

.task-table-row.selected {
  background: color-mix(in srgb, var(--primary-color, #2563eb) 5%, var(--bg-primary, #fff));
}

.task-table-row.unread {
  background: color-mix(in srgb, var(--primary-color, #2563eb) 4%, var(--bg-primary, #fff));
}

.task-table-cell {
  min-width: 0;
  overflow: hidden;
}

.task-table-entity {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.task-table-actions-head {
  text-align: right;
}

.task-table-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  margin-top: 3px;
  padding: 0;
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 2px;
  background: var(--bg-primary, #fff);
  color: #fff;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    background-color 150ms ease;
}

.task-table-check:hover {
  border-color: var(--primary-color, #2563eb);
}

.task-table-check.checked,
.task-table-check.indeterminate {
  border-color: var(--primary-color, #2563eb);
  background: var(--primary-color, #2563eb);
}

.task-table-check i {
  font-size: 12px;
  line-height: 1;
}

.task-table-title-block {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.task-table-title {
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary, #1e293b);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 150ms ease;
}

.task-table-title:hover {
  color: var(--primary-color, #2563eb);
}

.task-table-subtitle,
.task-table-user small {
  margin-top: 2px;
  color: var(--text-quaternary, #94a3b8);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-table-summary {
  display: -webkit-box;
  margin-top: 3px;
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
  line-height: 18px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow-wrap: anywhere;
}

.task-table-status {
  display: flex;
  align-items: center;
}

.task-table-node {
  color: var(--text-secondary, #475569);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-table-user {
  display: flex;
  flex-direction: column;
  color: var(--text-secondary, #475569);
  font-size: 12px;
  line-height: 18px;
}

.task-table-user span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-table-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

:global(.task-row-link-action) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--primary-color, #2563eb);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  transition: color 150ms ease;
}

:global(.task-row-link-action:hover) {
  color: color-mix(in srgb, var(--primary-color, #2563eb) 82%, #0f172a);
}

:global(.task-row-link-action.info) {
  color: #2563eb;
}

:global(.task-row-link-action.success) {
  color: #2563eb;
}

:global(.task-row-link-action.danger) {
  color: var(--text-secondary, #475569);
}

:global(.task-row-link-action.danger:hover) {
  color: var(--error-color, #dc2626);
}

:global(.task-row-link-action.muted) {
  color: var(--text-tertiary, #64748b);
}

:global(.task-row-link-action.muted:hover) {
  color: var(--text-secondary, #475569);
}

:global(.task-row-action-separator) {
  width: 1px;
  height: 12px;
  background: var(--border-light, #e2e8f0);
}

:global(.task-row-link-action i) {
  font-size: 14px;
  line-height: 1;
}

.task-list-empty {
  padding: 56px 0;
}

.task-list-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 12px;
  padding: 9px 14px;
  border-top: 1px solid var(--border-light, #e2e8f0);
  background: var(--bg-primary, #fff);
}

.task-list-total {
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
  white-space: nowrap;
}

:global(.task-status-pill) {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 2px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

:global(.task-list-hint) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 7px;
  border: 1px solid var(--border-light, #e2e8f0);
  border-radius: 2px;
  background: var(--bg-primary, #fff);
  color: var(--text-secondary, #475569);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

:global(.task-list-hint.urgent) {
  background: #fff7ed;
  color: #c2410c;
}

:global(.task-list-hint.pending) {
  background: #eff6ff;
  color: #2563eb;
}

:global(.task-status-pill.success),
:global(.task-status-pill.read) {
  background: #ecfdf3;
  color: #15803d;
  border-color: #bbf7d0;
}

:global(.task-status-pill.error),
:global(.task-status-pill.unread) {
  background: #fff1f2;
  color: #be123c;
  border-color: #fecdd3;
}

:global(.task-status-pill.info) {
  background: #eff6ff;
  color: #2563eb;
  border-color: #bfdbfe;
}

:global(.task-status-pill.default) {
  background: #f1f5f9;
  color: #64748b;
  border-color: #e2e8f0;
}

@media (max-width: 900px) {
  .task-list-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .task-list-tools {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .task-list-search {
    width: min(100%, 260px);
  }

  .task-table-header,
  .task-table-row {
    min-width: 820px;
    grid-template-columns:
      minmax(260px, 4fr)
      minmax(100px, 1.2fr)
      minmax(120px, 1.4fr)
      minmax(140px, 1.6fr)
      minmax(140px, 1.6fr);
  }
}
</style>
