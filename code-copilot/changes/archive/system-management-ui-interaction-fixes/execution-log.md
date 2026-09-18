# 执行日志 — 系统管理页面交互一致性修复
> status: complete
> created: 2026-08-08

## 1. 基线

- 当前分支：`fix/system-management-ui-interaction-fixes-20260808`。
- 开始前工作区仅有用户已有的 `.DS_Store` 修改/删除，本变更不处理这些文件。
- 已读取根 `AGENTS.md`、`code-copilot/AGENTS.md`、项目记忆、编码规范、自动化测试标准，以及适用的流程、前端、Forge 编码和浏览器测试 Skill。
- 本地检查时 `8580`、`5173`、`3000` 均无监听服务。

## 2. 研究结论

| 问题 | 根因 | 证据 |
|------|------|------|
| 叶节点显示全部资源 | `currentNode.children` 空值被 `|| allResources` 回退 | `menu.vue#getContextRows` |
| 删除子项后父节点仍提示有下级 | `selectedRow` 在树刷新后仍引用旧节点对象 | `menu.vue#keepSelectionAvailable`、`handleDelete` |
| 选择控件高度错位 | 全局按钮固定高度覆盖 Naive UI 的同尺寸高度变量 | `theme.css`、Naive UI `heightMedium/heightLarge` |
| 组织树底部不可见 | flex 滚动子项收缩约束不一致，内容被上层裁剪 | `org.vue`、`user.vue`、`post.vue`、`MasterDetailWorkspace.vue` |

## 3. 本轮执行记录

| 时间 | 范围 | 命令 | 结果 | 警告/跳过 |
|------|------|------|------|-----------|
| 2026-08-08 | 项目规则与基线 | 读取规则/Skill/记忆/历史验证；检查分支、工作区和监听端口 | passed | 初始位于 `main`，已创建独立修复分支；保留用户 `.DS_Store` 差异 |
| 2026-08-08 | 变更文档 | 创建 `spec.md`、`tasks.md`、`test-spec.md`、`execution-log.md` | passed | 进入 apply |
| 2026-08-08 | 回归测试 Red | `pnpm exec vitest run src/views/system/__tests__/menu-interaction-utils.spec.js src/views/system/__tests__/system-management-ui-contract.spec.js` | expected failed | 菜单工具尚不存在；按钮尺寸与三页滚动契约 4/4 失败 |
| 2026-08-08 | 菜单状态与 UI 契约 Green | 同上 | passed，2 files / 13 tests | 菜单逻辑 9 项、样式契约 4 项 |
| 2026-08-08 | 目标前端文件 | `pnpm exec eslint <本轮 7 个 Vue/JS/测试文件>` | passed | 无输出 |
| 2026-08-08 | 前端生产构建 | `pnpm build` | passed，8879 modules，built in 1m | 既有组件命名冲突、CSS `//` 注释及动态/静态导入警告；无本轮新增构建错误 |
| 2026-08-08 | 补丁完整性 | `git diff --check` | passed | 无输出 |
| 2026-08-08 | 前端开发服务 | Vite 启动于 `127.0.0.1:3000` | passed | 保留 PID `26346` 供用户验收 |
| 2026-08-08 | 浏览器控件几何 | Playwright 隔离脚本读取 large 输入/按钮和 Teleport 挂载 small 按钮边界 | passed | large 输入/按钮均为 `40px`；补充兜底后两个 small 按钮均为 `28px` |
| 2026-08-08 | 浏览器业务链路 | 访问 `http://127.0.0.1:3000/login?redirect=/home` | partial | `8580` 后端未运行，后端请求返回 500；未执行菜单删除与登录后组织树滚动真实 E2E |

## 4. 未执行项与限制

- 本地 `8580` 后端未启动，无法登录并使用真实资源树数据执行“删除子资源后立即删除父资源”的端到端操作。
- 同一限制下，未在登录后的组织、用户、岗位页面手动拖动滚动条；滚动约束由三页静态契约测试和生产构建验证。
- 本变更不涉及后端和数据库，未启动 MySQL、Redis、Admin 或 Flow 服务，也未执行后端测试。

## 5. 服务清理

- 本轮启动服务：前端 Vite，`http://127.0.0.1:3000/`，PID `26346`。
- 本轮停止服务：无。
- 遗留 PID：`26346`，按用户验收需要主动保留；未遗留其他本轮服务。

## 6. 增量：用户管理巨型组件拆分（2026-09-17）

