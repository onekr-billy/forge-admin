<template>
  <n-modal
    :show="show"
    :mask-closable="!busy"
    :close-on-esc="!busy"
    :trap-focus="false"
    :auto-focus="false"
    class="flow-task-detail-shell-modal"
    @update:show="emit('update:show', $event)"
  >
    <div class="flow-task-detail-shell" :class="{ fullscreen }">
      <header class="approval-topbar">
        <div class="approval-title-area">
          <div class="approval-status-mark" :class="statusClass">
            <i :class="statusIcon" />
          </div>
          <div class="approval-heading">
            <div class="approval-title-row">
              <span v-if="statusText" class="approval-state-text">{{ statusText }}</span>
              <span v-if="priorityText" class="approval-priority" :class="priorityClass">{{ priorityText }}</span>
              <h3>{{ title || '审批详情' }}</h3>
            </div>
            <div v-if="subtitle" class="approval-subtitle">
              {{ subtitle }}
            </div>
          </div>
        </div>
        <div class="approval-toolbar">
          <slot name="toolbar" />
          <button class="approval-close" :disabled="busy" aria-label="关闭详情" @click="emit('update:show', false)">
            <i class="i-material-symbols:close" />
          </button>
        </div>
      </header>

      <div class="approval-detail-layout">
        <main class="approval-main">
          <slot />
        </main>
        <aside class="approval-aside">
          <div class="approval-record-header">
            <div>
              <div class="approval-record-title">
                {{ recordTitle }}
              </div>
              <div class="approval-record-count">
                {{ records.length > 0 ? `${records.length} 条记录` : '暂无记录' }}
              </div>
            </div>
            <slot name="record-action" />
          </div>
          <div class="approval-records">
            <div v-if="records.length > 0" class="approval-record-list">
              <div
                v-for="(item, index) in records"
                :key="getRecordKey(item, index)"
                class="approval-record-item"
                :class="[getActionClass(item.action), { last: index === records.length - 1 }]"
              >
                <div class="approval-record-rail">
                  <div class="approval-record-icon">
                    <i :class="getActionIcon(item.action)" />
                  </div>
                </div>
                <div class="approval-record-content">
                  <div class="approval-record-line">
                    <div class="approval-record-main">
                      <div class="approval-record-stage">
                        <span class="approval-record-task">{{ item.taskName || getActionText(item.action) }}</span>
                        <span class="approval-record-action">{{ getActionText(item.action) }}</span>
                      </div>
                      <div class="approval-record-user">
                        <UserAvatar :name="getRecordUser(item)" :size="24" />
                        <span>{{ getRecordUser(item) }}</span>
                      </div>
                    </div>
                    <time class="approval-record-time">{{ formatRecordTime(item.completeTime || item.createTime) }}</time>
                  </div>
                  <div v-if="item.comment" class="approval-record-comment">
                    {{ item.comment }}
                  </div>
                  <div v-if="hasApprovalPointResults(item)" class="approval-record-points">
                    <div class="approval-record-points-title">
                      审批要点
                    </div>
                    <p
                      v-for="point in item.approvalPointResults"
                      :key="point.id || point.content"
                      class="approval-record-point"
                    >
                      {{ point.checked ? '已核' : '未核' }} · {{ point.content }}
                    </p>
                  </div>
                  <div v-if="item.signature" class="approval-record-signature">
                    <span>签名</span>
                    <SignatureImage :value="String(item.signature)" compact />
                  </div>
                </div>
              </div>
            </div>
            <n-empty v-else description="暂无审批记录" size="small" />
          </div>
          <slot name="aside-extra" />
        </aside>
      </div>
    </div>
  </n-modal>
</template>

<script setup>
import UserAvatar from '@/components/common/UserAvatar.vue'
import SignatureImage from '@/components/flow/SignatureImage.vue'

defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  statusText: { type: String, default: '' },
  statusClass: { type: String, default: 'default' },
  statusIcon: { type: String, default: 'i-material-symbols:check-circle' },
  priorityText: { type: String, default: '' },
  priorityClass: { type: String, default: '' },
  recordTitle: { type: String, default: '审批记录' },
  records: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  fullscreen: { type: Boolean, default: true },
})

const emit = defineEmits(['update:show'])

function getRecordKey(item, index) {
  return item.id || item.historyId || `${item.taskId || item.taskName || 'record'}-${index}`
}

function hasApprovalPointResults(item) {
  return Array.isArray(item?.approvalPointResults) && item.approvalPointResults.length > 0
}

function normalizeAction(action) {
  return String(action || '').toLowerCase()
}

function getActionClass(action) {
  const value = normalizeAction(action)
  if (['approve', 'approved', 'pass', 'passed', 'start', 'completed'].includes(value))
    return 'success'
  if (['reject', 'rejected', 'terminate', 'terminated', 'cancel'].includes(value))
    return 'error'
  if (['return', 'returned', 'delegate', 'delegated', 'reassign', 'rollback', 'withdraw'].includes(value))
    return 'warning'
  if (['claim', 'pending'].includes(value))
    return 'info'
  return 'default'
}

