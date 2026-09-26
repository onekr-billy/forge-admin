# 增量验证计划

## 本轮 P0

1. `git diff --check`。
2. `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`。
3. `BusinessFlowStartLockManagerTest`：事务完成前互斥、异常释放、Redisson 分布式锁优先。
4. `RuntimeFieldMetadataCompilerTest`：字典去重与顺序、脱敏和加密规则。

## 本轮 P1

运行 `BusinessFlowServiceLifecycleTest`、`BusinessFlowServiceBusinessKeyTest`、`LowcodeRuntimeConfigBuilderTest` 和 `GeneratedLowcodeRuntimeConfigBuilderTest`，检查入口行为没有因委托改变。

不启动 Admin/Flow 服务或改动数据库；本轮没有 API/SQL 变更，也未具备本机 MySQL/Redis 端到端环境。后续状态回调和发布阶段必须升级到服务级验证。

## 第二轮增量验证：树与主子表编译

- P0：`git diff --check`；generator Reactor 编译；`RuntimeTreeConfigBuilderTest`、`RuntimePageRelationResolverTest`、`RuntimeChildTableCompilerTest`，分别覆盖外部树来源优先级、字段名归一化、关系推断、树 API 协议和子表保存模式。
- P1：重跑 `LowcodeRuntimeConfigBuilderTest`、`GeneratedLowcodeRuntimeConfigBuilderTest`、`RuntimeFieldMetadataCompilerTest`，确认整体序列化配置及上一轮字段元数据保持兼容。
- 本轮不修改 Flowable 回调、数据库或 HTTP 协议；因此不启动 Admin/Flow 服务。后续流程状态修复阶段再做数据库和真实流程联调。

## 第三轮增量验证：流程状态写入与 1000 行规范

- P0：`git diff --check`、generator Reactor 编译；新增状态修复组件单测覆盖发布版/草稿版配置选择、状态字段白名单和写入失败传播。
- P1：重跑 `BusinessFlowServiceLifecycleTest`、`BusinessFlowStatusFieldServiceTest`、`BusinessFlowServiceBusinessKeyTest`，确认任务创建/完成、终态回调、重复回调和字段回写仍按原路径执行。
- 仅在当前变更的编译及单测范围验证；真实 MySQL/Redis/Flowable 端到端验收按已有用户偏好由用户执行，不将未执行的服务级测试写成通过。

## 第四轮增量验证：子表字段选择与页面引用字段解码

- P0：`git diff --check`、generator Reactor 编译；新增子表字段编译器单测覆盖“显示字段”优先级、子表编辑区排序、外键/系统/只读字段过滤与字段引用保留。
- P1：重跑 `LowcodeRuntimeConfigBuilderTest`、`GeneratedLowcodeRuntimeConfigBuilderTest`、`RuntimeChildTableCompilerTest`，并增加完整主子表运行配置片段的 JSON 契约断言，确认保存模式和主表单去重不变。
- 不修改 SQL、API 或真实流程状态；按用户现有偏好不自动启动 Admin/Flow 服务，也不连接数据库。若仅 JVM attach 限制导致 Mockito 初始化失败，应使用已验证的测试运行权限重试并分别记录。

## 第五轮增量验证：运行时动作协议编译

- P0：编译 generator Reactor；新增动作编译器测试覆盖默认操作与自定义键去重、位置过滤、权限码回退、参数和值的默认协议。
- P1：重跑 `LowcodeRuntimeConfigBuilderTest` 与 `GeneratedLowcodeRuntimeConfigBuilderTest`，确认列表操作列和 `options` 的四类动作仍与原布局配置一致；再次执行 `git diff --check`。
- 本轮为纯配置编译器搬迁，不改 HTTP/SQL/业务状态；不启动实际服务。

## 第六轮增量验证：引用关系展示与查询配置

- P0：新增引用关系编译器单测，覆盖来源字段归一化、展示字段候选优先级、`relationName` 翻译键、查询源参数与非 REFERENCE 关系过滤。
- P1：重跑运行配置入口测试及生成配置回归，检查搜索、列表、编辑三处关系协议和 `joinConfig` 别名；模块编译与 `git diff --check` 必须通过。
- 仅重排纯编译逻辑；未授权真实服务/数据状态改动，故不执行端到端服务联调。

## 第七轮增量验证：FlowTaskNotifyListener 通知内容渲染

- P0：`forge-plugin-flow` Reactor 编译；新增纯渲染器单测覆盖 H5 hash 基址、模板变量 URL 编码、绝对 URL、卡片 HTML 转义/截断和默认文案。
- P1：审视监听器异步 `AFTER_COMMIT` 注解、原有依赖注入与租户恢复路径未移动；运行插件现有通知/事件相关单测，检查拆分后类行数降至 1000 以下，最后执行 `git diff --check`。
- 本轮仅迁出纯字符串渲染；通知投递、Redis/Webhook、真实 Flowable 不变且不启动服务。未执行的真实协同渠道联调不得标记为通过。

## 第八轮增量验证：FlowTaskEventListener 用户身份解析

- P0：`forge-plugin-flow` Reactor 编译；新协作者单测覆盖数字用户 ID、显示名唯一匹配、已有姓名优先及流程定义 Key 的标准格式回退。
- P1：重跑 `FlowTaskEventListenerTest`、创建时自动签收与超时契约测试，核对任务镜像及租户处理未变；检查事件监听器行数降到 1000 内。
- 不动 Flowable 引擎事件订阅、表写入或外部通知，保留原事件回调边界。真实服务联调由用户按既有分工执行。

