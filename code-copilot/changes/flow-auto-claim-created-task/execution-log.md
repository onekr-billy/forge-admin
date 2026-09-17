# 流程创建时直接指定处理人的任务自动签收 Execution Log

> 变更名：`flow-auto-claim-created-task`
> 记录时间：2026-09-17
> 环境：本地 flow 服务（http://127.0.0.1:8081，源码直启）+ 远程开发库 `120.48.96.178:3306/forge_admin_new`
> 本次内容：死锁复现实证 + 管理员转派解锁演示（支撑 spec.md 8.5 测试策略与 tasks.md T0.1/T4.1）

## 1. 执行摘要

在真实环境上完成三段验证：

1. **死锁复现**：`PENDING + assignee 非空` 的任务，签收与审批双拒；
2. **存量解锁**：管理员转派接口把镜像置 `CLAIMED`，审批链路即时恢复；
3. **解锁后审批**：同一任务审批通过，状态流转 `0 → 1 → 2`。

关键佐证：本任务节点配置为 `flowable:assignee="${initiator}"`，与 KPI 场景（`${evaluateeId}/${evaluatorId}`）完全同构，属平台缺口产生的真实死锁样本。

## 2. 目标样本

| 项 | 值 |
|---|---|
| taskId | `fe947251-b0b0-11f1-9a2f-6e7dee32d25e` |
| 流程定义 | `low12:18:90857910-a43e-11f1-bae4-a69dcec7c7c7`（KEY=low12，v18） |
| 节点 | `Node_1`「审批人」，`flowable:assignee="${initiator}"`，1 个必审要点 |
| 镜像初始状态 | `status=0(PENDING)`, `assignee=1(admin)`, `owner=NULL`, `claim_time=NULL` |

该组合正是 spec.md 1.1 描述的死锁场景：有 assignee → 前端不显示签收按钮、签收 API 拒非候选人、审批守卫生效要求 CLAIMED。

## 3. 死锁复现（修复前行为）

| # | 请求 | 结果 |
|---|------|------|
| 1 | `POST /api/flow/task/claim?taskId=...&userId=1` | `500 FLOW_TASK_CLAIM_NOT_ALLOWED`（非候选人不能签收） |
| 2 | `POST /api/flow/task/approve`（带 idempotencyKey+requestDigest） | `500 FLOW_TASK_NOT_ACTIONABLE`（PENDING 不满足 CLAIMED 守卫） |

修复预期：F1 落地后新建任务直接为 CLAIMED，上述两步中的第 2 步可直接通过。

## 4. 解锁演示（管理员转派，方案 A）

```bash
POST /api/flow/monitor/reassign/fe947251-b0b0-11f1-9a2f-6e7dee32d25e
Authorization: Bearer <token>
{"newAssignee":"1","reason":"PENDING+assignee死锁解锁（管理员转派演示）"}
```

- 接口返回：`200 任务已转派`
- 数据库验证：`status 0 → 1`，`owner=1`
- 备注：`reassignTask`（FlowInstanceServiceImpl 第 663-671 行）不写 `claim_time`，解锁后 `claim_time` 仍为 NULL。属镜像字段的小缺口，建议纳入 spec 第 9 节待澄清（是否随 CLAIMED 一并补齐）。

## 5. 解锁后审批验证

| # | 请求 | 结果 |
|---|------|------|
| 3 | `POST /api/flow/task/approve`（带幂等键，未传要点） | `500 请完成全部必审要点` ← 状态守卫**已放行**，卡点转移到节点业务校验 |
| 4 | `POST /api/flow/task/approve`（带幂等键 + 正确 `approvalPointResults`） | `200 审批通过` |

第 4 步后数据库：`status = 2(APPROVED)`，`complete_time = 2026-09-17 19:49:22`。

第 3 步的报错变化（`FLOW_TASK_NOT_ACTIONABLE` → `请完成全部必审要点`）本身就是解锁成功的最直接证据：同一条带幂等键的审批请求越过了状态守卫。

## 6. 环境操作手册（复用于目标环境解锁）

### 6.1 登录（RSA 密码加密）

1. `GET /crypto/public-key` 取 `data.publicKey`；
2. 用 RSA PKCS#1 v1.5 加密明文密码，输出 Base64（Node 示例：`crypto.publicEncrypt({key: pem, padding: RSA_PKCS1_PADDING})`）；
3. 登录：`POST /auth/login`

```json
{
  "username": "admin",
  "password": "<RSA密文>",
  "authType": "password",
  "userClient": "pc",
  "appId": "pc",
  "tenantId": 1
}
```

