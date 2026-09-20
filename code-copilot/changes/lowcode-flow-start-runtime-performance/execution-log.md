# 低代码流程发起运行链路性能优化执行记录

> 本文件按执行轮次追加命令、结果与环境边界，不覆盖历史记录。

## 2026-09-20 实现中

- 已确认 Flow 客户端默认使用 `SimpleClientHttpRequestFactory`。
- 已确认流程启动前会无条件加载领导、区域、角色和组织上下文。
- 已确认低代码稳定业务键入口使用可信委托 Session，流程状态机和同步 `TASK_CREATED` 不纳入本轮改动。

## 2026-09-20 实现与验证完成

### 红灯与实现修正

- 新增连接池自动配置测试后，首次因连接池配置属性和 request factory 尚未实现而编译失败，符合预期红灯。
- 新增 BPMN 上下文需求测试后，首次因解析器尚未实现而无法编译，符合预期红灯。
- 最初评估过 Spring 的 OkHttp request factory，但 Spring 6.2 已将该适配器标记为待移除，最终改用 Apache HttpClient 5，支持连接池申请超时、总连接数和单路由连接数。
- HttpClient 构建器的空闲校验配置不会通过连接管理器旧 getter 暴露；改为显式构造并注入 `ConnectionConfig`，测试直接校验实际连接配置。

### 实现结果

- `flowRestTemplate` 改为 Spring 托管的 Apache HttpClient 5 连接池，默认总连接数 100、单路由 50、连接申请超时 1 秒、连接超时 3 秒、读取超时 10 秒、保活 5 分钟、空闲校验 5 秒；原 `connect-timeout` 和 `read-timeout` 配置键保持兼容。
- 流程定义只在首次发起时转换并识别 `initiatorLeader`、区域、角色、当前组织和所属组织变量，需求缓存按不可变 `processDefinitionId` 建键并限制 512 项。
- 普通审批模型不再无条件查询领导、区域、角色和组织；只引用区域变量的模型只查询用户区域。
- 委托类、脚本任务、调用活动、监听器等不透明执行点，以及模型转换异常，都会保守回退为完整上下文加载。
- 调用方传入的变量不做过滤；测试确认 BPMN 未引用的 `startUserOrgIds` 仍原样进入 Flowable 启动变量。
- 实例改为使用已解析的 `processDefinitionId` 启动，避免 Flowable 再按模型 Key 选择一次最新定义。
- 可信 Session 用户与发起人一致时复用 Session 真实姓名，低代码委托发起不再重复查询用户资料；其它场景保留原组织/用户查询兜底。

### 自动化证据

- JDK 17 执行 `FlowClientAutoConfigurationTest`：2 个用例通过，覆盖池化 request factory、连接/读取/申请超时、连接数、保活、空闲校验和自定义 RestTemplate 覆盖。
- JDK 17 执行 `FlowStarterContextRequirementResolverTest,FlowInstanceServiceStarterContextTest`：9 个用例通过，覆盖变量识别、失败/不透明执行点回退、缓存命中与上限、按需查询、调用方变量保留、定义 ID 启动和可信姓名复用。
- JDK 17 编译 `forge-flow-client`、`forge-plugin-flow` 及所需上游 reactor：成功。
- 全工作区 `git diff --check`：通过。

### 验证边界

- 按用户偏好未启动、停止或重启 Admin/Flow 服务，未执行真实低代码流程发起，未写入业务数据。
- 本阶段要生效需要同时重启 Admin（加载池化 Flow 客户端）和 Flow（加载按需上下文与定义 ID 启动）；第一阶段的 Flow 发起配置缓存也会随 Flow 重启一并生效。
- 工作区存在多组用户未提交改动，本轮仅编辑 Spec 中列出的 Flow 客户端、流程启动服务、测试及本变更目录，未回退或提交其它改动。

## 2026-09-20 阶段收尾复核

- 全工作区再次执行 `git diff --check`：通过。
- 使用 JDK 17 执行 `mvn -pl forge-flow/forge-flow-client,forge-framework/forge-plugin-parent/forge-plugin-flow -am test-compile -DskipTests`：28 个相关 reactor 模块 `BUILD SUCCESS`，Flow 客户端和流程插件生产源码重新编译通过。
- 上述命令因项目 `-DskipTests` 配置未重复编译测试源码；测试结论继续引用上一轮已实际执行通过的 2 个 `FlowClientAutoConfigurationTest` 和 9 个流程启动定向用例。
- 首次收尾编译命令因手工收窄 `PATH` 后未包含本机 Maven 路径而在 Maven 启动前退出；改用 `/usr/local/apache-maven-3.9.3/bin/mvn` 后成功，属于命令环境问题，不是代码失败。
- 本轮仍未启动或重启 Admin/Flow，未执行真实流程 E2E，未写入业务数据。
