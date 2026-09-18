# 审批表单加载与提交性能优化 Spec

> 变更名：`flow-approval-form-perf`
> 状态：`implemented-round-3-pending-runtime-validation`
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

### 二轮排查（用户反馈“还是很慢，循环 DB 是否太多”）

对 approve 全链路（含 `taskService.complete` 同步触发的 Flowable 事件 listener）做了完整往返审计，一次普通审批（单节点推进）同步事务内往返 30+ 次，新增定位四处放大器：

- **FlowAccessGuard 逐组 COUNT（N+1）**：`hasCandidateRelationForSessionGroups` 对会话的每个组（角色 ID/角色 key/组织/用户组，常见 5-15 个）逐个发 `countActiveByTaskAndValue` COUNT 查询；待办列表每行、每次任务可见性检查都要走一遍。
- **completeTask 恒真守卫**：`isProcessRunning(processInstanceId)` 在 task 刚从 taskQuery 查出的场景下恒真（ACT_RU_TASK 有行则流程必然在运行），每次审批白查一次；且 `directSendAfterReturn` 开头又查一次。
- **directSendAfterReturn 逐 key 清除**：三处 `removeVariable` ×3，每次 3 条独立往返。
- **FlowTaskEventListener（最大头）**：①`readProcessVariables` 无条件先查 runtime 变量再全量拉一次 history 变量兜底——TASK_COMPLETED/TASK_CREATED/TASK_ASSIGNED/PROCESS_COMPLETED 每个事件都重复一次重查询；②`resolveUserDisplayName` 无论 fallback 是否已有名字都反查一次用户表——每个任务事件 1-2 次冗余；③`getFlowBusiness` 在同一事务的多个事件里重复查（代价小，本轮不动）。

### 三轮排查（待办列表、表单加载和提交仍慢）

- **待办分页候选条件仍是逐行复杂相关子查询**：`TodoCandidateCondition` 对每条 `sys_flow_task` 关联 `sys_user_org_role/sys_role/sys_role_org/sys_user_org`，并多次规范化 CSV 后 `FIND_IN_SET`。这套条件同时进入分页 COUNT，任务量增长后不能稳定使用候选关系索引。
- **标准化候选关系没有成为主查询路径**：已有 `sys_flow_task_candidate`，但待办 SQL 只用它判断直接用户，组候选仍回到角色/组织逐行解析；已有候选索引也没有覆盖最终返回的 `task_id`。
- **列表业务摘要仍有按行对象查询**：业务列表 enrich 在批量取流程关联后，仍对每条任务调用 `resolveTaskBusinessObject`；同一业务对象的一页任务会重复查询相同对象元数据。
- **业务表单上下文重复跨服务取任务**：一次 `task-form-context` 内部先用 `getTaskDetail` 校验身份，再通过 `hydrateTaskFormQuery` 和节点表单解析重复调用 `getTaskFormInfo`；浏览器同时还直接请求一次 Flow 表单信息。
- **审批前暂存与办理是两个串行请求**：前端先 `PUT task-form-context`，再 `POST task-action`；两个请求都重新校验任务、解析业务运行时和节点权限，主子表数据还要重复组装。

## 3. 方案

### F1 前端并行加载 + 合并 loading（todo.vue）

- `openDrawer` 改为 `Promise.all([loadTaskFormInfo(taskId), loadBusinessTaskFormContext(row, {})])` 并行；formInfo 返回后仅当业务上下文未 configured 时用其精确字段（formKey/recordId/objectCode）兜底重查一次（罕见路径）；`hydrateBusinessFormFromAssets` 保持原条件。
- 新增 `taskFormLoading = computed(() => formInfoLoading || businessFormLoading)`，模板统一只展示这一个 spinner；删除业务表单区独立 spinner；checklist/directSend/empty 条件统一改用 `taskFormLoading`，空态在 loading 期间不闪现。
- 弹窗打开耗时从「formInfo + businessContext (+ assets) 之和」降为「两者最大值」，且全程单一转圈。

### F2 后端消除重复查询（FlowTaskServiceImpl）

