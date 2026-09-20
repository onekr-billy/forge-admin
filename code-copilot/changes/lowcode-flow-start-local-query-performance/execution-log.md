# 低代码流程发起本地查询优化执行记录

> 本文件按执行轮次追加命令、结果和环境边界，不覆盖历史记录。

## 2026-09-20 静态基线

- 发起服务在读取运行配置和记录后调用完整 `BusinessDocumentRuntimeService#getRuntime`。
- 完整运行态会额外查询应用流程运行记录、最新流程关联、流程轮次、记录数据、动作权限和当前用户 Flow 待办。
- 发起服务随后再次查询运行中关联、流程绑定、运行配置和单据配置，并为 Flow 业务 Key 与 `roundNo` 重复读取最新关联。
- 本阶段只优化 Admin 本地发起链路，不改变流程引擎启动、状态回调或幂等边界。

## 2026-09-20 增量验证

### 变更范围

- `BusinessDocumentRuntimeService` 新增基于预加载配置、记录和最新流程关联的轻量发起校验，共用原有最终判定规则。
- `BusinessDocumentConfigService` 支持复用调用方已解析的主流程绑定构建配置视图。
- `BusinessFlowService` 在发起锁内只读取一次最新流程关联，并复用于重复发起判断、Flow 业务 Key 和 `roundNo`。
- 新增轻量校验单测与发起查询性能契约测试。

### 命令与结果

1. JDK 17 隔离编译：
   - 编译 `BusinessDocumentConfigService`、`BusinessDocumentRuntimeService`、`BusinessApprovalTitleRenderer`、`BusinessFlowService`。
   - 结果：全部成功；`BusinessFlowService` 只有既有 deprecated API 提示，无编译错误。
   - 隔离目录：`/tmp/forge-flow-phase3.bcxuR2`。
2. JDK 17 隔离 JUnit：
   - 定向执行 `BusinessDocumentRuntimeServiceTest` 新增 3 个测试。
   - 执行 `BusinessFlowPerformanceContractTest` 全部 4 个测试。
   - 结果：7 个测试全部通过，0 失败、0 跳过。
3. JDK 17 Maven reactor：
   - 命令：`mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`。
   - 结果：上游 31 个模块成功，`forge-plugin-generator` 编译失败。
   - 阻塞 1：`LowcodeRuntimeConfigBuilder.java:283-284` 引用未定义变量 `context`。
   - 阻塞 2：`DataAuditValueNormalizer.java:70` 引用未定义变量 `value`。
   - 两处均属于用户工作区其它未完成变更，本阶段未修改。
4. 静态检查：
   - 本阶段相关文件 `git diff --check` 通过。
   - 全工作区 `git diff --check` 通过。

### 语义复核

- 单据模式只要已有 `processInstanceId`，无论运行中或终态都禁止另起主流程。
- 应用级流程仅在最新关联仍运行时阻止重复发起；终态关联继续用于生成新的轮次 Flow 业务 Key。
- 内部触发器可跳过手工按钮权限和手工发起模式限制，但仍受单据状态及既有实例限制。
- `isRunningFlowLink` 与 Mapper 原 `selectRunningByBusinessKey` 的状态及空结束结果条件保持一致。

### 环境边界

- 未启动或重启 Admin/Flow，未调用真实发起接口，未写入业务数据。
- 真实耗时对比与 SQL/远程调用观测需在加载本阶段代码后执行。
- 本轮未启动任何需保留或停止的服务进程。
