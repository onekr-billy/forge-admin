# 低代码流程发起本地查询优化 Spec

> 变更名：`lowcode-flow-start-local-query-performance`
> 状态：`completed`
> 创建日期：2026-09-20

## 1. 问题与根因

前两阶段已经处理前端反馈、Flow 发起配置缓存、Admin 到 Flow 的连接复用和 Flow 侧组织上下文按需加载。Admin 真正发起前仍存在一段重复工作：

1. `BusinessFlowService` 已解析业务对象运行配置并读取业务记录后，仍调用 `BusinessDocumentRuntimeService#getRuntime` 构造完整页面运行态。
2. 完整运行态会再次解析对象/单据配置、读取同一条业务记录，并查询应用流程运行记录、全部流程轮次和当前用户待办；待办查询还可能产生一次额外 Admin → Flow 请求。
3. 完整运行态校验结束后，发起服务又重复查询流程关联、流程绑定和运行配置。
4. 为生成流程业务 Key 与轮次，发起路径会对同一 `businessKey` 多次查询最新关联。

这些查询对“是否允许发起”并非都必需，且会直接叠加在用户等待的同步请求上。

## 2. 目标

1. 为发起流程提供基于预加载上下文的轻量校验入口，只校验单据模式、流程配置、手动发起方式、状态策略、权限和既有流程实例。
2. 轻量校验复用已经读取的记录、运行配置、单据配置、流程绑定和最新流程关联，不再构造完整详情运行态。
3. 同一次发起只查询一次最新流程关联，并复用它计算重复发起、Flow 业务 Key 和 `roundNo`。
4. `ensureBusinessBinding` 复用本次已经解析的运行配置和单据配置，避免再次查库。
5. 保持 `businessKey`、权限、状态策略、单据“一次主流程实例”限制、触发器发起兼容和状态回写语义不变。

## 3. 设计

### 3.1 预加载轻量校验

- `BusinessDocumentRuntimeService` 增加接收 `BusinessDocumentConfigVO`、记录数据和最新流程关联的校验入口。
- 该入口复用既有 `fillNextAction` 和统一校验规则，不读取配置、记录、应用流程运行记录、流程轮次或 Flow 待办。
- 手工发起继续要求 `START_FLOW` 权限；内部触发器继续跳过按钮权限和手动发起方式限制，但仍遵守单据状态及既有主流程限制。
- 旧的 `validateStartAllowed(objectCode, recordId, checkPermission)` 保留兼容，并与新入口共用最终判定方法。

### 3.2 流程绑定与配置视图复用

- `BusinessFlowService` 在锁内只解析一次主流程绑定。
- `BusinessDocumentConfigService` 支持用调用方已解析的绑定构建配置视图，避免为了 `mainFlowSummary` 再查一次绑定。
- 缺少正式绑定时仍按既有 `defaultFlowKey` 兼容路径生成主流程摘要。

### 3.3 最新关联复用

- 使用一次 `selectLatestByBusinessKey` 同时判断：
  - 单据是否已有主流程实例；
  - 应用级流程是否仍在运行；
  - 非稳定 Flow 业务 Key 的轮次后缀；
  - 新关联的 `roundNo`。
- 不改变稳定业务 Key 入口的远程幂等语义。

## 4. 非目标与边界

- 不缓存业务记录、权限或用户态数据。
- 不异步化流程启动、关联写入、状态回写或 `TASK_CREATED`。
- 不修改数据库结构、BPMN 或前端协议。
- 不启动或重启 Admin/Flow，不写入真实业务数据。

## 5. 验收标准

- [x] 预加载校验不调用完整 `getRuntime`，不查询流程轮次、应用流程运行记录或 Flow 待办。
- [x] 手工发起的流程配置、状态策略、权限和既有实例限制与原行为一致。
- [x] 内部触发器仍可跳过手工按钮权限，但不能绕过状态或既有主流程限制。
- [x] 一次发起只读取一次最新流程关联，并复用其生成 Flow 业务 Key 和轮次。
- [x] 发起路径不为 `ensureBusinessBinding` 或配置视图重复查询运行配置、单据配置和流程绑定。
- [x] 定向单测、JDK 17 隔离编译及 `git diff --check` 通过；完整 reactor 的无关工作区阻塞已记录。
