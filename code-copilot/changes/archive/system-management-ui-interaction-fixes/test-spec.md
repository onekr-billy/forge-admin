# 单测 Spec — 系统管理页面交互一致性修复
> status: complete
> created: 2026-08-08

## 0. 测试原则

- 复用现有 Vitest、ESLint 和前端生产构建基线，只验证本轮差异。
- 菜单状态逻辑先写失败测试，再实现最小修复。
- CSS 行为使用静态契约保护关键 flex/尺寸属性，最终视觉和滚动能力尽量通过浏览器验证。
- 不启动本轮不需要的后端、Flow、MySQL 或 Redis。

## 1. 测试框架

| 项目 | 值 |
|------|-----|
| 前端单测 | Vitest 2 + jsdom |
| 组件框架 | Vue 3.5 + Naive UI |
| 静态检查 | ESLint 9 + `git diff --check` |
| 集成检查 | Vite production build |

## 2. 覆盖范围

### P0 — 菜单状态逻辑

| 方法 | 场景 | 输入 | 预期结果 |
|------|------|------|----------|
| `resolveResourceContextRows` | 全部资源根上下文 | `currentNode=null` | 返回顶级资源 |
| `resolveResourceContextRows` | 有直接子项 | 当前节点含 `children` | 返回直接子项 |
| `resolveResourceContextRows` | 叶节点 | `children=null/undefined` | 返回空数组 |
| `resolveResourceContextRows` | 当前节点搜索 | `includeDescendants=true` | 仅展开当前节点后代 |
| `resolveFreshResourceRow` | 刷新后资源仍存在 | 旧对象 + 新扁平树 | 返回新对象引用 |
| `resolveFreshResourceRow` | 资源已删除 | 旧对象 + 无对应 ID | 返回 `null` |

### P1 — UI 样式契约

- `theme.css` 的默认、小号、微型和大号按钮高度均使用 `var(--n-height, fallback)`，兜底值分别与 Naive UI 的 `34/28/22/40px` 尺寸一致。
- `org.vue`、`user.vue`、`post.vue` 的 `.org-tree-content` 同时包含 `min-height: 0`、`overflow-y: auto`、`overscroll-behavior: contain` 和 `scrollbar-gutter: stable`。

### P2 — 集成与视觉

- 目标 Vue/JS/测试文件通过 ESLint。
- 前端 production build 通过。
- 前端开发服务可启动；若后端不可用，浏览器验证限定为可访问页面和静态几何检查，并在日志明确说明菜单数据链路未做真实 E2E。

## 3. 执行计划

- [x] Step 1: 执行新增 Vitest，确认菜单回归测试在实现前失败。
- [x] Step 2: 实现菜单修复并确认定向 Vitest 通过。
- [x] Step 3: 实现共享尺寸和三页滚动样式，执行样式契约测试。
- [x] Step 4: 执行目标 ESLint、生产构建和 `git diff --check`。
- [x] Step 5: 启动前端开发服务并执行可用范围内的浏览器检查。

## 4. 历史验证基线

| 时间 | 范围 | 命令 | 结果 | 备注 |
|------|------|------|------|------|
| 2026-07-15 | 前端生产构建 | `pnpm build` | passed | 见 `user-reported-platform-issues/execution-log.md`；当前代码在此后已有提交，不能替代本轮验证 |

## 5. 本轮增量验证

| 时间 | 变更范围 | 必跑项 | 实际命令 | 结果 | 跳过/警告 |
|------|----------|--------|----------|------|-----------|
| 2026-08-08 | 菜单交互与 UI 样式契约 Red | Vitest | `pnpm exec vitest run src/views/system/__tests__/menu-interaction-utils.spec.js src/views/system/__tests__/system-management-ui-contract.spec.js` | failed，菜单工具无法导入；样式契约 4/4 失败 | 预期 Red 基线 |
| 2026-08-08 | 菜单交互与 UI 样式契约 Green | Vitest | 同上 | passed，2 files / 13 tests | 无 |
| 2026-08-08 | 本轮目标文件 | ESLint | `pnpm exec eslint <本轮 7 个 Vue/JS/测试文件>` | passed | 无输出 |
| 2026-08-08 | 前端生产构建 | Vite | `pnpm build` | passed，8879 modules，built in 1m | 既有组件命名冲突、CSS `//` 注释及动态/静态导入警告 |
| 2026-08-08 | 补丁完整性 | Git | `git diff --check` | passed | 无输出 |
| 2026-08-08 | 控件几何 | Playwright | 隔离脚本访问 `http://127.0.0.1:3000` 并读取控件边界 | passed，large 输入/按钮 `40px`，独立挂载 small 按钮 `28px` | `8580` 未启动，未执行登录后菜单和组织树真实 E2E |

