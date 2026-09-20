# Tasks

- [x] T1 复现页面空白并检查 form/list/list-form 配置传递；保存失败测试。
- [x] T2 新增共享页面形态函数与运行配置合并模块，接入 GridBlockRenderer 及门户高度判断。
- [x] T3 回归真实组件 props：表单、纯列表、列表+表单、旧配置和静态预览保护。
- [x] T4 定向测试、ESLint、构建及浏览器验证，记录证据与限制（既有目录标题重复用例单独记录，未掩盖）。

文件：`forge-admin-ui/src/components/lowcode-builder/shared/runtime-crud-page-mode.js`（形态）；`page/runtime-crud-block-props.js`（区块合并）；`page/GridBlockRenderer.vue`（薄入口）；`views/app-center/components/portal/portal-page-runtime-layout.js`（高度）；同域 `__tests__`。

命令（forge-admin-ui，Node v20.19.0）：`pnpm --ignore-workspace exec vitest run src/components/lowcode-builder/page/__tests__/grid-block-renderer-data-source.spec.js src/components/lowcode-builder/shared/__tests__/runtime-crud-props.spec.js src/views/app-center/components/portal/__tests__/portal-page-runtime-layout.spec.js src/views/app-center/in-app-builder/__tests__/page-shape-design.spec.js`，随后定向 ESLint、`pnpm --ignore-workspace build`。