## 第九轮增量验证：业务流程运行视图组装

- P0：抽运行记录/节点时间线 VO 与节点名称映射协作者后，编译 generator Reactor；直接测试雪花 ID 字符串化、时间线字段映射、同版本节点名称解析和损坏快照回退。
- P1：重跑 `BusinessProcessOrchestratorTest` 与相关运行流程测试，检查启动、重试、取消公开协议未变；执行 `git diff --check` 并确认 `BusinessProcessOrchestrator` 小于 1000 行。
- 不修改状态机、SQL、Flowable 调用、事务注解或真实数据库；本轮不启动服务。

## 第十轮增量验证：流程监控视图组装

- P0：抽取管理员流程实例视图、时长格式化和统计映射后，编译 flow Reactor；新增测试覆盖待办任务显示名、未认领/降级回退、时长及统计空值/数字转换。
- P1：重跑 `FlowMonitorBatchQueryContractTest`、`FlowMonitorTaskTreeContractTest`，确认批量任务摘要查询、任务树结构与公开接口未变；执行 `git diff --check` 并确认 `FlowMonitorServiceImpl` 小于 1000 行。
- 不改变租户校验、Mapper 查询、清理事务或实际流程状态；不启动 Admin/Flow 服务。

## 第十一轮增量验证：运行时表单容器选项

- P0：将弹窗宽度优先级、modal/drawer/flat/tabWorkspace 模式和工作区默认值迁出；单测覆盖编辑配置覆盖表格配置、默认 900px 回退、auto/百分比宽度和非法模式归一化。
- P1：重跑运行配置入口与生成配置测试，检查 JSON 中 `modalWidth`、`modalType`、`formOpenMode`、`tabWorkspace` 保持一致；generator Reactor 编译与 `git diff --check` 通过。
- 纯配置编译迁移，不修改表单保存或 API 协议，不启动服务。

## 第十二轮增量验证：树字段选项源装饰

- P0：迁出树字段 `treeSelect` 类型/选项源装饰，直接测试左树右表外部源、当前对象父级字段、本表树 API 资格与已有选项源保留。
- P1：重跑 `RuntimeTreeConfigBuilderTest`、`LowcodeRuntimeConfigBuilderTest`、`GeneratedLowcodeRuntimeConfigBuilderTest`，确认搜索/编辑字段和 `treeConfig` JSON 协议不变；generator Reactor 编译与 `git diff --check` 通过。
- 不改变树数据查询、Mapper、表单保存或运行时状态；不启动服务。

## 第十三轮增量验证：运行字段组件协议

- P0：迁出搜索/编辑组件类型归一、记录选择器判定、动态选项源伴随字段与占位符规则；新增单测覆盖组件别名、过时搜索覆盖回退、`objectReference` 下拉不误变弹窗、标签回写字段和选择型占位符。
- P1：重跑 `LowcodeRuntimeConfigBuilderTest`、`GeneratedLowcodeRuntimeConfigBuilderTest` 和树/引用配置测试，检查搜索与编辑 JSON 协议；generator Reactor 编译及 `git diff --check` 必须通过。
- 不改数据查询、表单保存或真实服务状态，不启动 Admin/Flow。

## 第十四轮增量验证：在线 DDL 安全策略

- P0：仅迁出在线 DDL 白名单与统一拒绝异常；新增纯策略测试覆盖创建表、追加列/索引、表注释允许，以及修改/删除、空语句拒绝。
- P1：重跑 `LowcodeDdlAdditiveColumnTest`、`LowcodeDdlExplicitIndexTest`，检查预览、兼容修改与执行路径仍调用同一策略；generator Reactor 编译与 `git diff --check` 通过，确认 `LowcodeDdlService` 低于 1000 行。
- 仅静态与单测验证；不连接数据库、不执行真实 DDL。

## 第十五轮增量验证：BPMN 部署预检

- P0：迁出流程 Key 提取/替换、重复连线兼容规范化、连线引用、可执行节点与网关条件校验；新组件单测覆盖有效最小流程、缺失起止节点、悬空连线、无审批人、未配置条件与不支持的执行属性。
- P1：重跑 `FlowModelServiceImplTest`、`FlowModelDeploymentValidationContractTest` 及模型版本相关契约测试；flow Reactor 编译、`git diff --check` 和入口行数低于 1000 行。
- 不改变发布事务、部署/版本保存、候选人解析或现有 BPMN XML；不启动 Flow 服务。

## 第十六轮增量验证：设计器布局元数据读取

- P0：迁出列表布局块属性/字段设置读取、表单规则和画布元素遍历；新增单测覆盖 AiCrudPage 搜索字段配置优先于表格字段配置、表格全局对齐回退、嵌套表单规则及画布字段读取。
- P1：重跑 `LowcodeRuntimeConfigBuilderTest` 和 `GeneratedLowcodeRuntimeConfigBuilderTest`，确认搜索/表格/编辑 JSON 协议；generator Reactor 编译、`git diff --check` 和入口行数复核。
- 只移动设计态元数据读取，不修改页面布局持久化、发布、动态 SQL 或字段权限；不启动服务。