- `appId` 必须与 `sys_client.app_id` 一致（默认库为 `pc`，不是 `.env.example` 的 `forge_pc_001`）；
- 不传 `tenantId` 且多租户账号会返回 4091 要求选择工作区；
- 请求头默认名 `Authorization: Bearer <accessToken>`（Sa-Token 配置从数据库加载，tokenName=Authorization）。

### 6.2 解锁命令模板

```bash
curl -X POST "http://<flow-host>/api/flow/monitor/reassign/<taskId>" \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"newAssignee":"<原处理人userId>","reason":"PENDING死锁解锁"}'
```

`newAssignee` 传原处理人 ID 即可（保留处理人不变，仅修正镜像状态）；权限需 `flow:monitor:manage`（admin 具备）。

## 7. 存量死锁盘点（当前库）

`SELECT COUNT(*) FROM sys_flow_task WHERE status=0 AND assignee IS NOT NULL AND (owner IS NULL OR owner=assignee);`

- 已知样本（TOP10 中）：`fe947251…`（已解锁演示）、`921be3db…`、`4255f0da…`、`5696f746…`、`4999a52a…`、`d67dbae4…`、`2d4d0052…`、`ab41cb32…`（assignee=1）、`0810667b…`、`27b0d895…`（assignee=45）；
- 完整清单与是否需要批量修复（T3.1/T3.2）待 spec 第 9 节待澄清 1 确认。

## 8. 结论与后续

- [x] 死锁可复现（双拒证据）
- [x] 存量解锁通道有效（reassign → CLAIMED）
- [x] 解锁后审批全链路通（含必审要点）
- [x] 代码修复（F1：handleTaskCreated 自动签收）已实施，编译 + 单测 + 回归通过（见第 9 节）
- [ ] 前端实测（修复前无签收按钮）尚未执行，当前为代码级证据（todo.vue 第 1490 行 `status === 0 && !assignee`）
- [ ] `claim_time` 未随解锁写入的缺口确认

## 9. 代码修复实施记录（2026-09-17，apply 阶段）

### 9.1 T1.1/T1.2 代码变更

文件：`forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/listener/FlowTaskEventListener.java`（diff 18+/3-）。

- `handleTaskCreated` 中 PENDING 硬编码替换为条件判定：`assignee` 非空且（`owner` 为空或 `owner = assignee`）→ `FlowTaskStatus.CLAIMED` + `claimTime = LocalDateTime.now()` + info 日志；其余保持 `FlowTaskStatus.PENDING`；
- 「创建待办任务成功」日志追加 `status={}` 输出；
- 注意：Flowable `TASK_ASSIGNED` 事件先于 `TASK_CREATED` 派发，创建场景下 `handleTaskAssigned` 因镜像未插入不可达，故修复必须落在创建时刻（代码注释已标注）。

### 9.2 编译验证

```bash
cd forge-server && mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-flow compile
# BUILD SUCCESS（全模块 208 源文件）
```

### 9.3 单元测试（T2.1/T2.2）

新增 `src/test/.../listener/FlowTaskEventListenerCreatedAutoClaimTest.java`（5 用例）：
assignee 非空且 owner 空 → CLAIMED+claimTime；owner=assignee → CLAIMED；owner≠assignee → PENDING；无 assignee 无候选 → PENDING；无 assignee 有候选 → PENDING。

```bash
mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-flow test -Penable-tests -Dtest='FlowTaskEventListenerCreatedAutoClaimTest'
# Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

> ⚠️ 测试命令必须带 `-Penable-tests`：`forge-server/pom.xml` 默认 `forge.tests.skip=true` 且 `maven.test.skip=${forge.tests.skip}`，否则输出 `Tests are skipped` 且不编译测试源码。

### 9.4 全模块回归（T2.3）

```bash
mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-flow test -Penable-tests
# Tests run: 128, Failures: 0, Errors: 1, Skipped: 0
```

唯一失败：`FlowModelServiceImplTest.copyModelPreservesMultiLevelReturnSwitch` — `IllegalStateException: 缺少可信租户上下文`（FlowModelServiceImpl.java:808 requireTenantId ← copyModel:721）。

**既有问题确认**：`git stash` 暂存本次修改后单独重跑该测试，同样失败（同错误），与本变更无关；验证后已 `git stash pop` 恢复。

### 9.5 新增用例调试记录

首轮运行 4/5 通过，`shouldKeepPendingWhenNoAssignee` 报 `flowTaskMapper.insert` 未被调用。原因：无 assignee 且无候选人时进入「审批人分配失败」分支调用 `flowErrorLogService.recordError`，测试未注入该字段导致 NPE 被外层 catch 吞掉。已在 `setUp` 注入 mock 后 5/5 通过。