## 6. 执行证据

- `execution-log.md`：同目录增量记录。
- 关键接口：接口协议无变更；真实资源树数据验证视本地后端可用性执行。
- 关键数据库检查：无数据库变更，不执行。
- 服务启动与停止：前端 Vite 服务保留在 `http://127.0.0.1:3000/`，PID `26346`，供验收使用。

## 7. 增量验证：用户管理巨型组件拆分（2026-09-17）

### 7.1 新增用例

| Suite | 用例数 | 覆盖内容 |
|-------|--------|----------|
| `user-management-store.spec.js` | 14 | 组织筛选与清除、新增/编辑表单转换与 allowedClients、提交强制登录租户与 userType 保护、字典异步响应式、当前登录用户保护、已拥有角色优先与跨页选择、角色分页参数、批量授权追加不覆盖、单用户授权失败弹窗保留、主组织/主岗位/默认租户约束、空组织不提交、超管租户页签响应、密码草稿清理与组件实例隔离、卸载重进旧请求不污染新 store |
| `user-management-components.spec.js` | 3 交互 + 2 结构 | 首挂载 CRUD 协议与树列表共享状态、关系工作台五页签与角色表、密码弹窗与卸载释放；全部 SFC ≤800 行且位于路由排除目录、领域样式无 `:deep` 且限定命名空间 |

### 7.2 增量验证记录

| 时间 | 变更范围 | 实际命令 | 结果 | 跳过/警告 |
|------|----------|----------|------|-----------|
| 2026-09-17 | 状态测试 Red | `pnpm exec vitest run src/views/system/__tests__/user-management-store.spec.js` | expected failed | store 未实现，符合 TDD Red 基线 |
| 2026-09-17 | 4 个目标 suite | `pnpm exec vitest run user-management-store + user-management-components + user-role-order + system-management-ui-contract` | passed，26/26 | 首轮 2 项结构测试因测试内 `new URL` 被 Vite 改写为 HTTP 失败，修复为 `sourceUrl()` 后通过 |
| 2026-09-17 | 目标 ESLint | `pnpm exec eslint <user.vue+user 目录+store+测试>` | passed | 无告警 |
| 2026-09-17 | 前端生产构建 | `pnpm --ignore-workspace build` | passed，built in 49.16s | 无本轮新增错误 |
| 2026-09-17 | 补丁完整性 | `git diff --check` | passed | 无输出 |
| 2026-09-17 | 浏览器只读验证 | Browser 子代理访问 `http://localhost:3002`（Admin 8580 已启动） | passed | 布局、组织树滚动、树筛选联动、折叠、新增/编辑、五页签工作台、批量授权、重置密码弹窗全部通过；Console 零告警；暗色模式无切换入口未执行 |
| 2026-09-17 | 敏感写操作 | 不执行 | skipped | 授权提交、保存关系、重置密码、删除均留给用户人工验收 |

## 8. 增量验证：系统管理员不受自我维护限制（2026-09-17）

### 8.1 用例调整

| Suite | 用例数 | 变更内容 |
|-------|--------|----------|
| `user-management-store.spec.js` | 14 → 15 | 原“当前登录用户隐藏敏感操作”用例改名“非管理员用户隐藏自己的敏感操作”并补充状态字段 vIf 断言；新增“系统管理员可维护自己：操作可见、状态可改、批量可包含自己”，覆盖五个操作可见性、`id=1` 防自毁保护仍生效、批量授权含自己正常提交 |

### 8.2 增量验证记录

| 时间 | 变更范围 | 实际命令 | 结果 | 跳过/警告 |
|------|----------|----------|------|-----------|
| 2026-09-17 | 4 个目标 suite | `pnpm --ignore-workspace exec vitest run user-management-store + user-management-components + user-role-order + system-management-ui-contract` | passed，27/27 | 无 |
| 2026-09-17 | 目标 ESLint | `pnpm --ignore-workspace exec eslint core.js form.js user-management-store.spec.js` | passed | 无告警 |
| 2026-09-17 | 后端编译 | `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-system -am compile -DskipTests`（Java 17） | passed | 静默模式无输出 |
| 2026-09-17 | 浏览器验证 | 用户自行验证 | deferred | 管理员维护自己的真实交互由用户验收 |
