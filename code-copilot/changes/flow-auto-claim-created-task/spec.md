# 流程创建时直接指定处理人的任务自动签收 Spec

> 变更名：`flow-auto-claim-created-task`
> 状态：`apply`
> 创建日期：2026-09-17
> 参考基线：当前 forge-plugin-flow 源码、Flowable 7.0.1（本地字节码验证）、Flowable 6.7.2-xugu 源码、`AGENTS.md`、`.agents/skills/forge-business-flow-development/SKILL.md`

## 1. 背景与目标

### 1.1 问题现象

业务场景（如 KPI 考核流程）在 BPMN 节点使用「指定人员」模式直接写入处理人表达式（`${initiator}`、`${evaluateeId}`、`${evaluatorId}`），任务创建后出现死锁：

1. 任务镜像 `sys_flow_task.status = 0`（PENDING）但 `assignee` 非空；
2. 前端待办页签收按钮条件为 `status === 0 && !assignee`，有 assignee 时不显示签收按钮，提交前自动签收同样跳过；
3. 签收 API `claimTask` 只接受候选人（`isClaimCandidate`），指定人员模式无法通过；
4. 审批守卫在携带幂等凭证时要求 `CLAIMED`，PENDING 直接拒绝 `FLOW_TASK_NOT_ACTIONABLE`。

结果：任务既不能签收也不能审批，永久卡死。

### 1.2 根因定位（关键修正）

`FlowTaskEventListener.handleTaskAssigned` 第 368-378 行**已存在**「有 assignee 即签收」分支，但它对「创建时直接指定处理人」场景永远不生效：

Flowable 7.0.1 `UserTaskActivityBehavior.execute`（字节码验证，6.7.2-xugu 源码一致）执行顺序为：

```
TaskHelper.insertTask()        ← assignee 仍为 null（表达式未求值），不派发 TASK_ASSIGNED
  → handleAssignments()        ← 表达式求值，TaskHelper.changeTaskAssignee → fireAssignmentEvents 派发 TASK_ASSIGNED
  → 派发 TASK_CREATED
```

即 **TASK_ASSIGNED 先于 TASK_CREATED**。事件到达 `handleTaskAssigned` 时镜像记录尚未插入，`flowTaskMapper.selectByTaskId` 返回 null，`if (flowTask != null)` 整段静默跳过；随后 `handleTaskCreated` 才插入固定 `PENDING` 的镜像。

因此修复位置必须在 `handleTaskCreated`，修复 `handleTaskAssigned` 无意义。

### 1.3 目标

- 创建时已直接指定处理人（assignee 非空）的任务，镜像状态直接置 `CLAIMED`，与「转办/改派通过 setAssignee 直接指定办理人」的既有语义保持一致；
- 候选人/候选组模式（无 assignee）行为不变，仍为 `PENDING` 需要签收；
- 不改变现有接口协议与前端交互。

### 1.4 非目标

- 不改动 Flowable 引擎及其事件机制；
- 不改动前端签收按钮与快速操作条件（修复后条件自然成立）；
- 不在本变更中处理 `handleTaskAssigned` 的「owner≠assignee 保持待办」分支与 `delegate` 语义冲突问题（列为待澄清，可另开变更）。

## 2. 代码现状（Research Findings）