function getActionIcon(action) {
  const icons = {
    approve: 'i-material-symbols:check',
    approved: 'i-material-symbols:check',
    pass: 'i-material-symbols:check',
    passed: 'i-material-symbols:check',
    start: 'i-material-symbols:add',
    reject: 'i-material-symbols:close',
    rejected: 'i-material-symbols:close',
    return: 'i-material-symbols:keyboard-return',
    returned: 'i-material-symbols:keyboard-return',
    delegate: 'i-material-symbols:arrow-forward',
    delegated: 'i-material-symbols:arrow-forward',
    reassign: 'i-material-symbols:person-edit',
    rollback: 'i-material-symbols:undo',
    terminate: 'i-material-symbols:stop',
    terminated: 'i-material-symbols:stop',
    withdraw: 'i-material-symbols:undo',
    claim: 'i-material-symbols:assignment-ind',
    pending: 'i-material-symbols:schedule',
  }
  return icons[normalizeAction(action)] || 'i-material-symbols:radio-button-unchecked'
}

function getActionText(action) {
  const texts = {
    approve: '已通过',
    approved: '已通过',
    pass: '已通过',
    passed: '已通过',
    start: '提交申请',
    reject: '已驳回',
    rejected: '已驳回',
    return: '已退回',
    returned: '已退回',
    delegate: '已转办',
    delegated: '已转办',
    reassign: '已转派',
    rollback: '已回退',
    terminate: '已终结',
    terminated: '已终结',
    withdraw: '已撤回',
    claim: '已签收',
    pending: '待处理',
  }
  return texts[normalizeAction(action)] || action || '处理中'
}

function getRecordUser(item) {
  return item.assigneeName || item.operatorName || item.userName || item.startUserName || '未知'
}

function formatRecordTime(time) {
  if (!time)
    return '-'
  const value = String(time)
  if (value.includes('T'))
    return value.replace('T', ' ').slice(0, 16)
  return value
}
</script>

<style scoped>
.flow-task-detail-shell-modal {
  width: auto;
}

.flow-task-detail-shell {
  width: min(1420px, calc(100vw - 40px));
  max-height: calc(100vh - 40px);
  overflow: hidden;
  border: 1px solid #e5edf5;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.18);
}

.flow-task-detail-shell.fullscreen {
  width: calc(100vw - 24px);
  height: calc(100vh - 24px);
  max-height: calc(100vh - 24px);
}

.approval-topbar {
  display: flex;
  height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 18px 0 20px;
  border-bottom: 1px solid #edf2f7;
  background: #fff;
}