- `populateDirectSendInfo` 增加已取变量参数，3 次 `runtimeService.getVariable` 改为从 `variables` Map 读取（这些 key 均为流程级直送标记，`taskService.getVariables(taskId)` 返回的任务上下文全量变量必然包含）。
- approve 路径：一次 `repositoryService.getBpmnModel` 解析后，`getFlowNode(bpmnModel, taskDefinitionKey)` 复用给 `validateTaskAction`/`validateRequiredVariables`/`validateApprovalPoints` 与 `autoApproveRepeatedTasks`（三个 validate 增加带 FlowNode 的重载，旧签名保留并委托，reject/delegate/terminate 等其他调用点行为不变）；`getTaskFormInfo` 主节点与直送源节点共用同一次 BPMN 解析。

### F3 可见性检查批量化（FlowAccessGuard + FlowTaskCandidateMapper）

- 新增 `countActiveByTaskAndValues`（IN 查询），`hasCandidateRelationForSessionGroups` 从逐组 COUNT 改为按类型分组后一次 IN，N+1 放大器消除；原单值方法保留兼容其他调用点。

### F4 completeTask 守卫与直送标记批量清除（FlowTaskServiceImpl）

- 删除 `isProcessRunning` 恒真守卫（三个调用点 approve/reject/autoApproveTask 的 task 均刚从 taskQuery 查出；若并发下流程恰好被终止，`setVariables` 与 `complete` 抛出的 `FlowableObjectNotFoundException` 均被 catch 转为同一提示，行为等价）。
- `directSendAfterReturn` 三处 `removeVariable` ×3 收敛为 `clearDirectSendMarks`：一次 `runtimeService.removeVariables(processInstanceId, keys)` 批量清除（退回后审批场景每处省 2 次往返）。`directSendAfterReturn` 读三枚标记也收敛为一次 `getVariables`。

### F5 事件监听器查询收敛（FlowTaskEventListener）

- `readProcessVariables`：runtime 变量非空时直接返回，history 全量兜底仅在运行变量不可读（流程已结束）时触发——TASK_COMPLETED/TASK_CREATED/TASK_ASSIGNED/PROCESS_COMPLETED 每事件省 1 次重查询。
- `resolveUserDisplayName`：fallback 非空直接返回，仅名字缺失时反查用户表；`handleTaskCreated` 的 applyUserName 规范化回写自然退化为「仅存量名字为空时补一次」。

### F6 待办列表候选关系与摘要批量化

- 在 Service 层按当前会话一次性组装候选组值：当前组织下的角色 ID、角色编码、当前组织 ID和用户组编码；Mapper 只接收已经校验过的组值集合。
- `TodoCandidateCondition` 优先通过 `sys_flow_task_candidate` 的 USER/GROUP 关系判断可见性；只有任务完全没有标准化候选关系时，才进入历史 CSV 兼容分支，兼容分支也只遍历本次会话的有限组值，不再逐行关联角色和组织表。
- 新增覆盖索引 `(tenant_id, status, candidate_type, candidate_value, task_id)`，让候选子查询直接从索引得到任务 ID。
- 业务摘要 enrich 优先使用流程关联快照中的 `configKey/objectCode` 分组；同页只有缺少稳定身份时才查询对象，且按身份缓存。分组 enrich 复用 `BusinessRuntimeContext.businessObject`，不再重复查询对象。

### F7 单次任务表单快照复用

- Flow `TaskFormInfo` 增加当前任务镜像的 `status/assignee/candidateUsers/candidateGroups`，原接口在完成可见性校验后返回这些字段。
- `BusinessFlowService` 的上下文、暂存、办理入口每次只读取一次 Flow `TaskFormInfo`；同一份快照同时用于任务访问校验、查询身份补齐、节点表单权限解析，不再额外调用 `getTaskDetail` 或重复 `getTaskFormInfo`。
- `BusinessTaskFormContextVO` 携带本次已校验的 `taskFormInfo`。待办前端优先通过单个 `task-form-context` 请求同时拿表单策略和业务数据；只有兼容旧后端或接口未返回快照时才回退直连 Flow 表单接口。

### F8 保存与审批合并为单请求

- `task-action` 使用现有 `BusinessTaskActionDTO.data` 接收 `{main, children}`，后端在同一入口按当前任务节点权限过滤、校验并保存业务字段，再调用 Flow 审批动作。
- 暂存按钮继续使用独立保存接口；点击同意、驳回、驳回至发起人、退回时不再先发一次暂存请求。
- 保存和办理复用同一次任务快照、业务运行时、节点表单和权限解析。若 Flow 动作失败，本地事务回滚本次表单修改；仍不信任前端传入的字段权限。

