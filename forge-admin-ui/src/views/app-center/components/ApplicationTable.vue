<template>
  <div class="application-card-grid" role="list" aria-label="业务应用列表">
    <article
      v-for="application in applications"
      :key="application.id"
      class="application-card"
      role="listitem"
      tabindex="0"
      @click="emit('enter', application)"
      @keydown.enter.self.prevent="emit('enter', application)"
    >
      <header class="application-card-head">
        <span class="application-icon">
          <IconRenderer v-if="application.icon" :icon="application.icon" :size="16" />
          <i v-else class="i-lucide:layout-dashboard" />
        </span>
        <span class="application-copy">
          <strong>{{ application.applicationName || application.applicationCode }}</strong>
          <code>{{ application.applicationCode }}</code>
        </span>
        <span
          class="application-design-status"
          :class="{ 'is-draft': isDraftApplication(application) }"
        >
          <DictTag
            dict-type="ai_business_application_design_status"
            :value="application.designStatus"
            :bordered="false"
            force-tag
          />
        </span>
      </header>

      <div class="application-tags">
        <span class="application-badge" :title="application.suiteCode">
          {{ application.suiteName || application.suiteCode || '未关联业务域' }}
        </span>
        <span class="application-badge">{{ application.pageCount || 0 }} 个页面</span>
      </div>

      <div class="application-body">
        <div
          class="application-binding"
          :class="{ empty: !application.lastPublishVersion, warning: isUnpublishedApplication(application) }"
        >
          <i class="i-lucide:git-branch" />
          <span v-if="isUnpublishedApplication(application)" class="problem-text">
            有变更未发布
          </span>
          <span v-else-if="application.lastPublishVersion">
            已发布 {{ publishVersionText(application) }}
          </span>
          <span v-else>尚未发布</span>
        </div>
        <p class="application-description">
          {{ application.description || '尚未补充应用说明' }}
        </p>
      </div>

      <footer class="application-card-foot">
        <div class="application-meta">
          <span>
            <i class="i-lucide:calendar" />
            {{ formatDate(application.updateTime) }}
          </span>
          <span>
            <i class="i-lucide:git-commit" />
            {{ publishVersionText(application) }}
          </span>
        </div>

        <div class="application-actions" @click.stop>
          <button type="button" class="application-action-link" @click="emit('enter', application)">
            编辑
          </button>
          <span class="application-action-separator" />
          <button
            v-if="isDraftApplication(application)"
            type="button"
            class="application-action-link"
            @click="emit('publish', application)"
          >
            发布
          </button>
          <button
            v-else
            type="button"
            class="application-action-link"
            @click="emit('run', application)"
          >
            运行
          </button>
          <span class="application-action-separator" />
          <n-dropdown
            trigger="click"
            :options="actionOptions(application)"
            @select="key => handleAction(key, application)"
          >
            <button type="button" class="application-more-action" aria-label="更多应用操作">
              <i class="i-lucide:more-horizontal" />
            </button>
          </n-dropdown>
        </div>
      </footer>
    </article>
  </div>
</template>

<script setup>
import DictTag from '@/components/DictTag.vue'
import IconRenderer from '@/components/IconRenderer.vue'

defineProps({
  applications: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['enter', 'run', 'edit', 'code', 'publish', 'toggle', 'delete'])

function actionOptions(application) {
  const isDraft = isDraftApplication(application)
  return [
    { label: isDraft ? '运行应用' : '发布应用', key: isDraft ? 'run' : 'publish' },
    { label: '预览与下载代码', key: 'code' },
    { label: '应用设置', key: 'edit' },
    { label: Number(application.status) === 1 ? '停用应用' : '启用应用', key: 'toggle' },
    { type: 'divider', key: 'divider' },
    { label: '删除应用', key: 'delete' },
  ]
}

function isDraftApplication(application) {
  const status = String(application?.designStatus || '').toUpperCase()
  return !application?.lastPublishVersion || ['DRAFT', 'READY', 'CHANGED'].includes(status)
}

function isUnpublishedApplication(application) {
  const status = String(application?.designStatus || '').toUpperCase()
  return Boolean(application?.lastPublishVersion) && ['DRAFT', 'READY', 'CHANGED'].includes(status)
}

function publishVersionText(application) {
  return application?.lastPublishVersion ? `v${application.lastPublishVersion}` : '未发布'
}

function handleAction(key, application) {
  if (key === 'enter')
    emit('enter', application)
  else if (key === 'run')
    emit('run', application)
  else if (key === 'publish')
    emit('publish', application)
  else if (key === 'code')
    emit('code', application)
  else if (key === 'edit')
    emit('edit', application)
  else if (key === 'toggle')
    emit('toggle', application)
  else if (key === 'delete')
    emit('delete', application)
}

function formatDate(value) {
  if (!value)
    return '-'
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime()))
    return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
</script>

<style scoped>
.application-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  align-content: start;
  gap: 10px;
  min-width: 0;
  min-height: 100%;
  padding: 10px;
}