.approval-title-area {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.approval-status-mark {
  display: inline-flex;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: #fff;
  font-size: 15px;
}

.approval-status-mark.pending,
.approval-status-mark.warning {
  background: #f59e0b;
}

.approval-status-mark.claimed,
.approval-status-mark.info {
  background: #2563eb;
}

.approval-status-mark.success {
  background: #16a34a;
}

.approval-status-mark.error {
  background: #dc2626;
}

.approval-status-mark.default {
  background: #94a3b8;
}

.approval-heading {
  min-width: 0;
}

.approval-title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.approval-title-row h3 {
  min-width: 0;
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-state-text {
  color: #1f2937;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
}

.approval-priority {
  padding: 2px 8px;
  border-radius: 5px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.approval-priority.high,
.approval-priority.warning {
  background: #fef3c7;
  color: #b45309;
}

.approval-priority.urgent,
.approval-priority.error {
  background: #fee2e2;
  color: #b91c1c;
}

.approval-subtitle {
  margin-top: 2px;
  color: #94a3b8;
  font-size: 12px;
}

.approval-toolbar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.approval-close {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #64748b;
  cursor: pointer;
  font-size: 20px;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.approval-close:hover:not(:disabled) {
  background: #f1f5f9;
  color: #0f172a;
}

.approval-close:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.approval-detail-layout {
  display: grid;
  height: calc(100% - 64px);
  min-height: 520px;
  grid-template-columns: minmax(0, 1fr) 392px;
  background: #fff;
}

.approval-main {
  min-width: 0;
  overflow: auto;
  padding: 24px 28px 32px;
  background: #fff;
}

.approval-aside {
  min-width: 0;
  overflow: auto;
  border-left: 1px solid #edf2f7;
  background: #fbfcfe;
  padding: 24px 22px 28px;
}

.approval-record-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.approval-record-title {
  color: #111827;
  font-size: 17px;
  font-weight: 700;
}

.approval-record-count {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.approval-record-list {
  display: flex;
  flex-direction: column;
}

.approval-record-item {
  position: relative;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  column-gap: 12px;
  padding-bottom: 18px;
}

.approval-record-item:not(.last)::before {
  content: '';
  position: absolute;
  top: 31px;
  bottom: 0;
  left: 15px;
  width: 1px;
  background: #dbe4ec;
}

.approval-record-rail {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
}

.approval-record-icon {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe4ec;
  border-radius: 6px;
  background: #fff;
  color: #64748b;
  font-size: 15px;
}

.approval-record-item.success .approval-record-icon {
  border-color: #86efac;
  background: #16a34a;
  color: #fff;
}

.approval-record-item.error .approval-record-icon {
  border-color: #fecaca;
  background: #dc2626;
  color: #fff;
}

.approval-record-item.warning .approval-record-icon {
  border-color: #fde68a;
  background: #f59e0b;
  color: #fff;
}

.approval-record-item.info .approval-record-icon {
  border-color: #bfdbfe;
  background: #2563eb;
  color: #fff;
}

.approval-record-content {
  min-width: 0;
  padding-bottom: 16px;
  border-bottom: 1px solid #edf2f7;
}

.approval-record-item.last {
  padding-bottom: 0;
}

.approval-record-item.last .approval-record-content {
  border-bottom: 0;
  padding-bottom: 0;
}

.approval-record-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.approval-record-main {
  min-width: 0;
}

.approval-record-stage {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.approval-record-task {
  min-width: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  line-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-record-action {
  flex: 0 0 auto;
  padding: 1px 6px;
  border: 1px solid #dbe4ec;
  border-radius: 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}

.approval-record-item.success .approval-record-action {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #16a34a;
}

.approval-record-item.error .approval-record-action {
  border-color: #fecaca;
  background: #fef2f2;
  color: #dc2626;
}

.approval-record-item.warning .approval-record-action {
  border-color: #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.approval-record-item.info .approval-record-action {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.approval-record-user {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.approval-record-user span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-record-time {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 12px;
  line-height: 22px;
  white-space: nowrap;
}

.approval-record-comment {
  margin-top: 12px;
  padding: 9px 10px;
  border: 1px solid #edf2f7;
  border-radius: 6px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.approval-record-points {
  margin-top: 10px;
  padding: 9px 10px;
  border: 1px solid #eadfc9;
  border-radius: 6px;
  background: #fffaf0;
}

.approval-record-points-title {
  color: #9a5700;
  font-size: 12px;
  font-weight: 700;
}

.approval-record-point {
  margin: 6px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.approval-record-signature {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  color: #94a3b8;
  font-size: 12px;
}

:global(.approval-detail-section) {
  padding: 0 0 22px;
  margin-bottom: 22px;
  border-bottom: 1px solid #edf2f7;
}

:global(.approval-detail-section:last-child) {
  margin-bottom: 0;
  border-bottom: 0;
}

:global(.approval-section-header) {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

:global(.approval-section-header i) {
  color: #0ea5a4;
  font-size: 18px;
}

:global(.approval-field-grid) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 72px;
  row-gap: 18px;
}

:global(.approval-field) {
  display: grid;
  min-width: 0;
  grid-template-columns: 92px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}

:global(.approval-field.full) {
  grid-column: 1 / -1;
}

:global(.approval-label) {
  color: #6b7280;
  font-size: 14px;
  line-height: 24px;
}

:global(.approval-value) {
  min-width: 0;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 24px;
  overflow-wrap: anywhere;
}

:global(.approval-value.muted) {
  color: #94a3b8;
  font-weight: 500;
}

:global(.approval-user-inline) {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  vertical-align: top;
}

:global(.approval-note) {
  color: #374151;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
}

:global(.approval-diagram) {
  min-height: 360px;
  overflow: hidden;
}

:global(.approval-action-buttons) {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
}

:global(.approval-form-loading) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 28px 0;
  color: #64748b;
}

:global(.approval-dynamic-form) {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #f8fafc;
}

:global(.approval-form-title) {
  margin-bottom: 12px;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

:global(.approval-warning-section) {
  padding: 16px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
}

@media (max-width: 980px) {
  .flow-task-detail-shell,
  .flow-task-detail-shell.fullscreen {
    width: 100vw;
    height: 100vh;
    max-height: 100vh;
    border-radius: 0;
  }

  .approval-topbar {
    height: auto;
    min-height: 64px;
    align-items: flex-start;
    padding: 14px 14px 12px;
  }

  .approval-title-row {
    flex-wrap: wrap;
  }

  .approval-detail-layout {
    height: calc(100% - 64px);
    grid-template-columns: 1fr;
  }

  .approval-main {
    padding: 18px 16px 22px;
  }

  .approval-aside {
    border-top: 1px solid #edf2f7;
    border-left: 0;
    padding: 18px 16px 22px;
  }

  :global(.approval-field-grid) {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  :global(.approval-field) {
    grid-template-columns: 86px minmax(0, 1fr);
  }
}
</style>
