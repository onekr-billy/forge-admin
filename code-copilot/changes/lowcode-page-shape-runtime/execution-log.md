# 执行记录

## 2026-09-20 排查

- 创建模板有 formOnly 和 objectRef.pageMode，但 GridBlockRenderer 的编译运行分支和静态兜底分支都没有传递 formOnly；门户高度却已经按表单自适应，存在模式/高度不一致。
- 分支 fix/new-page-object-rebind，保留工作区并行改动；不启动/重启服务、不发布页面、不保存业务记录。

## 2026-09-20 实现与增量验证

### 实现

- 新增区块级形态解析 `runtime-crud-page-mode.js`：显式布尔值优先于 pageMode，显式 list/crud/list-form 覆盖旧 pageKey=form；兼容历史对象引用属性、运行配置兜底及空属性。
- 从 GridBlockRenderer 提取运行配置合并到 `runtime-crud-block-props.js`，保留字段筛选、子表列、增强钩子、设计预览标记和表单打开方式优先级。保留字段 schema 按需计算，避免提取后在已有运行配置时重复编译。
- 编译配置、静态预览两条分支都传递 formOnly/表单文案；门户高度与区块形态共用解析，不污染对象级运行配置。
- 巨型存量 SFC 本轮只提取相关配置合并逻辑，未跨域全面重构；因工作区已有重叠改动且存量文件超限，未自动混合提交。

### 命令与证据

在 `forge-admin-ui` 目录，先 `source ~/.nvm/nvm.sh && nvm use v20.19.0`：

```sh
pnpm --ignore-workspace exec vitest run \
  src/components/lowcode-builder/page/__tests__/grid-block-renderer-data-source.spec.js \
  src/components/lowcode-builder/shared/__tests__/runtime-crud-page-mode.spec.js \
  src/components/lowcode-builder/shared/__tests__/runtime-crud-props.spec.js \
  src/views/app-center/components/portal/__tests__/portal-page-runtime-layout.spec.js \
  src/views/app-center/in-app-builder/__tests__/page-shape-design.spec.js

pnpm --ignore-workspace exec eslint \
  src/components/lowcode-builder/page/GridBlockRenderer.vue \
  src/components/lowcode-builder/page/runtime-crud-block-props.js \
  src/components/lowcode-builder/page/__tests__/grid-block-renderer-data-source.spec.js \
  src/components/lowcode-builder/shared/runtime-crud-page-mode.js \
  src/components/lowcode-builder/shared/runtime-crud-props.js \
  src/components/lowcode-builder/shared/__tests__/runtime-crud-page-mode.spec.js \
  src/views/app-center/components/portal/portal-page-runtime-layout.js \
  src/views/app-center/components/portal/__tests__/portal-page-runtime-layout.spec.js

NODE_OPTIONS=--max-old-space-size=8192 pnpm --ignore-workspace build
```

- 定向 5 文件 / 68 tests 全通过；定向 ESLint 通过。
- 初次 Grid 测试收集失败是依赖拉起真实自动路由，已隔离路由依赖；后续 6 个参数化用例缺 `dataSourceConfigured` 导致渲染来源提示，补齐真实前提后 18 项全部通过，未更改生产逻辑绕过测试。
- 扩展命令追加 `src/components/lowcode-builder/page/__tests__/page-schema.spec.js`：当时合计 70 通过、1 失败；失败为 `new Set(visibleTitles).size` 实际 59、预期 63。已在前次子表变更执行记录中存在，目录逻辑本轮未修改。
- 第一轮构建 exit 0，9299 modules、`built in 1m 54s`。警告是 Vite 配置导入路径、既有 CSS `//` 注释、混合静态/动态导入及插件耗时，不阻断构建。
- 最终按需计算微调后复跑：ESLint exit 0、5 文件 / 68 tests 通过（5.29s），生产构建 exit 0；完整构建输出留在 `/tmp/forge-page-shape-check.2CdkdX/build-final.log`，未新增阻断项。
- 仓库根目录 `git diff --check` 通过。

### 只读浏览器验证

- 复用用户已运行的前端 `localhost:3000`、Admin `localhost:8580`，Python Playwright headless Chromium；默认本地账号通过公钥加密登录，令牌仅内存使用。普通浏览器鉴权/请求链路，无内部调用头。
- 用户表单页 `page_page_vhu5rh`：运行配置已加载，实际 AiCrudPage 为 formOnly=true，4 个字段（输入框、扫码输入、数字、滑块）及重置/提交按钮可见；`/ai/crud/` 数据请求 0，页面错误 0。
- 现有列表＋表单 `page_page_oa210w`：首屏 10 行，主字段和子表数字列均可见，非纯表单。
- 应用内未新建真实纯列表页：复制该页运行区块到浏览器独立临时容器，pageMode=list；使用真实 GridBlockRenderer/AiCrudPage 和真实只读列表接口，首屏 10 行、分页可见、无纯表单、页面错误 0。未改响应式页面树，未调用页面保存/发布。
- 临时证据：`/tmp/forge-page-shape-check.2CdkdX/check.py`，`after.png`、`list-form.png`、`list-only.png`；截图不提交到仓库。
- 未点击提交/保存、未执行真实业务写入，也未针对独立已发布应用快照做发布验收；草稿/发布标记和静态提交保护由单测覆盖。
- 本轮未启动服务，因此无服务 PID 需要停止；Playwright 浏览器及临时挂载均已关闭。不需要为本轮前端修复重启 Admin/Flow 或重新发布应用，已有配置刷新即可读取修复后的解释器。
