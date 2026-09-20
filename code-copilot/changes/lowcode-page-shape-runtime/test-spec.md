# 测试计划

复用已有 runtime-crud-props / grid-block-renderer-data-source / portal-page-runtime-layout / page-shape-design 测试，不从零构造新测试框架。

- P0：formOnly 及 objectRef.pageMode=form 实际传至 AiCrudPage，字段/schema 不丢；纯列表覆盖运行配置 formOnly，不自动开表单；list-form 保留已有 formOpenMode。
- P0：无运行配置预览仍按表单形态显示；静态预览 beforeSubmit 拦截；正式 runtimeInteractive 不覆盖提交增强钩子（mock 钩子单测；复用已有 AiForm mock API 提交测试，不声称真实业务落库验证）。
- P1：显式 false、旧 pageKey、同对象多区块不修改共享运行配置；高度与渲染模式一致。
- P1：现有字段、子表列、草稿 API 标记测试回归；ESLint、生产构建、git diff --check。
- 浏览器：打开用户页面截图，检查表单/列表 DOM 与请求，不保存数据。没有实际纯列表页时用浏览器内存 fixture 验证并明确区分。

## 本轮增量验证

- 新增 `runtime-crud-page-mode.spec.js` 14 项，包含空配置/旧别名、模式优先级、文案、共享配置不变及静态/交互提交钩子边界。
- `grid-block-renderer-data-source.spec.js` 使用真实 AiCrudPage props 定义、替换子组件实现，验证编译与兜底两条传参链路；用浏览器补真实子组件渲染。
- 扩展执行 `page-schema.spec.js`：6 项通过，1 项既有目录标题唯一性失败。前次 `lowcode-child-list-columns/execution-log.md` 已记录同一问题；不改目录、不屏蔽断言。