### 有意不做

- `completeTask` 内 `runtimeService.setVariables(processInstanceId, ...)` + `taskService.complete(taskId, variables)` 疑似重复写变量，但在子流程/多实例场景下 processInstanceId 顶层作用域与任务所在 execution 作用域存在语义差异，未经现场验证不动。
- `FlowModelService.getModelByKey` 加缓存：失效链路（模型保存/部署/删除）需另行梳理，收益大但侵入面大，留作后续专项。
- listener 内 `getFlowBusiness` 同事务多事件重复查询与 `handleTaskCompleted` + approve 主流程对同一任务的双 update：事件镜像与主流程落库的既有架构，合并风险高，单次主键查询代价小，不动。
- AiForm 全量渲染优化：与网络耗时无关，另行处理。
- 不使用跨请求长 TTL 缓存保存任务权限；任务状态和节点权限是安全边界，只做单请求内复用。

## 4. 影响范围

| 范围 | 说明 |
|------|------|
| 改动文件 | 前两轮文件；第三轮增加 `FlowTaskMapper.java/.xml`、`TaskFormInfo.java`、`BusinessFlowService.java`、`BusinessTaskFormContextVO.java`、`todo.vue`、候选索引迁移及对应测试 |
| 行为变化 | 前两轮将表单加载改为并行并合并 loading；第三轮常规业务待办进一步收敛为单个浏览器请求、单次 Flow 表单快照。待办候选查询改走标准化关系和覆盖索引，列表对象元数据按身份缓存；业务审批由“暂存 + 办理”两个请求合并为一个受控办理请求 |
| 不变 | 各 API 协议与返回结构、业务表单兜底重查逻辑、审批校验规则（重载委托保证 reject 等路径与原行为一致）、直送判定语义、applyUserName 规范化能力（仅收敛为缺失时补齐）、历史变量兜底能力（保留在 runtime 不可读时生效） |
| DB 变更 | 新增非破坏性 Flyway 覆盖索引迁移 `V1.0.167__optimize_flow_todo_candidate_lookup.sql`，不修改业务数据 |

> 第三轮新增一条非破坏性覆盖索引迁移；不修改业务数据和接口路径。

## 5. 验收

- [x] 打开审批弹窗只出现一个“加载表单中”转圈，不再两段式 loading
- [x] 表单信息与业务表单上下文并行加载，弹窗打开耗时 ≈ max(两接口) 而非求和
- [x] formInfo 精确字段兜底重查仅在首次定位失败时触发（不改变业务表单最终渲染结果）
- [x] 空态提示在 loading 期间不闪现
- [x] 后端 `getTaskFormInfo` 不再逐 key 查直送变量（省 3 次 DB 往返）
- [x] approve 单次请求内 BPMN 节点只解析一次（原 3 次）
- [x] 可见性检查不再逐组 COUNT，按类型一次 IN 命中（会话组 5-15 个时每次省 4-14 次往返）
- [x] completeTask 无恒真守卫查询；直送标记清除为单次批量调用
- [x] 事件链路 runtime 变量命中时不再拉历史全量；用户名 fallback 命中时不再反查用户表
- [x] flow 插件 128 用例回归 127 过，唯一失败为既有问题（`copyModelPreservesMultiLevelReturnSwitch`，与本变更无关，前轮已 stash 验证）
- [x] 直送测试 mock 同步收敛（`getVariables` 单次 / `removeVariables` 批量断言）
- [x] 待办候选组优先使用标准化关系和覆盖索引，分页 SQL 不再逐行关联角色/组织表
- [x] 同页业务任务不再按行重复查询相同业务对象元数据
- [x] 加载业务表单的常规路径只发一个浏览器请求，Admin 到 Flow 每次只读取一次任务表单快照
- [x] 审批时业务表单保存和 Flow 动作合并为一个浏览器请求，并保留服务端字段权限校验
- [x] 前端定向测试 11 文件 70 项、ESLint、生产构建通过
- [ ] 后端定向测试、编译及真实 Admin/Flow/MySQL EXPLAIN（当前主机无 Java/Maven/运行服务，留待部署验收）
