# 审批表单加载与提交性能优化 Spec

> 变更名：`flow-approval-form-perf`
> 状态：`review`
> 创建日期：2026-09-18

## 1. 需求

用户反馈审批体验差：①打开审批弹窗加载表单特别慢；②页面先后出现两个 loading（"加载表单中"→"加载业务表单中"）；③点审批（同意）也很慢。

## 2. 根因

### 前端 todo.vue

- **串行链**：`openDrawer` 中 `loadTaskFormInfo(taskId)` 完成后才 `await loadBusinessTaskFormContext(row, formInfo)`，再 `await hydrateBusinessFormFromAssets(formInfo)`——三段串行 HTTP，总耗时为三者之和。而待办行自带 taskId/processInstanceId，业务表单上下文接口凭这些字段即可定位，与 formInfo 无硬依赖。
- **两个 loading**：`formInfoLoading`（模板 L284 spinner"加载表单中"）与 `businessFormLoading`（L341 spinner"加载业务表单中"）由串行链先后触发，用户看到两段式转圈。

### 后端 FlowTaskServiceImpl

- **表单加载**（`getTaskFormInfo`）：`populateDirectSendInfo` 用 3 次独立的 `runtimeService.getVariable(processInstanceId, key)` 读直送标记变量，而方法开头 `taskService.getVariables(taskId)` 已全量取过这些流程级变量——纯浪费 3 次 DB 往返。
- **审批提交**（`approve`）：`validateTaskAction` → `getTaskApprovalPolicy(task)`、`validateRequiredVariables` → `getFlowNode(task)`、`validateApprovalPoints` → `getFlowNode(task)`，同一次 approve 里 `getFlowNode(task)`（内含 `repositoryService.getBpmnModel` BPMN 解析 + 节点查找）被重复执行 3 次。

## 3. 方案

### F1 前端并行加载 + 合并 loading（todo.vue）

- `openDrawer` 改为 `Promise.all([loadTaskFormInfo(taskId), loadBusinessTaskFormContext(row, {})])` 并行；formInfo 返回后仅当业务上下文未 configured 时用其精确字段（formKey/recordId/objectCode）兜底重查一次（罕见路径）；`hydrateBusinessFormFromAssets` 保持原条件。
- 新增 `taskFormLoading = computed(() => formInfoLoading || businessFormLoading)`，模板统一只展示这一个 spinner；删除业务表单区独立 spinner；checklist/directSend/empty 条件统一改用 `taskFormLoading`，空态在 loading 期间不闪现。
- 弹窗打开耗时从「formInfo + businessContext (+ assets) 之和」降为「两者最大值」，且全程单一转圈。

### F2 后端消除重复查询（FlowTaskServiceImpl）

- `populateDirectSendInfo` 增加已取变量参数，3 次 `runtimeService.getVariable` 改为从 `variables` Map 读取（这些 key 均为流程级直送标记，`taskService.getVariables(taskId)` 返回的任务上下文全量变量必然包含）。
- approve 路径：一次 `getFlowNode(task)` 解析后复用给 `validateTaskAction`/`validateRequiredVariables`/`validateApprovalPoints`（三个 validate 增加带 FlowNode 的重载，旧签名保留并委托，reject/delegate/terminate 等其他调用点行为不变）。

### 有意不做

- `completeTask` 内 `runtimeService.setVariables(processInstanceId, ...)` + `taskService.complete(taskId, variables)` 疑似重复写变量，但在子流程/多实例场景下 processInstanceId 顶层作用域与任务所在 execution 作用域存在语义差异，未经现场验证不动。
- `FlowModelService.getModelByKey` 加缓存：失效链路（模型保存/部署/删除）需另行梳理，收益大但侵入面大，留作后续专项。
- AiForm 全量渲染优化：与网络耗时无关，另行处理。

## 4. 影响范围

| 范围 | 说明 |
|------|------|
| 改动文件 | `todo.vue`、`FlowTaskServiceImpl.java` |
| 行为变化 | 弹窗打开速度约提升一倍（串行改并行）；全流程单一 loading；表单加载省 3 次变量查询；approve 省 2 次 BPMN 解析 |
| 不变 | 各 API 协议与返回结构、业务表单兜底重查逻辑、审批校验规则（重载委托保证 reject 等路径与原行为一致）、 hydrateBusinessFormFromAssets 回退链 |
| 无 DB 变更 | 无表结构、无 Flyway 脚本 |

## 5. 验收

- [x] 打开审批弹窗只出现一个"加载表单中"转圈，不再两段式 loading
- [x] 表单信息与业务表单上下文并行加载，弹窗打开耗时 ≈ max(两接口) 而非求和
- [x] formInfo 精确字段兜底重查仅在首次定位失败时触发（不改变业务表单最终渲染结果）
- [x] 空态提示在 loading 期间不闪现
- [x] 后端 `getTaskFormInfo` 不再逐 key 查直送变量（省 3 次 DB 往返）
- [x] approve 单次请求内 BPMN 节点只解析一次（原 3 次）
- [x] flow 插件 128 用例回归 127 过，唯一失败为既有问题（`copyModelPreservesMultiLevelReturnSwitch`，与本变更无关，前轮已 stash 验证）
