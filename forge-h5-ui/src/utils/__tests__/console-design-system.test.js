import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const testDir = path.dirname(fileURLToPath(import.meta.url))
const srcDir = path.resolve(testDir, '../..')

function readSource(relativePath) {
  return fs.readFileSync(path.join(srcDir, relativePath), 'utf8')
}

function readProductionStyles() {
  const roots = ['components', 'pages', 'styles']
  const files = []
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const entryPath = path.join(directory, entry.name)
      if (entry.isDirectory()) visit(entryPath)
      else if (/\.(?:vue|scss|css)$/.test(entry.name)) files.push(entryPath)
    }
  }
  roots.forEach(root => visit(path.join(srcDir, root)))
  return files.map(file => fs.readFileSync(file, 'utf8')).join('\n')
}

test('H5 theme exposes the 2025 light-business palette and sizing tokens', () => {
  const source = readSource('styles/theme.css').toLowerCase()
  assert.match(source, /--forge-color-primary:\s*#3b82f6/)
  assert.match(source, /--forge-color-primary-soft:\s*#eff6ff/)
  assert.match(source, /--forge-color-success:\s*#10b981/)
  assert.match(source, /--forge-text-primary:\s*#1e293b/)
  assert.match(source, /--forge-text-secondary:\s*#475569/)
  assert.match(source, /--forge-text-tertiary:\s*#94a3b8/)
  assert.match(source, /--forge-border:\s*#e2e8f0/)
  assert.match(source, /--forge-page-bg:\s*#f4f5f7/)
  assert.match(source, /--forge-surface-muted:\s*#f8fafc/)
  assert.match(source, /--forge-radius-control:\s*12px/)
  assert.match(source, /--forge-radius-card:\s*20px/)
  assert.match(source, /--forge-space-page:\s*16px/)
  assert.match(source, /--forge-control-height:\s*44px/)
  assert.match(source, /--forge-shadow-soft:\s*0 2px 12px rgba\(15, 23, 42, 0\.06\)/)
})

test('global typography uses the cross-platform system font stack', () => {
  const source = readSource('styles/global.css')
  assert.match(source, /system-ui,\s*-apple-system,\s*BlinkMacSystemFont,\s*"Segoe UI",\s*Roboto,\s*sans-serif/)
})

test('shared controls retain 44px touch targets and consistent radii', () => {
  const button = readSource('components/AiButton.vue')
  const field = readSource('components/AiField.vue')
  const tabs = readSource('components/AiTabs.vue')
  const popup = readSource('components/AiPopupSheet.vue')
  assert.match(button, /ai-button--md \{ min-height:\s*44px/)
  assert.match(field, /height:\s*44px/)
  assert.match(tabs, /min-height:\s*44px/)
  assert.match(popup, /border-radius:\s*var\(--forge-radius-popup,\s*24px\)/)
})

test('single-line controls vertically center values, placeholders and icons', () => {
  const field = readSource('components/AiField.vue')
  const search = readSource('components/AiSearchBar.vue')
  const select = readSource('components/AiSelect.vue')
  const datetime = readSource('components/AiDateTimePicker.vue')

  assert.match(field, /wd-input__inner[\s\S]*height:\s*42px[\s\S]*align-items:\s*center/)
  assert.match(field, /uni-input-placeholder[\s\S]*line-height:\s*42px/)
  assert.match(search, /wd-search__field[\s\S]*align-items:\s*center/)
  assert.match(search, /wd-search__search-left-icon[\s\S]*position:\s*static[\s\S]*flex:\s*0 0 16px/)
  assert.match(search, /wd-search__input \.uni-input-placeholder[\s\S]*align-items:\s*center/)
  assert.match(select, /wd-select-picker__cell[\s\S]*align-items:\s*center/)
  assert.match(datetime, /wd-datetime-picker__cell[\s\S]*align-items:\s*center/)
  assert.match(search, /wd-search__block[\s\S]*height:\s*44px/)
  assert.match(select, /wd-select-picker__cell[\s\S]*min-height:\s*44px/)
})

test('native navigation and backend brand resources are used across H5 pages', () => {
  const pages = readSource('pages.json')
  const brand = readSource('utils/tenant-brand.js')
  const login = readSource('pages/login/index.vue')
  const home = readSource('pages/index/index.vue')
  const mine = readSource('pages/mine/index.vue')
  const auth = readSource('store/modules/auth.js')

  assert.doesNotMatch(pages, /"navigationStyle"\s*:\s*"custom"/)
  assert.match(pages, /"path":\s*"pages\/message\/detail"/)
  assert.match(brand, /\/auth\/tenant\/assets\/\$\{encodeURIComponent\(String\(tenantId\)\)\}\/logo/)
  assert.match(auth, /api\.getLoginConfig/)
  assert.match(auth, /state\.userInfo\?\.avatar/)
  assert.match(login, /brandLogoSrc/)
  assert.match(login, /\/static\/images\/login-bg\.png/)
  assert.match(login, /mode="scaleToFill"/)
  assert.doesNotMatch(login, /AiCheckboxGroup|agreementOptions|this\.agreed/)
  assert.match(home, /v-if="rawAvatarUrl"[\s\S]*:src="rawAvatarUrl" :fallback="brandLogoUrl \|\| '\/static\/logo\.png'"/)
  assert.match(home, /v-else class="avatar-image" :src="brandLogoUrl \|\| '\/static\/logo\.png'"/)
  assert.match(mine, /v-if="rawAvatarUrl"[\s\S]*:src="rawAvatarUrl" :fallback="brandLogoUrl \|\| '\/static\/logo\.png'"/)
  assert.match(mine, /v-else class="avatar-large-image" :src="brandLogoUrl \|\| '\/static\/logo\.png'"/)
})

test('authenticated images retry once per file id and deduplicate access-url requests', () => {
  const authImage = readSource('components/AiAuthImage.vue')
  const file = readSource('utils/file.js')

  assert.match(authImage, /let retriedInputKey = ''/)
  assert.match(authImage, /const inputKey = sourceKey\(props\.src\)/)
  assert.match(authImage, /failedSource !== fallbackSource/)
  assert.match(authImage, /retriedInputKey !== inputKey/)
  assert.doesNotMatch(authImage, /retriedSource !== failedSource/)
  assert.match(file, /const fileAccessUrlPending = new Map\(\)/)
  assert.match(file, /fileAccessUrlPending\.has\(rawValue\)/)
  assert.match(file, /fileAccessUrlPending\.set\(rawValue, pending\)/)
  assert.match(file, /fileAccessUrlPending\.delete\(rawValue\)/)
})

test('home uses real metrics, four-column shortcuts and notifications', () => {
  const source = readSource('pages/index/index.vue')
  const skeleton = readSource('components/home/HomeWorkspaceSkeleton.vue')
  const styles = readSource('pages/styles/home.scss')

  assert.match(source, /<HomeWorkspaceSkeleton v-if="workspaceLoading"/)
  assert.match(source, /workspaceLoading\.value = false/)
  assert.match(skeleton, /aria-busy="true"/)
  assert.match(source, /class="home-dashboard"/)
  assert.match(source, /class="overview-list"/)
  assert.match(source, /class="shortcut-section"/)
  assert.match(source, /class="shortcut-item shortcut-more" @click="openMenuSheet"/)
  assert.match(source, /allMenuItems\.value\.slice\(0, 3\)/)
  assert.match(source, /<AiPopupSheet[\s\S]*title="全部应用"/)
  assert.match(source, /class="feed-section"/)
  assert.doesNotMatch(source, /class="attention-grid"/)
  assert.match(styles, /grid-template-areas:\s*\n\s*"overview apps"\s*\n\s*"feed apps"/)
  assert.match(styles, /@media \(max-width: 1023px\)[\s\S]*\.shortcut-grid\s*\{\s*grid-template-columns:\s*repeat\(4,/)
})

test('approval validation scrolls to comments and login is single-flight', () => {
  const detail = readSource('pages/todo-detail.vue')
  const actionLoading = readSource('components/AiLoadingOverlay.vue')
  const login = readSource('pages/login/index.vue')
  const auth = readSource('store/modules/auth.js')

  assert.match(detail, /:scroll-into-view="scrollTarget"/)
  assert.match(detail, /id="approval-comment-panel"/)
  assert.match(detail, /scrollToApprovalComment\(\)/)
  assert.match(detail, /<AiLoadingOverlay :visible="actionLoading" :text="actionLoadingText"/)
  assert.match(detail, /:loading="actionLoading && pendingAction === 'reject'"/)
  assert.match(actionLoading, /<wd-loading/)
  assert.match(login, /if \(this\.loading\)\s*return/)
  assert.match(login, /:disabled="loading"/)
  assert.match(auth, /let passwordLoginPromise = null/)
  assert.match(auth, /if \(passwordLoginPromise\)\s*return passwordLoginPromise/)
  assert.match(auth, /loginConfigRequests/)
})

test('interactive H5 primitives use Wot components without uView or uni-ui fallbacks', () => {
  const main = readSource('main.js')
  const app = readSource('App.vue')
  const uniStyles = readSource('uni.scss')
  const pages = readSource('pages.json')
  const packageJson = readSource('../package.json')
  const popup = readSource('components/AiPopupSheet.vue')
  const select = readSource('components/AiSelect.vue')
  const dropdown = readSource('components/AiDropdownMenu.vue')
  const legacyPage = readSource('components/AiPage.vue')
  const mine = readSource('pages/mine/index.vue')

  for (const source of [main, app, uniStyles, pages, packageJson]) assert.doesNotMatch(source, /uview-plus/)
  assert.match(popup, /<wd-popup/)
  assert.match(select, /<wd-select-picker/)
  assert.match(dropdown, /<wd-select-picker/)
  assert.doesNotMatch(dropdown, /dropdown-overlay/)
  assert.match(legacyPage, /<wd-navbar/)
  assert.doesNotMatch(legacyPage, /<uni-nav-bar/)
  assert.match(mine, /<wd-switch/)
  assert.doesNotMatch(mine, /<switch\b/)
})

test('todo cards claim candidates and navigate directly without a task transit sheet', () => {
  const source = readSource('pages/todo.vue')
  const claimIndex = source.indexOf('await api.claimFlowTask(normalizedTaskId, userId.value)')
  const navigationIndex = source.indexOf('await navigateToTask(`/pages/todo-detail')

  assert.ok(claimIndex >= 0)
  assert.ok(navigationIndex > claimIndex)
  assert.match(source, /@click="openTask\(task\)"/)
  assert.doesNotMatch(source, /<AiPopupSheet\b/)
  assert.doesNotMatch(source, /@click\.stop="claimTask\(task\)"/)
})

test('all key workspaces expose the 1024px desktop breakpoint', () => {
  const files = [
    'pages/styles/login.scss',
    'pages/styles/home.scss',
    'pages/styles/message.scss',
    'pages/styles/todo.scss',
    'pages/styles/todo-detail.scss',
    'pages/styles/mine.scss',
    'pages/styles/lowcode-runtime.scss',
    'components/lowcode/LowcodeRuntimeList.vue',
  ]
  for (const file of files) {
    assert.match(readSource(file), /@media\s*\(min-width:\s*1024px\)/, `${file} should define the desktop H5 layout`)
  }
})

test('production page styles use only the reference palette for decorative gradients', () => {
  const source = readProductionStyles().toLowerCase()
  assert.doesNotMatch(source, /#(?:4266f7|165dff|1f5fbf)\b/)
  assert.match(source, /linear-gradient\s*\(/)
})

test('three-tab navigation and multi-condition filters follow the mobile contract', () => {
  const pages = readSource('pages.json')
  const tabbar = readSource('components/AiTabBar.vue')
  const todo = readSource('pages/todo.vue')
  const message = readSource('pages/message/index.vue')
  const runtime = readSource('components/lowcode/LowcodeRuntimeList.vue')

  assert.doesNotMatch(pages, /"pagePath":\s*"pages\/message\/index"/)
  for (const key of ['home', 'todo', 'mine']) assert.match(tabbar, new RegExp(`key: '${key}'`))
  assert.doesNotMatch(tabbar, /key: 'message'/)
  assert.doesNotMatch(tabbar, /ai-tabbar__badge/)
  assert.match(todo, /<AiFilterSheet[\s\S]*draftCategoryFilter[\s\S]*draftStatusFilter/)
  for (const tone of ['blue', 'orange', 'emerald', 'purple', 'cyan', 'rose']) {
    assert.match(todo, new RegExp(`tone-${tone}`))
  }
  assert.match(todo, /statusToneClass\(task\)/)
  assert.match(message, /<AiFilterSheet[\s\S]*draftReadFilter/)
  assert.match(runtime, /searchFields\.length > 1[\s\S]*<AiFilterSheet/)
  assert.match(message, /buildFlowTaskDetailUrl\(taskId, resolveFlowMessageMode\(message\), message\.id\)/)
})

test('authenticated workspaces keep controls compact and avoid clipped nested sheets', () => {
  const todo = readSource('pages/todo.vue')
  const todoStyle = readSource('pages/styles/todo.scss')
  const detail = readSource('pages/todo-detail.vue')
  const summary = readSource('components/flow/TodoTaskSummary.vue')
  const detailStyle = readSource('pages/styles/todo-detail.scss')
  const select = readSource('components/AiSelect.vue')
  const datetime = readSource('components/AiDateTimePicker.vue')
  const homeStyle = readSource('pages/styles/home.scss')
  const global = readSource('styles/global.css')

  assert.match(todo, /class="todo-tools"[\s\S]*class="todo-list"/)
  assert.doesNotMatch(todo, /class="todo-header"/)
  assert.doesNotMatch(todo, /class="todo-title"/)
  assert.match(todo, /<AiSelect[\s\S]*v-model="draftCategoryFilter"[\s\S]*v-model="draftStatusFilter"/)
  assert.match(select, /<wd-select-picker[\s\S]*root-portal/)
  assert.doesNotMatch(todo, /task-card__meta-grid/)
  assert.match(todoStyle, /\.task-card__footer[\s\S]*min-height:\s*44px/)
  assert.match(todoStyle, /\.claim-button[\s\S]*width:\s*64px[\s\S]*height:\s*36px[\s\S]*line-height:\s*1/)
  assert.match(detail, /<TodoTaskSummary :task="task" @refresh="refresh"/)
  assert.match(summary, /class="task-summary__refresh"[\s\S]*emit\('refresh'\)/)
  assert.match(detailStyle, /\.user-row\s*\{[^}]*line-height:\s*1\.4/)
  assert.match(detailStyle, /\.user-meta\s*\{[^}]*overflow-wrap:\s*anywhere/)
  assert.match(detailStyle, /\.lowcode-field--compact-row[\s\S]*grid-template-columns:\s*78px minmax\(0, 1fr\)/)
  assert.match(select, /:z-index="10010"/)
  assert.match(datetime, /:z-index="10010"/)
  assert.match(homeStyle, /\.overview-item\s*\{[^}]*line-height:\s*1\.4/)
  assert.match(global, /body::-webkit-scrollbar-thumb/)
})

test('query pages refresh their actual scroll surface and approval loads managed comment phrases', () => {
  const pages = readSource('pages.json')
  const home = readSource('pages/index/index.vue')
  const todo = readSource('pages/todo.vue')
  const message = readSource('pages/message/index.vue')
  const runtime = readSource('pages/lowcode-runtime.vue')
  const layout = readSource('components/AiLayoutPage.vue')
  const detail = readSource('pages/todo-detail.vue')
  const phraseInput = readSource('components/flow/FlowCommentPhraseInput.vue')
  assert.match(pages, /"path": "pages\/index\/index"[\s\S]*?"enablePullDownRefresh": true/)
  assert.match(home, /onPullDownRefresh[\s\S]*refreshWorkspace/)
  assert.doesNotMatch(home, /fallbackMenuItems/)
  assert.match(todo, /refresher-enabled[\s\S]*@refresherrefresh="refreshByPull"/)
  assert.match(message, /onPullDownRefresh[\s\S]*await refresh\(\)/)
  assert.match(runtime, /:refresher-enabled="mode === 'list'"[\s\S]*@refresh="refreshListByPull"/)
  assert.match(layout, /:refresher-triggered="refreshing"/)
  assert.match(detail, /class="detail-scroll"[\s\S]*scroll-y[\s\S]*:show-scrollbar="true"/)
  assert.match(detail, /<FlowCommentPhraseInput/)
  assert.match(phraseInput, /api\.listUsableCommentPhrases/)
  assert.match(phraseInput, /api\.listMyCommentPhrases/)
  assert.match(phraseInput, /api\.createCommentPhrase/)
  assert.match(phraseInput, /api\.deleteCommentPhrase/)
})

test('approval detail combines progress and history with localized display helpers', () => {
  const detail = readSource('pages/todo-detail.vue')
  const trace = readSource('components/flow/TodoFlowTrace.vue')
  const summary = readSource('components/flow/TodoTaskSummary.vue')
  const todo = readSource('pages/todo.vue')
  const lowcodeTrace = readSource('components/lowcode/LowcodeFlowTimeline.vue')
  assert.match(detail, /class="detail-section-title">审批流程/)
  assert.match(detail, /<TodoFlowTrace mode="process"[\s\S]*<TodoFlowTrace mode="history"/)
  assert.doesNotMatch(detail, /<AiTabs|<AiTab/)
  assert.match(trace, /formatFlowStatus\(item\.status \|\| item\.statusText\)/)
  assert.match(trace, /formatFlowDateTime\(time\)/)
  assert.match(summary, /formatFlowDateTime\(task\.createTime \|\| task\.startTime\)/)
  assert.match(todo, /formatFlowDateTime\(task\.createTime \|\| task\.startTime\)/)
  assert.match(lowcodeTrace, /formatFlowDateTime\(item\.completeTime/)
})

test('approval detail only renders business fields returned by task form context', () => {
  const api = readSource('api/index.js')
  const detail = readSource('pages/todo-detail.vue')

  assert.match(detail, /context\?\.taskFormInfo[\s\S]*formInfo\.value = context\.taskFormInfo/)
  assert.match(detail, /businessContextError\.value = resolveErrorMessage/)
  assert.match(detail, /已停止渲染动态表单/)
  assert.match(detail, /const mainFields = computed\([\s\S]*return \[\]/)
  assert.match(detail, /v-else-if="showBusinessFormPanel" class="content-panel"/)
  assert.equal((detail.match(/await loadBusinessContext\(/g) || []).length, 1)
  assert.equal((detail.match(/await loadReadonlyBusinessContext\(/g) || []).length, 1)
  assert.doesNotMatch(detail, /hydrateBusinessContextFromAssets/)
  assert.doesNotMatch(detail, /replaceMainData\(formInfo\.value\?\.variables\)/)
  assert.doesNotMatch(api, /getBusinessFlowFormAssets/)
})

test('approval actions, message cards and mine scrolling follow the reference mobile layout', () => {
  const api = readSource('api/index.js')
  const detail = readSource('pages/todo-detail.vue')
  const detailStyle = readSource('pages/styles/todo-detail.scss')
  const detailSkeleton = readSource('components/flow/TodoDetailSkeleton.vue')
  const taskSummary = readSource('components/flow/TodoTaskSummary.vue')
  const textarea = readSource('components/AiTextarea.vue')
  const message = readSource('pages/message/index.vue')
  const messageStyle = readSource('pages/styles/message.scss')
  const mine = readSource('pages/mine/index.vue')
  const mineStyle = readSource('pages/styles/mine.scss')

  assert.match(detail, /class="detail-section-title">审批意见<text/)
  assert.doesNotMatch(detail, /class="form-label">处理意见/)
  assert.match(detail, /class="more-action-grid"/)
  assert.match(detail, /canRejectToStart[\s\S]*退回发起人修改/)
  assert.match(detail, /\['approve', 'reject', 'rejectToStart', 'return'\]/)
  assert.match(api, /rejectToStartFlowTask:[\s\S]*\/api\/flow\/task\/reject-to-start/)
  assert.match(detail, /class="more-cancel-button"/)
  assert.match(detail, /<TodoDetailSkeleton v-if="loading"/)
  assert.match(detail, /file-text\.svg[\s\S]*check-circle\.svg[\s\S]*edit-3\.svg/)
  assert.match(detailSkeleton, /detail-skeleton-summary[\s\S]*detail-skeleton-form[\s\S]*detail-skeleton-flow/)
  assert.match(taskSummary, /file-text\.svg[\s\S]*user\.svg[\s\S]*briefcase\.svg[\s\S]*folder\.svg[\s\S]*clock\.svg/)
  assert.match(detailStyle, /\.more-action-grid[^}]*grid-template-columns:\s*repeat\(4,/)
  assert.match(textarea, /font-size:\s*14px !important/)
  assert.doesNotMatch(message, /aria-label="刷新消息"/)
  assert.match(message, /class="message-search-wrap"[\s\S]*aria-label="筛选消息"/)
  assert.match(messageStyle, /\.filter-tab\.active::before/)
  assert.match(messageStyle, /\.message-query-row[^}]*overflow:\s*hidden/)
  assert.match(messageStyle, /\.message-search-wrap[^}]*width:\s*0[^}]*min-width:\s*0[^}]*flex:\s*1/)
  assert.match(messageStyle, /\.message-list[\s\S]*gap:\s*12px/)
  assert.match(messageStyle, /\.message-card[\s\S]*border-radius:\s*var\(--radius-card\)/)
  assert.match(mine, /class="mine-scroll" scroll-y :show-scrollbar="true"/)
  assert.match(mineStyle, /\.mine-scroll[^}]*overflow-y:\s*auto/)
})
