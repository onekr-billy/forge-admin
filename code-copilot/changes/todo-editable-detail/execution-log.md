# 待办中的可编辑明细 Execution Log

## 2026-09-18：SDD 提案初始化

- 状态：已创建 `spec.md`、`tasks.md`、`test-spec.md`、`execution-log.md`。
- 范围：第一期路线 A；动态表单数组路线 B 暂不编码。
- 初始排查：确认 `todo.vue` 固定 `ChildTableEditor readonly`，`BusinessFlowService.saveTaskFormContext` 只更新主表，节点权限目录没有子表字段，`DynamicCrudService` 普通 CRUD 虽支持主子表但待办未接入。
- 工作区：保留用户已有未提交修改；本轮只新增当前变更文档，未修改业务代码。
- 分支：`main`（编码规范仅禁止 `master`，当前分支可继续）。
- 验证：尚未执行实现阶段测试。

## 2026-09-18：进入实现阶段

- 任务：开始 T1，先扩展节点权限协议的前端 parser/writer 和权限配置组件。
- 提交：按仓库规范尝试独立 commit，但当前环境无法写入 `.git/index`，后续以工作区差异和执行日志留痕。

## 2026-09-18：主子表待办编辑实现与增量验证

- 实现：完成路线 A。待办上下文、节点权限目录、ChildTableEditor 和服务端任务保存均支持主子表；路线 B 动态表单数组字段仍未实现。
- 兼容修正：历史/已办上下文递归锁定子表字段和行操作；子表关系键统一优先 `modelCode/relationKey`，服务端兼容 `key/tableName` 别名并将 payload 归一化到运行关系。
- 前端测试：`FormPermissionConfig.spec.js`、`user-task-parser-permissions.spec.js`、`DingFlowDesigner.spec.js`、`ChildTableEditor.spec.js`，4 个测试文件、37 tests passed。
- 前端静态检查：定向 ESLint 0 errors、0 warnings（Node v20.19.0）。
- 前端构建：`pnpm --ignore-workspace build` 通过；仅有 Vite 原有 configLoader/CSS 注释和动态 import 警告。
- 后端编译：JDK 17 下 `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests` 通过；开启测试编译时主代码仍通过。
- 后端测试阻断：开启 `forge.tests.skip=false` 的 Maven 测试命令在测试源码编译阶段失败，原因是既有 `FormulaExecutionEngineTest` 编码错误，以及 `DynamicCrudRepositoryTest`、`DbAggregateDataProviderTest` 构造器参数与当前主代码不匹配；未进入本变更目标测试执行。
- 后端最终复核：会话 `30511` 完成，JDK 17 下 `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests` 返回 `BUILD SUCCESS`（32 个 reactor 模块，约 45 秒）。
- 服务级验证：未启动 Admin/Flow，未修改数据库或运行中流程；真实待办 E2E 留给用户开发环境补验。
- 工作区：未能提交 commit，`.git/index.lock` 写入被沙箱拒绝；保留全部工作区改动。
