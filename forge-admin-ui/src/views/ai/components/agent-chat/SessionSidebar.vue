<template>
  <div class="chat-sidebar">
    <div class="sidebar-header">
      <!-- 新建对话：先选智能体再创建（创建时绑定，绑定后不可更改） -->
      <NDropdown
        v-if="agentOptions.length"
        :options="agentOptions"
        trigger="click"
        @select="code => emit('create', code)"
      >
        <NButton type="primary" block>
          <template #icon>
            <NIcon><AddOutline /></NIcon>
          </template>
          新建对话
        </NButton>
      </NDropdown>
      <NButton v-else type="primary" block disabled>
        <template #icon>
          <NIcon><AddOutline /></NIcon>
        </template>
        暂无可用智能体
      </NButton>

      <!-- 关键词搜索 -->
      <NInput
        :value="keyword"
        class="sidebar-search"
        size="small"
        clearable
        placeholder="搜索会话"
        @update:value="emit('update:keyword', $event)"
        @input="emit('keyword-input')"
      >
        <template #prefix>
          <NIcon><SearchOutline /></NIcon>
        </template>
      </NInput>
    </div>

    <div class="session-list" @scroll="onScroll">
      <div v-for="group in sessionGroups" :key="group.key" class="session-group">
        <div class="session-group-title">{{ group.label }}</div>
        <div
          v-for="session in group.items"
          :key="session.id"
          class="session-item"
          :class="{ active: currentSessionId === session.id }"
          @click="emit('switch', session.id)"
        >
          <div class="session-info">
            <span class="session-name">{{ session.sessionName || '新对话' }}</span>
            <span class="session-meta">
              <span v-if="agentNameMap[session.agentCode]" class="session-agent">{{ agentNameMap[session.agentCode] }}</span>
              <span class="session-time">{{ formatTime(session.updateTime || session.createTime) }}</span>
              <span v-if="session.pinned" class="session-pinned">已置顶</span>
              <span v-if="session.messageCount" class="session-count">· {{ session.messageCount }} 条</span>
            </span>
          </div>
          <div class="session-ops" @click.stop>
            <NTooltip>
              <template #trigger>
                <NButton quaternary circle size="tiny" @click="emit('toggle-pin', session)">
                  <template #icon>
                    <NIcon size="14"><PinOutline /></NIcon>
                  </template>
                </NButton>
              </template>
              {{ session.pinned ? '取消置顶' : '置顶会话' }}
            </NTooltip>
            <NTooltip>
              <template #trigger>
                <NButton quaternary circle size="tiny" @click="emit('rename', session)">
                  <template #icon>
                    <NIcon size="14"><CreateOutline /></NIcon>
                  </template>
                </NButton>
              </template>
              重命名
            </NTooltip>
            <NPopconfirm @positive-click="emit('delete', session.id)">
              <template #trigger>
                <NButton quaternary circle size="tiny">
                  <template #icon>
                    <NIcon size="14"><CloseOutline /></NIcon>
                  </template>
                </NButton>
              </template>
              确定删除该会话吗？
            </NPopconfirm>
          </div>
        </div>
      </div>

      <div v-if="!sessions.length && !loadingSessions" class="session-empty">
        <NEmpty size="small" description="暂无会话" />
      </div>
      <div v-if="loadingSessions" class="session-loading">
        加载中…
      </div>
      <div v-else-if="!hasMoreSessions && sessions.length" class="session-end">
        没有更多了
      </div>
    </div>
  </div>
</template>

<script setup>
import { AddOutline, CloseOutline, CreateOutline, PinOutline, SearchOutline } from '@vicons/ionicons5'
import { NButton, NDropdown, NEmpty, NIcon, NInput, NPopconfirm, NTooltip } from 'naive-ui'
import { formatTime } from './chat-utils'

defineOptions({ name: 'ChatSessionSidebar' })

defineProps({
  sessions: { type: Array, default: () => [] },
  // 会话分组（父组件 computed，含置顶/今天/近7天/更早）
  sessionGroups: { type: Array, default: () => [] },
  currentSessionId: { type: [String, Number], default: null },
  // 智能体下拉项（「新建对话」时选择要绑定的智能体，NDropdown 以 key 作值）
  agentOptions: { type: Array, default: () => [] },
  // agentCode → 展示名（会话项标注所属智能体）
  agentNameMap: { type: Object, default: () => ({}) },
  // 搜索关键词（v-model:keyword）
  keyword: { type: String, default: '' },
  loadingSessions: { type: Boolean, default: false },
  hasMoreSessions: { type: Boolean, default: false },
})

const emit = defineEmits([
  'create',
  'update:keyword',
  'keyword-input',
  'switch',
  'toggle-pin',
  'rename',
  'delete',
  'load-more',
])

// 触底加载更多（阈值同原实现 60px）
function onScroll(e) {
  const el = e.target
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 60)
    emit('load-more')
}
</script>

<style scoped>
.chat-sidebar {
  display: flex;
  width: 280px;
  flex-direction: column;
  background: var(--bg-soft);
  border-right: 1px solid var(--border);
}

.sidebar-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 14px 12px;
}

.sidebar-header :deep(.n-button) {
  border-radius: 10px;
  font-weight: 500;
}

.sidebar-search :deep(.n-input) {
  border-radius: 10px;
}

.session-list {
  flex: 1;
  padding: 0 8px 8px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(100, 116, 139, 0.3) transparent;
}

.session-list::-webkit-scrollbar {
  width: 10px;
}

.session-list::-webkit-scrollbar-track {
  background: transparent;
}

.session-list::-webkit-scrollbar-thumb {
  background: rgba(100, 116, 139, 0.26);
  border-radius: 8px;
  border: 3px solid transparent;
  background-clip: content-box;
}

.session-list::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 116, 139, 0.42);
  background-clip: content-box;
}

/* 会话时间分组 */
.session-group + .session-group {
  margin-top: 6px;
}

.session-group-title {
  padding: 8px 12px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s ease;
}

.session-item:hover {
  background: var(--primary-light);
}

.session-item.active {
  background: var(--primary-soft);
}

.session-item.active .session-name {
  color: var(--primary);
  font-weight: 600;
}

.session-info {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.session-name {
  font-size: 13px;
  color: var(--text-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 会话所属智能体标签（创建时绑定、不可更改，用作会话区分） */
.session-agent {
  max-width: 96px;
  padding: 0 6px;
  border-radius: 6px;
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-ops {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.session-item:hover .session-ops,
.session-item.active .session-ops {
  opacity: 1;
}

.session-empty {
  padding: 24px 0;
}

.session-loading,
.session-end {
  padding: 10px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
