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

## 2026-09-22 增量验证

- 范围：修复 `GET /ai/crud-config/render/{configKey}?designPreview=true` 在物化子表关系时的 MySQL `Lock wait timeout`，并补齐契约测试构造器参数。
- JDK：使用 `/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home`。
- 主代码编译：`mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile -q`，通过。
- 定向契约测试：`mvn -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessApplicationDraftPreviewContractTest -Dsurefire.failIfNoSpecifiedTests=false test -q`，通过（5 tests）。为绕过仓库既有的 `DynamicCrudPrintReadTest` 构造器失配，测试执行期间临时补入一个 `null` 参数，验证后已恢复该无关文件。
- 差异检查：`git diff --check`，通过。
- 未执行：真实 Admin/MySQL 并发接口压测；按用户约定不启动服务、不改动业务数据库。全量 generator 测试仍受既有 `DynamicCrudPrintReadTest` 构造器失配阻断。