| # | 位置 | 现状 | 结论 |
|---|------|------|------|
| 1 | `FlowTaskEventListener.handleTaskCreated`（第 169 行） | `flowTask.setStatus(FlowTaskStatus.PENDING.getCode())` 固定写死 | 缺口所在，需增加 assignee 判断 |
| 2 | `FlowTaskEventListener.buildFlowTask`（第 699 行） | 已从 Flowable task 读取 assignee 并归一化 | 创建时 assignee 已是终态值，可直接判断 |
| 3 | `FlowTaskEventListener.handleTaskAssigned`（第 360-383 行） | 第 368-378 行有「无 owner → CLAIMED」分支；第 360 行 `selectByTaskId`，null 时整段跳过 | 分支存在但因事件顺序失效，不修改 |
| 4 | `FlowTaskServiceImpl.claimTask`（第 272-297、299-322 行） | 第 283 行要求 PENDING，第 284 行 `isClaimCandidate` 只认候选用户/候选组 | 指定人员模式被拒 `FLOW_TASK_CLAIM_NOT_ALLOWED`，本次不修改 |
| 5 | `FlowTaskActionAuthorization.authorize`（第 43-49 行） | 携带幂等凭证（前端总是携带）时只接受 `CLAIMED` | 死锁闭环来源之一，本次不修改 |
| 6 | `FlowTaskStatus`（第 63-65 行） | `isActionable` = PENDING 或 CLAIMED；`TODO_CODES` 含两者 | 置 CLAIMED 后待办列表查询不受影响 |
| 7 | `forge-admin-ui/src/views/flow/todo.vue`（第 105、328、499、1490 行） | 签收按钮与自动签收条件均为 `status === 0 && !assignee` | 修复后对指定人员任务不再展示签收按钮，符合预期 |
| 8 | `FlowTaskServiceImpl.delegate`（第 530-532 行）、`reassignByInitiator`（第 648-651 行）、`FlowInstanceServiceImpl.reassignTask`（第 663-671 行） | `setAssignee` 直接指定办理人后镜像置 `CLAIMED` | 平台既有先例，本次修复与其对齐 |

## 3. 功能点

### F1 创建时指定处理人的任务自动签收

`handleTaskCreated` 插入镜像前，若 `flowTask.getAssignee()` 非空（含数字用户 ID 与已归一化的显示值），则：

- `status = FlowTaskStatus.CLAIMED.getCode()`（1）
- `claimTime = LocalDateTime.now()`

若 assignee 为空（候选人/候选组模式），保持 `status = PENDING`（0），行为与现状一致。

### F2 日志与可观测性

- 任务创建日志补充状态字段，便于排查（现有日志已打印 assignee/candidate，增加 status 输出或保持现状即可）。

## 4. 业务规则

1. **创建即指定 = 已签收**：BPMN 节点直接配置 assignee（字面量或表达式）时，任务不需要人工签收，处理人可直接审批；语义与转办/改派一致。
2. **候选人模式不变**：无 assignee 的任务仍为 PENDING，签收后变 CLAIMED，审批要求 CLAIMED（幂等凭证场景）。
3. **owner 语义保留**：创建场景下 owner 一般为空；若未来出现创建时 owner 非空且不等于 assignee 的情况，应按 `handleTaskAssigned` 相同的判断保持 PENDING（本次实现按此保守处理）。
4. **不改变状态机**：`FlowTaskStatus` 枚举与流转规则不变，仅修正创建时刻的初始状态取值。
5. **存量数据不在本变更自动修复**：历史 `PENDING + assignee 非空` 的死锁任务是否批量修正，见第 9 节待澄清。

## 5. 数据变更

无表结构、无字典、无 Flyway 脚本变更。

如需修复存量死锁任务（待澄清确认后），建议单开一个版本化数据迁移（`UPDATE sys_flow_task SET status=1, claim_time=NOW() WHERE status=0 AND assignee IS NOT NULL AND (owner IS NULL OR owner = assignee)`），必须配套影响范围与回滚说明。

## 6. 接口变更

无新增、无修改、无删除。全部现有接口（审批、签收、转办、改派）行为不变。

## 7. 影响范围

| 范围 | 说明 |
|------|------|
| 直接改动 | `FlowTaskEventListener.handleTaskCreated` 一处分支 |
| 行为变化 | 所有「节点直接指定处理人」的流程（含 KPI 考核、审批人为固定人/表达式人的流程）任务创建即为 CLAIMED |
| 不变 | 候选人/候选组流程、审批接口、签收接口、前端交互、消息通知（通知逻辑基于 assignee 非空，不受影响） |
| 数据 | 新任务初始状态取值变化；存量数据不变 |

## 8. 风险与关注点

