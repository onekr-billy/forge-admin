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
