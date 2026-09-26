# 低代码与流程巨型类扫描

扫描时间：2026-09-25

## 结论

当前低代码/流程域存在明显的巨型类与巨型 SFC。它们已经成为配置协议、发布快照、流程回调和页面渲染互相影响的主要风险源。本轮先完成树形页面和子表排序的功能闭环，不在同一变更中做大规模移动代码；拆分必须以测试保护和稳定接口为前提，分阶段推进。

## 优先级清单

| 优先级 | 文件 | 规模 | 主要问题 | 建议拆分边界 |
| --- | --- | ---: | --- | --- |
| P0 | `forge-server/.../service/businessapp/BusinessFlowService.java` | 9,188 行 | 同时承载绑定、发起、回调、状态修复、变量解析、兼容入口，流程状态修复容易与发布快照耦合 | `FlowBindingService`、`FlowStartService`、`FlowCallbackService`、`FlowStatusRepairService`、`FlowVariableResolver` |
| P0 | `forge-admin-ui/src/components/lowcode-builder/page/ListPageGridDesigner.vue` | 12,917 行 | 模板、栅格拖拽、树配置、CRUD 属性、事件配置和预览状态集中在一个 SFC | `ListTemplatePanel`、`TreeConfigPanel`、`CrudBlockPanel`、`GridInteractionController`、Pinia `listDesignerStore` |
| P0 | `forge-admin-ui/src/views/app-center/components/designer/forge-form-designer/ForgePropertyPanel.vue` | 11,458 行 | 字段、树选项、子表、关联选择器和流程表单权限配置互相耦合 | 按属性域拆 panel，并将选中组件/配置草稿迁入 `formDesignerStore` |
| P1 | `forge-server/.../service/lowcode/LowcodeRuntimeConfigBuilder.java` | 3,287 行 | 页面 schema、树、子表、流程状态字段、运行 API 编译混在一起 | `RuntimeFieldCompiler`、`RuntimeTreeConfigBuilder`、`RuntimeChildTableCompiler`、`RuntimeApiConfigBuilder` |
| P1 | `forge-admin-ui/src/components/lowcode-builder/page/GridBlockRenderer.vue` | 5,592 行 | 所有区块渲染、运行 CRUD、树接口、详情、组件事件集中 | 按 block type 拆 renderer，并抽 `useRuntimeTree`、`useRuntimeCrud` |
| P1 | `forge-server/.../service/businessprocess/validation/BusinessProcessSchemaValidator.java` | 1,219 行 | 节点、表单、变量、策略校验规则集中 | 按节点类型拆 validator，保留统一错误协议 |
| P1 | `forge-server/.../service/businessprocess/BusinessProcessOrchestrator.java` | 1,073 行 | 编排、Flowable 调用、状态机、回调变量组装混合 | `ProcessExecutionService`、`ProcessStateService`、`ProcessVariableMapper` |
| P2 | `forge-server/.../service/lowcode/LowcodePublishService.java` | 999 行 | 发布准备、快照、应用同步和回滚逻辑集中 | `PublishPreparationService`、`PublishSnapshotService`、`PublishRollbackService` |

## 拆分约束

1. 先为流程状态字段、树运行时和发布快照建立契约测试，再移动实现。
2. Service 之间不互相注入；跨域编排上提到 Facade/Orchestrator，底层服务保持单向依赖。
3. 前端跨面板状态必须进入 Pinia，禁止将已有 props/emit 链继续加深。
4. 每次只拆一个边界并保持可编译，拆分后的类名与职责写入对应 Spec；不得借拆分机会改变接口协议。
5. 审批状态字段整列消失应优先放在 `FlowStatusRepairService` + 发布快照契约测试中验证，确认草稿字段、列表选列和发布版本三者一致后再改实现。

## 建议下一阶段顺序

1. 先抽 `FlowStatusRepairService`，补“绑定后未重新发布仍可读取列表列”的回归测试。
2. 再抽 `RuntimeTreeConfigBuilder` 和 `RuntimeChildTableCompiler`，冻结低代码运行配置协议。
3. 最后拆 `ListPageGridDesigner.vue` / `ForgePropertyPanel.vue`，避免在后端协议未稳定时同时修改前端状态链。