.application-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 158px;
  overflow: hidden;
  border: 1px solid var(--n-border-color, var(--border-light, #e5e7eb));
  border-radius: 2px;
  outline: none;
  color: var(--n-text-color, var(--text-primary, #1d2129));
  background: var(--n-color, var(--bg-primary, #fff));
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.16s ease;
}

.application-card:hover,
.application-card:focus-visible {
  border-color: color-mix(in srgb, var(--primary-color, #165dff) 32%, var(--border-default, #c9cdd4));
  box-shadow: 0 7px 18px rgb(15 23 42 / 7%);
  transform: translateY(-1px);
}

.application-card-head {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
  min-width: 0;
  padding: 14px 14px 9px;
}

.application-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid color-mix(in srgb, var(--primary-color, #165dff) 18%, var(--border-light, #e5e7eb));
  border-radius: 2px;
  color: var(--primary-color, #165dff);
  background: color-mix(in srgb, var(--primary-color, #165dff) 7%, var(--bg-primary, #fff));
  font-size: 16px;
}

.application-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding-top: 1px;
}

.application-copy strong,
.application-copy code,
.application-description,
.application-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-copy strong {
  color: var(--n-text-color, var(--text-primary, #1d2129));
  font-size: 14px;
  font-weight: 600;
  line-height: 16px;
}

.application-copy code {
  color: var(--n-text-color-3, var(--text-tertiary, #86909c));
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  line-height: 14px;
}

.application-card-head :deep(.n-tag) {
  max-width: 84px;
  height: 20px;
  border-radius: 2px;
}

.application-design-status.is-draft :deep(.n-tag) {
  color: var(--warning-color, #d46b08);
  background-color: color-mix(in srgb, var(--warning-color, #ff7d00) 14%, transparent);
}

.application-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
  padding: 0 14px 9px;
}

.application-badge {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  height: 20px;
  min-width: 0;
  overflow: hidden;
  border-radius: 2px;
  background: var(--n-color-embedded, var(--bg-secondary, #f7f8fa));
  color: var(--n-text-color-2, var(--text-secondary, #4e5969));
  font-size: 11px;
  line-height: 20px;
  padding: 0 6px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-body {
  display: grid;
  gap: 7px;
  min-width: 0;
  padding: 0 14px 10px;
}

.application-binding {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  height: 28px;
  overflow: hidden;
  border: 1px solid var(--n-border-color, var(--border-light, #eef0f4));
  border-radius: 2px;
  background: var(--n-color-embedded, var(--bg-secondary, #f7f8fa));
  color: var(--n-text-color-3, var(--text-tertiary, #6b7280));
  font-size: 12px;
  line-height: 1;
  padding: 0 8px;
}

.application-binding i {
  flex: 0 0 auto;
  color: var(--n-text-color-3, var(--text-tertiary, #9ca3af));
  font-size: 14px;
}

.application-binding.empty {
  color: var(--n-text-color-3, var(--text-tertiary, #9ca3af));
}

.application-binding.warning {
  border-color: color-mix(in srgb, var(--warning-color, #ff7d00) 20%, var(--border-light, #eef0f4));
  background: color-mix(in srgb, var(--warning-color, #ff7d00) 6%, var(--bg-secondary, #f7f8fa));
}

.application-binding span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-description {
  margin: 0;
  color: var(--n-text-color-3, var(--text-tertiary, #6b7280));
  font-size: 12px;
  line-height: 17px;
}

.application-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: var(--n-text-color-3, var(--text-tertiary, #86909c));
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.application-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.application-meta i {
  flex: 0 0 auto;
  color: var(--n-text-color-3, var(--text-tertiary, #9ca3af));
  font-size: 12px;
}

.application-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  min-width: 0;
  border-top: 1px solid var(--n-border-color, var(--border-light, #eef0f4));
  background: var(--n-color-embedded, var(--bg-secondary, #f7f8fa));
  padding: 8px 14px;
}

.application-actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  justify-content: flex-end;
  gap: 7px;
  min-width: 0;
}

.problem-text {
  overflow: hidden;
  color: var(--warning-color, #d46b08);
  text-overflow: ellipsis;
}

.application-action-link,
.application-more-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  outline: none;
  background: transparent;
  cursor: pointer;
  transition: color 0.16s ease;
}

.application-action-link {
  height: 20px;
  color: var(--primary-color, #165dff);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  padding: 0;
}

.application-action-link:hover,
.application-more-action:hover,
.application-action-link:focus-visible,
.application-more-action:focus-visible {
  color: var(--primary-color-hover, #4080ff);
}

.application-action-separator {
  width: 1px;
  height: 12px;
  background: var(--n-border-color, var(--border-default, #d9dde5));
}

.application-more-action {
  width: 18px;
  height: 20px;
  color: var(--n-text-color-3, var(--text-tertiary, #86909c));
  font-size: 14px;
  padding: 0;
}

:global(.dark) .application-icon {
  border-color: color-mix(in srgb, var(--primary-color, #4080ff) 28%, #303540);
  background: color-mix(in srgb, var(--primary-color, #4080ff) 12%, var(--n-color, #18181c));
}

@media (max-width: 620px) {
  .application-card-grid {
    grid-template-columns: minmax(0, 1fr);
    padding: 8px;
  }

  .application-card {
    min-height: 154px;
  }
}
</style>
