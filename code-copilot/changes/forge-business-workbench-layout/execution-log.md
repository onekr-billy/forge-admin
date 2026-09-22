# 执行记录

## 2026-09-22 初始化

- 基线：`origin/main`，提交 `43402b8b`。
- 工作树：`/tmp/forge-admin-business-workbench`。
- 已核对：现有默认布局为 `top-menu`，租户数据库默认值为 `default`，最新 Flyway 版本为 `1.0.180`。

## 2026-09-22 增量验证

- 变更范围：业务工作台布局、布局设置、租户外观配置、前端默认值、Flyway 默认布局迁移。
- `node node_modules/vitest/vitest.mjs run --no-cache src/layouts/business-workbench/__tests__/menu-model.spec.js src/layouts/business-workbench/__tests__/settings.spec.js`：2 个测试文件、12 项测试全部通过。
- `node node_modules/eslint/bin/eslint.js ...`：本轮 Vue、JS、CSS 文件无错误。
- `NODE_OPTIONS=--max-old-space-size=8192 node node_modules/vite/bin/vite.js build`：生产构建通过；存在仓库既有 CSS `//` 注释及动态导入提示，不阻断本变更。
- `git diff --check`：通过。
- Flyway 静态检查：`V1.0.181` 版本唯一，脚本无 `${...}` 占位符。
- 未连接数据库：本轮通过版本化迁移交付，由目标环境启动 Admin 时执行。
- 未启动服务，无需清理服务进程。

## 2026-09-22 菜单定位增量同步

- 基线：最新 `origin/main`，提交 `242922b0`；通过独立 worktree 实施，未触碰原工作目录中的 H5 未提交改动。
- 同步范围：业务工作台菜单面板父级锚定、固定 500px 高度、桌面三列、左右区域独立滚动、切换动画稳定化和 640px 窄屏全宽回退。
- 保留 Forge Admin 的 `business-workbench` 通用命名、租户主题变量和动态权限菜单，不引入 LawHub 业务文案。
- `node node_modules/eslint/bin/eslint.js src/layouts/business-workbench/components/WorkbenchMegaPanel.vue`：通过。
- `node node_modules/vitest/vitest.mjs run --no-cache ...`：业务工作台 2 个测试文件、12 项测试全部通过。
- 菜单布局静态检查：锚点、固定高度、桌面三列、无嵌套离场动画、640px 回退等 7 项约束通过。
- `node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build`：生产构建通过；仅有仓库既有 CSS 注释和动态导入提示。
- `git diff --check`：通过。
