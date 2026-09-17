# 执行记录

## 2026-09-17 根因验证

- 变更前线上命令：`curl -sS -I http://www.dlforgelab.com:8084/forge/assets/storage-config-bQDZWntM.js`
- 结果：`HTTP/1.1 200 OK`、`Content-Type: text/html`、`Content-Length: 13396`，与 `/forge/` 首页响应一致，确认缺失的旧 chunk 被 SPA fallback 错误返回为 HTML。
- 环境说明：项目记录的 Node `v20.19.0` 当前未安装；现有 Node 为 `v24.21.0`、pnpm 为 `11.19.0`。
- 初次测试尝试因依赖未安装且沙箱无法访问 npm registry 中止；该次产生的未完成 `node_modules` 和仓库内 `.pnpm-store` 已清理。

## 2026-09-17 增量验证

- 依赖准备：`pnpm install --frozen-lockfile --fetch-timeout 300000 --network-concurrency 4` 完成 1332 个包落盘；命令因 pnpm 报告若干存量依赖构建脚本未批准而返回非零，但 Vite、Vitest、ESLint 及本次所需依赖均已安装。未执行 `pnpm approve-builds`，避免改变项目依赖策略。
- 定向单测：`node_modules/.bin/vitest run src/utils/__tests__/dynamic-import-recovery.spec.js`，4 个测试全部通过，包含存储不可用时不刷新以避免死循环的边界。
- ESLint：`node_modules/.bin/eslint src/main.js src/router/index.js src/utils/dynamic-import-recovery.js src/utils/__tests__/dynamic-import-recovery.spec.js`，通过，无输出。
- 生产构建：`NODE_OPTIONS=--max-old-space-size=8192 node node_modules/vite/bin/vite.js build --mode prod`，最终复跑通过，9224 个模块完成转换；生成 `dist/assets/storage-config-Biojf3z3.js`。存在项目既有的 CSS `//` 注释和无效动态导入警告，不阻断构建。
- 补丁检查：`git diff --check`，通过。
- Nginx 运行时语法检查：跳过；当前机器未安装 `nginx`，也没有 Docker CLI。配置使用标准 `location/root/try_files/add_header` 指令，需在镜像构建或部署流水线执行 `nginx -t`。
- 线上部署后验证：未执行；本轮未获授权发布镜像。部署后需验证旧 chunk 返回 404、首页禁止缓存、现有哈希资源使用 immutable 缓存。
- 服务清理：本轮未启动后端、Vite 或 Nginx 常驻服务，无需清理 PID。

## 2026-09-17 全部工程改动推送前复验

- 用户授权将当前工程全部代码修改提交到 origin/main；先无冲突快进同步远端 a856ecab。
- 复跑现有动态导入恢复单测：`node node_modules/vitest/vitest.mjs run src/utils/__tests__/dynamic-import-recovery.spec.js`，4/4 通过。
- 本变更入口、Router、工具及单测文件的定向 ESLint 通过；与文件管理改版合并执行 `node --max-old-space-size=8192 node_modules/vite/bin/vite.js build --mode prod`，25.78 秒构建成功。
- 复用既有 Nginx 检查边界；未进行镜像部署、真实后端联调或新增常驻服务。其余推送前检查见 file-list-refinement/execution-log.md。
