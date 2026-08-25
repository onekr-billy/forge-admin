# Application Runtime and Center UX Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复低代码应用运行页、应用中心和发布页的导航层级、个人资料、消息通知、响应式布局、旧路由跳转以及发布状态一致性问题。

**Architecture:** 以当前已发布应用快照为唯一运行态来源，统一在应用运行页构造“分组 → 页面”树；应用内顶部工具复用系统消息通知数据源但路由保持在应用上下文；应用中心所有设置/发布入口统一走新的 application workspace 路由，并用发布版本号/草稿状态判断“未发布变更”。响应式修复只调整当前组件布局和断点，不改变业务数据协议。

**Tech Stack:** Vue 3.5、Naive UI、Pinia、Vue Router、Vite、Playwright、Vitest。

---

### Task 1: 运行页页面分组树与个人资料路由

**Files:**
- Modify: `forge-admin-ui/src/views/app-center/application-portal.vue`
- Modify: `forge-admin-ui/src/views/app-center/components/portal/PortalNavigation.vue`
- Modify: `forge-admin-ui/src/layouts/components/UserAvatar.vue`
- Test: `forge-admin-ui/src/views/app-center/__tests__/portal-config.spec.js` or a focused runtime navigation test

- [ ] **Step 1: Write failing tests** for a published builder containing a group node and page nodes, asserting navigation data preserves parent/child structure and profile action stays in application context.
- [ ] **Step 2: Run the focused test** and confirm the current flat page filtering/profile route behavior fails.
- [ ] **Step 3: Implement navigation normalization** so group nodes render as visible non-clickable parents with nested page children, while page selection continues to update `pageId`.
- [ ] **Step 4: Implement scoped profile navigation**: the avatar action in an application portal must route to the application profile/settings entry (or an in-app profile panel), never directly to `/system/profile`; retain the system route only outside an application portal.
- [ ] **Step 5: Run the focused tests** and verify parent/child labels, selected page state, and route output.

### Task 2: Application runtime message notification integration

**Files:**
- Modify: `forge-admin-ui/src/views/app-center/application-portal.vue`
- Modify: `forge-admin-ui/src/layouts/components/MessageNotification.vue`
- Modify: `forge-admin-ui/src/api/message.js` only if an existing API wrapper is missing
- Test: `forge-admin-ui/src/views/app-center/__tests__/application-runtime-load.spec.js` and a focused notification test

- [ ] **Step 1: Write failing tests** asserting the application portal renders the existing message notification component and passes the current application context, while notification click targets preserve the application route.
- [ ] **Step 2: Run the focused tests** to confirm the portal currently renders no notification trigger or loses the application context.
- [ ] **Step 3: Reuse the existing system message APIs/store** in the application portal header; do not create a second notification protocol or hard-code message data.
- [ ] **Step 4: Add route-context handling** for notification detail/list clicks so they return to the current application/page when the message is application-scoped, and to the system message center for global messages.
- [ ] **Step 5: Run tests and inspect browser console** for duplicate polling, unhandled requests, or route warnings.

### Task 3: Application portal and app-center responsive layouts

**Files:**
- Modify: `forge-admin-ui/src/views/app-center/application-portal.vue`
- Modify: `forge-admin-ui/src/views/app-center/suite.[suiteCode].vue`
- Modify: `forge-admin-ui/src/views/app-center/index.vue`
- Modify: `forge-admin-ui/src/views/app-center/components/AppMarketPanel.vue`
- Modify: `forge-admin-ui/src/views/app-center/shared-center.css`
- Test: focused component tests where available; Playwright viewport checks for 375px, 768px, and desktop widths

- [ ] **Step 1: Capture failing screenshots** at the reported routes and narrow viewports, recording bounding boxes for “我创建的” controls, search controls, and market tabs.
- [ ] **Step 2: Add responsive layout rules** using flex wrapping/grid minmax and breakpoint-specific stacking; ensure toolbar actions and search controls occupy separate rows on narrow screens.
- [ ] **Step 3: Fix market tabs** to use a horizontally scrollable or wrapped tab strip with stable item widths and no absolute overlap.
- [ ] **Step 4: Re-run screenshots** at all target widths and assert no intersecting bounding boxes and no horizontal overflow beyond the intended scroll container.

### Task 4: Application settings/publish routing and compact workspace summary

**Files:**
- Modify: `forge-admin-ui/src/views/app-center/index.vue`
- Modify: `forge-admin-ui/src/views/app-center/application-portal.vue` only if runtime links are involved
- Modify: `forge-admin-ui/src/views/app-center/application-designer-navigation.js`
- Modify: `forge-admin-ui/src/views/app-center/application-workspace/ApplicationWorkspaceHeader.vue`
- Modify: `forge-admin-ui/src/views/app-center/application-workspace/ApplicationWorkspaceNav.vue`
- Modify: `forge-admin-ui/src/views/app-center/application-publish.[applicationCode].vue`
- Test: `forge-admin-ui/src/views/app-center/__tests__/application-designer-navigation.spec.js`

- [ ] **Step 1: Write failing route tests** asserting “应用设置” and “发布” navigate to the current application workspace/publish pages, not `/app-center/application/:code/publish` legacy screens.
- [ ] **Step 2: Update route builders** to use the canonical application workspace route and preserve `applicationCode`, `suiteCode`, and selected panel query values.
- [ ] **Step 3: Replace the publish-page resource count summary** with a compact “页面” count only; remove business objects, entries, processes, and extensions from the visible summary while retaining them in readiness validation where required.
- [ ] **Step 4: Run route/component tests** and verify direct navigation keeps the selected application and panel.

### Task 5: Published version and “unpublished changes” consistency

**Files:**
- Modify: `forge-admin-ui/src/views/app-center/application-publish.[applicationCode].vue`
- Modify: `forge-admin-ui/src/views/app-center/components/publish/AppPublishStatusCard.vue`
- Modify: `forge-admin-ui/src/views/app-center/components/publish/AppPublishVersionHistory.vue`
- Modify: `forge-admin-ui/src/views/app-center/index.vue`
- Inspect/modify only if needed: corresponding runtime/publish API wrappers under `forge-admin-ui/src/api/`
- Test: `forge-admin-ui/src/views/app-center/__tests__/application-create-result.spec.js` or a new publish-state contract test

- [ ] **Step 1: Write failing tests** for equal draft/published versions (status must be published) and newer draft/design versions (status must show unpublished changes).
- [ ] **Step 2: Trace the API fields** used by list, workspace, and publish views; normalize numeric/string version values before comparison.
- [ ] **Step 3: Fix status derivation** to compare the actual current draft/design version with `lastPublishVersion` and invalidate stale cached state after a successful publish.
- [ ] **Step 4: Add a post-publish refresh** of application detail/list state and route back to the canonical workspace/publish result.
- [ ] **Step 5: Run tests and manually verify** a publish → refresh → reopen sequence does not show a false “有变更未发布” badge.

### Task 6: Verification and execution record

**Files:**
- Modify: `code-copilot/changes/lowcode-app-portal-productization/execution-log.md`

- [ ] **Step 1: Run focused Vitest tests** for navigation, routing, runtime loading, and publish-state contracts.
- [ ] **Step 2: Run ESLint** on all changed Vue/JS files.
- [ ] **Step 3: Run `pnpm build`** with the project’s Node version.
- [ ] **Step 4: Run Playwright at the reported URLs** if local services and credentials are available; capture screenshots and console/network failures.
- [ ] **Step 5: Append commands, outcomes, warnings, skipped E2E items, and service cleanup to `execution-log.md`.**
