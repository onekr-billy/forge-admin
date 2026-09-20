# 低代码单据与审批流程生命周期同步测试计划

## 新版应用增量验证

- 无单据配置、没有我的待办时，运行中的流程发起人仍能在详情与列表获取撤回动作；非发起人、缺接口权限、终态均不可撤回。
- 仓储在真实无 Web 请求上下文中仍可按明确租户更新状态和更新时间，不冒充用户；Web 会话/显式身份原审计行为不变。
- 流程撤回事件同步 CANCELED；重复事件幂等；状态写入失败不继续更新 link。
- 前端两个独立读取同时开始；失败退出释放加载态；旧运行态响应不覆盖新记录。
- 定向测试后执行前端 build；真实撤回 E2E 留待用户重启并使用测试记录验收。

## P0 自动化验证

1. 前端 BPMN 转换器
   - `TO_END` 下普通驳回仍到结束节点。
   - 节点开启 `allowRejectToStart` 后生成独立发起人修改分支。
   - `MANUAL` 下不自动生成普通驳回，但仍为显式“驳回至发起人”权限生成专用分支。
   - 普通驳回条件与 `rejectToStart` 条件互斥。
2. 前端低代码运行动作
   - `IN_PROCESS`、`NEED_MODIFY`、`APPROVED`、`REJECTED`、`CANCELED` 均隐藏 `START_FLOW`。
   - `NOT_STARTED` 仍允许显示 `START_FLOW`。
3. Generator 业务状态同步
   - `TASK_CREATED` 到 `Forge_InitiatorModify` 时同时写关联状态和记录状态 `NEED_MODIFY`。
   - 从修改节点重新进入审批节点时恢复 `IN_PROCESS`。
   - `NEED_MODIFY` 能加载发起人修改待办并产生 `RESUBMIT_FLOW`，不产生 `START_FLOW`。
   - Flow 批量待办查询暂时为空时，使用关联表中的修改任务快照恢复 `RESUBMIT_FLOW`，且不向非处理人暴露任务。
   - 未启用单据模式但已绑定应用级流程时，仍将发起人修改待办转换为列表 `RESUBMIT_FLOW` 动作。
   - 终态关联只提供查看流程，不产生 `START_FLOW`。
4. Flow 动作变量
   - 普通通过/驳回显式写 `rejectToStart=false`。
   - 驳回至发起人在同一动作中覆盖为 `rejectToStart=true`。

## P1 构建与静态检查

- Generator 与 Flow 相关模块定向测试。
- Generator 与 Flow 相关模块编译。
- 前端 Vitest 定向测试与生产构建。
- `git diff --check`。
- 静态检查本轮 Java 状态写入使用 `BusinessDocumentFlowStatus`，不新增固定字段 `@RequestBody Map`。

## 暂不执行

- 不自动启动 Admin、Flow、MySQL、Redis，也不修改真实流程运行态；遵循用户既有联调分工。
- 真实服务端到端验收由用户环境复跑，最终交付提供明确场景清单。

## 2026-09-20 终态按钮与 loading 增量验证

### P0 回归

- 前端 `business-action-runtime.spec.js`：已完成的同 `processCode` 应用流程隐藏启动动作，其他未发起流程仍可见。
- 后端 `BusinessDocumentRuntimeServiceTest`：批量运行态返回成功流程的 `startedProcessCodes`。
- 后端 `BusinessProcessMapperContractTest`：已启动流程查询具备租户、批量和状态约束，不屏蔽可重试的 `FAILED`。

### P1 静态与构建检查

- 定向 ESLint 检查本轮 JS/Vue 改动。
- Generator 相关模块定向测试与编译。
- 前端生产构建。
- `git diff --check`。

### 真实环境待用户验收

- 已通过记录刷新列表后，“更多”中不再出现同一流程的“发起流程”。
- 撤回后状态显示“已撤回”，仅保留查看详情/审批历史，不出现发起、重提、撤回动作。
- 发起、选定审批人后发起、修改后重提与撤回时，页面中央显示对应 loading 文案。