1. **语义放开风险**：指定人员任务不再需要签收。这是本次修复的目标语义，且与 delegate/reassign 先例一致；若产品上要求「指定人员也必须显式签收」，则该需求与平台现状（签收 API 不认指定人员、审批要求 CLAIMED）矛盾，需产品先决策，本变更以对齐先例为准。
2. **待办口径**：`TODO_CODES = [PENDING, CLAIMED]`，置 CLAIMED 不影响待办/已办查询与批量操作。
3. **幂等与审批**：置 CLAIMED 后携带幂等凭证的审批可通过，`FlowTaskActionAuthorization` 逻辑无需改动。
4. **消息通知**：`TASK_ASSIGNED` 事件的站内信/企微推送逻辑在 `handleTaskAssigned`，其通知部分不依赖镜像状态（`flowTask.getAssignee() != null` 才推送），不受影响。
5. **回归面**：候选人流程签收链路、转办/改派链路、退回/驳回复用节点均需验证不受影响。

## 8.5 测试策略

1. **单元/组件测试**（优先）：
   - 构造 `FlowTaskEventListener.handleTaskCreated` 场景：assignee 非空 → 断言插入镜像 status=1 且 claim_time 非空；
   - assignee 为空且无候选人 → 断言 status=0；
   - assignee 为空但有候选人/候选组 → 断言 status=0。
2. **集成验证**（本地 dev 环境）：
   - 启动 BPMN 流程，节点配置 `assignee = ${initiator}`，发起后断言镜像 status=1；
   - 以 assignee 身份携带幂等凭证审批，断言成功；
   - 节点配置候选人组，断言发起后 status=0，签收后 status=1，再审批成功。
3. **回归验证**：
   - 转办（delegate）后目标用户可直接审批；
   - 改派（reassign）后镜像为 CLAIMED；
   - 既有 PENDING 死锁任务用 `/api/flow/monitor/reassign/{taskId}`（管理员转派）解锁后可审批。

## 9. 待澄清

1. 存量死锁任务（`status=0 AND assignee IS NOT NULL AND (owner IS NULL OR owner=assignee)`）是否需要在本变更内提供一次性数据修复脚本？（推荐：提供，独立 Flyway 脚本 + 人工确认后执行）
2. `handleTaskAssigned` 的「owner≠assignee 保持待办」分支与 `delegate`「设为已签收」语义相反，当前靠执行顺序隐式兜住，是否一并修正？（推荐：另开变更，避免扩大本变更回归面）
3. KPI 场景是否还要求「指定人员同样需要显式签收」的交互？（推荐：不要求，对齐先例）
4. `reassignTask` 解锁后不写 `claim_time`（`execution-log.md` 第 4 节实证），是否在存量修复脚本或后续变更中一并补齐 `claim_time` 语义？

## 10. 执行日志（apply）

### 2026-09-17

- **T1.1/T1.2 完成**：`FlowTaskEventListener.handleTaskCreated` PENDING 硬编码替换为条件判定（assignee 非空且 owner 空/相等 → `CLAIMED` + `claimTime`），创建日志追加 `status`；`mvn -o -pl .../forge-plugin-flow compile` BUILD SUCCESS。
- **T2.1/T2.2 完成**：新增 `FlowTaskEventListenerCreatedAutoClaimTest`（5 用例：CLAIMED×2、PENDING×3），`-Penable-tests` 下 `Tests run: 5, Failures: 0, Errors: 0`。
- **T2.3 回归完成**：全模块 `Tests run: 128, Failures: 0, Errors: 1`；唯一失败 `FlowModelServiceImplTest.copyModelPreservesMultiLevelReturnSwitch`（「缺少可信租户上下文」）经 `git stash` 对比验证为既有问题，与本变更无关。
- **测试命令要点**：默认 `forge.tests.skip=true`，必须追加 `-Penable-tests` profile。
- **待办**：Phase 3 存量修复依赖第 9 节待澄清 1；Phase 4 新建任务 E2E 断言需修复部署后执行；Git 提交方式待确认（当前 main 分支 + 工作树含大量既有未提交修改）。