| 时间 | 范围 | 命令 | 结果 | 警告/跳过 |
|------|------|------|------|-----------|
| 2026-09-17 | 拆分前基线 | 读取 `user.vue` 全文（约 4390 行）与规则、Skill、自动化测试标准 | passed | 保持行为、接口、权限不变；排除 AI 功能 |
| 2026-09-17 | 状态测试 Red→Green | `pnpm exec vitest run src/views/system/__tests__/user-management-store.spec.js` | passed，14/14 | Red 阶段 store 尚不存在，符合预期 |
| 2026-09-17 | 组件交互与结构测试 | `pnpm exec vitest run <4 个目标 suite>` | passed，26/26 | 首轮结构测试曾因 `new URL` 被 Vite 重写为 HTTP 失败，改用 `sourceUrl()` 帮助函数后通过 |
| 2026-09-17 | 领域 JS 格式 | `pnpm exec eslint src/stores/system/user-management --fix --rule 'curly: [error, all]' --rule 'style/brace-style: [error, stroustrup, {allowSingleLine: false}]'` | passed | 未改全局规则 |
| 2026-09-17 | 目标 ESLint | `pnpm exec eslint <user.vue+user 目录+store+3 测试>` | passed | 无告警 |
| 2026-09-17 | 前端生产构建 | `pnpm --ignore-workspace build` | passed，built in 49.16s | 仅既有动态/静态导入与 plugin timings 信息 |
| 2026-09-17 | 补丁完整性 | `git diff --check` | passed | 无输出 |
| 2026-09-17 | 行数核查 | `wc -l` 新目录全部文件 | passed | 最大 CSS 623 行、最大 JS 438 行、SFC 全部 ≤121 行，均低于 800 行 |
| 2026-09-17 | 本地服务 | Admin 8580（Java 17，37.9s 启动，health UP）+ 前端 Vite 3002 | passed | 用户先前 5173 无监听，本轮用 3002 |
| 2026-09-17 | 浏览器只读验证 | Browser 子代理执行 A1–C11 清单 | passed（1 项无法执行） | 整体布局、组织树滚动到底、树筛选联动、面板折叠、新增/编辑、关系工作台五页签、批量授权、加入租户、重置密码弹窗全部通过；Console 零 error/warning；暗色模式因无切换入口未执行 |

## 7. 增量：未执行项与限制（2026-09-17）

- 敏感写操作（真实授权提交、保存关系、重置密码、删除用户）未执行；自动化与浏览器均只验证到“打开→观察→取消”。
- 暗色模式未在浏览器验证（当前版本 UI 未提供切换入口）；深浅色规则由样式命名空间保留，未删减。
- 旧请求未实现主动取消：退出页面后旧异步结果仅会落入已脱离 Pinia 的旧实例，该边界已由状态测试层验证。
- 本轮保留服务供用户验收：Admin `8580`、前端 `3002`。

## 8. 增量：系统管理员不受自我维护限制（2026-09-17）

| 时间 | 范围 | 命令 | 结果 | 警告/跳过 |
|------|------|------|------|-----------|
| 2026-09-17 | 身份判定调研 | 核对 `LoginUser.isAdmin()`、`sys_user_type` 字典与前端 `userStore.isAdmin` | passed | 确认“系统管理员”= `userType=0`，前后端判定一致 |
| 2026-09-17 | 后端自我保护调整 | `SysUserServiceImpl.java` 12 处 `assertNotSelfManagement` → `assertNotSelfManagementUnlessAdmin`，并删除无调用者的严格版私有方法 | passed | `bindUserOrg`/`bindUserOrgs` 原本已是管理员放行版本，未改动 |
| 2026-09-17 | 前端限制收敛 | `core.js` 新增 `isRestrictedCurrentUser`；`form.js` 6 处 visible/vIf 与批量选择校验改用该函数 | passed | 初始账号 `id=1` 的禁用/删除按钮保护保留 |
| 2026-09-17 | 4 个目标 suite | `pnpm --ignore-workspace exec vitest run <4 个目标 suite>` | passed，27/27 | 原保护用例改名并补 vIf 断言；新增管理员放行用例 |
| 2026-09-17 | 目标 ESLint | `pnpm --ignore-workspace exec eslint core.js form.js user-management-store.spec.js` | passed | 无告警 |
| 2026-09-17 | 后端编译 | `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl forge-framework/forge-plugin-parent/forge-plugin-system -am compile -DskipTests` | passed | 静默模式无输出 |
| 2026-09-17 | 后端生效链路 | `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-system install -DskipTests` → 重启 Admin `8580` | passed | `~/.m2` jar 12:54 → 17:20；启动约 42s 后 health UP；SIGTERM 无效，`kill -9` 后重启 |
| 2026-09-17 | 浏览器验证 | 用户自行验证 | deferred | 本轮不执行浏览器与真实写操作验证 |

## 9. 增量未执行项与限制（2026-09-17）

- 敏感写路径（管理员保存编辑自己、重置自己密码、批量授权含自己）未执行真实请求，由用户自行验证。
- `spring-boot:run` 的 classpath 从 `~/.m2` 已安装 jar 解析，仅编译 `target/classes` 不等效；本轮已 `install` plugin-system（jar 更新至 17:20）并重启 Admin `8580`（health UP），验证环境已就绪。
