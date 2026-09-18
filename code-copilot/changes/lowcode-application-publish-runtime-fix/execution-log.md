# 执行记录

## 2026-09-18

- 已定位正式运行页误判：`GridBlockRenderer` 仅依据 `previewLiveData` 判断静态预览，没有区分 `runtimeInteractive`。
- 已确认发布 CRUD 配置通过 `buildRuntimeCrudProps` 携带 `childrenConfig`。
- 已定位子表 Schema 遍历缺口：后端直接遍历 `forms` 容器，但未进入 `forms[*].schema.components`。
- 已修复静态预览判定：正式 `runtimeInteractive` 页面不再受设计器 `previewLiveData` 开关约束。
- 已补齐根组件、嵌套 children 和多表单 schema 的子表遍历，并在发布前从持久化表单恢复自动关系。
- 应用发布检查和最终发布会准备全部关联对象；对象步骤增加 `PUBLISHED + 发布版本` 后置校验。
- 应用不可变快照会把成功发布对象状态回填为 `PUBLISHED`，同时记录 `publishedDesignVersionId`。
- 前端定向测试：7 个测试文件、65 个测试全部通过。
- 前端变更文件 ESLint：通过。
- 前端生产构建：通过；仅有仓库存量 Vite/CSS 警告。
- 全量前端 ESLint：未通过，存在 450 个仓库存量错误和 161 个仓库存量警告；本次变更文件定向检查无问题。
- `git diff --check`：通过。
- 后端定向测试/编译未执行：当前主机没有 Java 运行时和 Maven 命令。
