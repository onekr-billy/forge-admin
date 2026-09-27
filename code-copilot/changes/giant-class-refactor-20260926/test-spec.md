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

## 第十七轮增量验证：表单规则字段设置

- P0：迁出 form-create 规则的组件标识、必填开关与验证规则合成、样式及网格跨度映射；新增单测覆盖必填文案/默认触发、显式关闭必填、嵌套组件标识与网格跨度。
- P1：重跑 `LowcodeRuntimeConfigBuilderTest`、`GeneratedLowcodeRuntimeConfigBuilderTest` 和布局读取测试；generator Reactor 编译、`git diff --check` 与入口行数复核。
- 不修改原 form-create 规则和发布协议，不改变动态查询、保存或真实页面布局；不启动服务。

## 第十八轮增量验证：运行时编辑字段编译

- P0：将编辑字段的基础属性白名单、对齐及系统字段判断归入共享呈现支持类；将编辑字段 JSON、必填校验、公式只读、引用回显和 form-create 元数据映射归入独立编译器。新增定向测试覆盖字段规则、引用/动态选项源与基础属性过滤。
- P1：重跑运行配置入口、生成配置和主子表契约测试，尤其检查子表复用同一编辑字段编译器；generator Reactor 编译、`git diff --check` 与入口行数复核。
- 不改变运行配置字段顺序、SQL/表结构、业务状态或表单保存；不启动服务。

## 第十九轮增量验证：运行时列表列编译

- P0：迁出列表列渲染协议、组件默认类型、设计器点击配置和子表标题前缀处理；新增单测覆盖字典、开关、引用显示及字段宽度/固定列优先级。
- P1：重跑运行配置入口、生成配置与主子表协议测试；generator Reactor 编译、`git diff --check` 和入口行数复核。
- 保持列 JSON 键和值、动作列及字段顺序不变，不修改表格查询或发布协议；不启动服务。

## 第二十轮增量验证：运行字段目录选择

- P0：迁出主子表字段引用合并、列表网格选列、默认字段回退和托管流程状态列补齐规则；新增单测覆盖显式列顺序、旧快照回退、显式隐藏与已停用字段过滤。
- P1：重跑运行配置入口、生成配置、主子表及字段目录相关契约；generator Reactor 编译、`git diff --check`，确认 `LowcodeRuntimeConfigBuilder` 低于 1000 行且新协作者均低于 1000 行。
- 保留公开的托管流程状态列 API、运行时 JSON 顺序与字段可见性语义；不修改数据库、发布或真实流程状态，不启动服务。

## 第二十一轮增量验证：业务流程节点表单权限归一化

- P0：迁出节点表单绑定与主/子表字段权限的兼容归一化，并将共用 JSON/布尔值读取工具独立封装；新增单测覆盖对象字符串 `fields/children`、可读/可写/必填交叉约束、字段选择集合及重复节点去重。
- P1：重跑 `BusinessFlowServiceChildFieldControlTest`、表单资产合并和生命周期回归，generator Reactor 编译、`git diff --check`；入口签名和流程事务/状态机不变。
- 不修改 BPMN 节点配置所有权、实际任务保存权限或回调行为；按既有分工不启动 Admin/Flow 服务。

## 第二十二轮增量验证：审批表单字段目录与控件类型

- P0：迁出设计器 schema 字段目录、嵌套子表列、字段预览及控件类型兼容推断；新增单测覆盖组件优先级、必填/字典、子表去重、旧字段目录回退和弱类型推断。
- P1：重跑表单资产合并、审批字段、生命周期和节点权限测试；generator Reactor 编译、`git diff --check`，核对入口及新类行数。
- 保持表单资产 JSON 结构、字段顺序和控件名称兼容；不触碰流程状态机、事务或数据库，不启动服务。

## 第二十三轮增量验证：代码应用表单资产元数据合并

- P0：迁出 Provider 资产与绑定元数据的展示字段、字段目录、隐藏字段和删除标记合并规则；新增纯单测覆盖覆盖优先级、内部字段过滤、移除资产、仅配置资产与预览顺序。
- P1：重跑表单资产、代码应用权限、任务表单及生命周期相关测试；generator Reactor 编译、`git diff --check` 和类行数检查。
- 原入口仍负责按租户查询绑定与 Provider、审批表单权限执行及 Flowable 调用；不改持久化、事务或状态机，不启动服务。

## 第二十四轮增量验证：业务流程绑定配置编解码

- P0：迁出 DTO/JSON 互转、默认业务绑定、旧键回退、启动模式/变量映射归一化；新增单测覆盖旧键、空值/默认值、变量过滤、显式绑定优先和选项拷贝。
- P1：重跑业务绑定、表单资产、流程生命周期及业务键测试；generator Reactor 编译、`git diff --check` 和新旧类行数检查。
- 入口保留租户查询、保存事务、状态字段同步和 Flowable 调用；不改绑定表协议、BPMN 节点配置所有权或状态机，不启动服务。

## 第二十五轮增量验证：流程启动上下文组装

- P0：将流程变量映射、字段别名、调用方变量合并、服务端保留变量防覆盖及标题模板组装迁入独立 Assembler；新增单测覆盖 snake/camel 别名、主表包装、集合字段过滤、变量映射、保留变量拒绝、审批人变量透传及标题回退/扩展占位符。
- P1：重跑表单资产、业务键、生命周期和性能契约测试；执行 generator Reactor 编译、`git diff --check` 与新旧类行数检查。
- 原入口继续负责租户/绑定查询、服务端业务上下文写入、Flowable 启动、事务与状态机；不改变 BPMN、回调或表单权限，不启动服务。

## 第二十六轮增量验证：待办任务访问策略

- P0：将任务存在/待办状态、签收人/候选人、写权限、流程实例/业务 Key/节点/流程定义一致性校验迁入独立 Policy；新增单测覆盖已签收、候选人只读、未签收写入拒绝、已处理任务、串单阻断、重提业务 Key 与发起测试 Key 兼容。
- P1：重跑业务键、任务表单、生命周期和性能契约测试；执行 generator Reactor 编译、`git diff --check` 与类行数检查。
- 入口仍负责从 Flow 服务加载任务详情并解析当前登录人；Policy 只作身份与协议校验，不修改任务状态、候选人或 Flowable 数据，不启动服务。

## 第二十七轮增量验证：审批子表权限策略

- P0：将子表/字段权限匹配、保存白名单生成、主子表请求拆包、子表别名匹配及返回行字段裁剪迁入独立 Policy；新增单测覆盖 snake/camel 字段、带对象前缀的子表键、显式字段权限优先、只读/必填约束、伴随名称列保留与未配置子表移除。
- P1：重跑表单资产、子表控件、生命周期和性能契约测试；执行 generator Reactor 编译、`git diff --check` 与类行数检查。
- 原入口继续加载节点表单与业务记录并调用动态 CRUD 保存；Policy 不查询数据库、不写流程状态，不改变 BPMN 节点权限所有权，不启动服务。

## 第二十八轮增量验证：业务表单资产组装

- P0：将对象设计器/运行配置/字段注册表表单资产的收集、去重合并、运行态 schema 回退及字段目录标准化迁入独立 Assembler；新增单测覆盖多来源去重、较完整字段目录优先、字段注册表回退、系统字段过滤、运行态标题/布局与设计器 schema 优先。
- P1：重跑表单资产、子表控件、任务上下文和性能契约测试；执行 generator Reactor 编译、`git diff --check` 与类行数检查。
- 入口仍负责租户内运行配置与业务对象查询、应用页/代码 Provider 资产读取及任务权限执行；Assembler 不访问 Flowable、不保存业务记录，不启动服务。

## 第二十九轮增量验证：应用页面表单资产解析

- P0：将应用草稿页面/表单资产定位、稳定 formKey 编解码、旧 CRUD 页面默认资产兼容与两级短缓存迁入独立 Resolver；新增单测覆盖直接引用、单一默认资产、稳定 key 解析、schema 构建和缓存命中。
- P1：重跑表单资产、任务上下文和性能契约测试；执行 generator Reactor 编译、`git diff --check` 与类行数检查。
- Resolver 只读取应用设计快照并返回副本，入口仍负责租户业务对象解析、代码 Provider 合并及任务权限；不保存应用、不修改流程状态，不启动服务。
## 2026-09-26 第三十轮增量验证：任务表单大块拆分

- P0：`BusinessFlowService` 的待办/已办表单查询、暂存、代码表单 Provider、低代码主子表权限和必填校验行为保持不变。
- P0：BPMN 节点 `fieldPermissions` / `childPermissions` 继续作为服务端可读、可写、必填权限真源；只读上下文不得重新开放写权限。
- P0：事务注解、流程状态机、Flowable 调用和回调顺序不迁移到普通辅助对象。
- P1：表单 schema/layout、子表装配、代码表单协调、字段权限分别进入职责明确的组件；所有新增生产类少于 1000 行。
- P1：本轮目标一次性将 `BusinessFlowService` 减少至少 1500 行，并更新源码契约测试，不保留仅为测试服务的旧私有转发方法。
- 验证顺序：`git diff --check`、generator Reactor 编译、任务表单/子表/代码 Provider/生命周期/性能契约定向测试。
- 本轮仍不启动 Admin/Flow 服务、不连接 MySQL/Redis/Flowable；真实审批页面与流程端到端由既有分工在服务环境验证。

## 2026-09-26 第三十一轮增量验证：节点表单解析器

- P0：实际 Flowable 任务/流程表单信息优先于绑定回退；同一表单的页面 formKey 别名不得清空节点字段权限。
- P0：完整 task form 信息、query/variables 已带 formKey 时继续跳过重复 process-form RPC；应用页面资产只传轻量身份元数据。
- P1：将任务/流程表单 RPC、节点策略合成和应用页面表单资产选择迁入独立 Resolver，Facade 继续持有事务、业务记录读写和状态机。
- P1：更新表单资产与性能源码契约测试，直接验证 Resolver，不在 Facade 保留测试转发方法。
- 验证：generator Reactor 编译、业务流程 18 类定向回归、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：新增 `BusinessFlowTaskNodeFormResolverTest` 覆盖完整任务表单跳过实例 RPC、不完整任务表单回退实例 RPC、`app_` 表单键跳过对象资产扫描和结构化运行时表单引用优先级；相关 19 类共 94 项通过，0 失败、0 错误。模块 Reactor 编译与 `git diff --check` 通过。

## 2026-09-26 第三十二轮增量验证：任务运行身份与业务上下文解析

- P0：任务查询从 task form、应用页资产、流程关联快照恢复 objectId/configKey/objectCode/recordId 的优先级保持不变；测试业务 Key 不得误绑定真实记录。
- P0：发布运行配置、单据配置和业务对象的规范化查找顺序不变，严格模式仍只对非代码表单拒绝缺少运行配置。
- P1：提取通用业务 Key Codec、业务运行上下文 Resolver 与任务运行身份解析；Facade 保留任务保存、流程关联写入、事务和状态机。
- 验证：generator Reactor 编译、身份/业务 Key/任务表单/生命周期定向回归、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：新增 `BusinessFlowRuntimeContextResolverTest` 覆盖 task form 身份优先级、单据/对象元数据规范化和测试业务 Key 的记录隔离；旧业务 Key 测试改为直接验证 Codec/Resolver。相关 20 类共 97 项通过，0 失败、0 错误；模块 Reactor 编译与 `git diff --check` 通过。

## 2026-09-26 第三十三轮增量验证：业务待办列表展示装配

- P0：批量流程关联查询、对象身份缓存、低代码批量记录查询和代码 Provider 摘要批量构建保持原调用次数与回退顺序。
- P0：启动参数中的 `displayFields` 继续优先形成展示扩展，业务记录仅合并进 `businessParams`；对象名称与流程名称回退不变。
- P1：使用 Enricher + Batch Loader 将列表分组、批量读取和展示投影整体迁出 Facade，不保留私有转发方法。
- 验证：generator Reactor 编译、列表展示/身份/生命周期/性能契约回归、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：新增 `BusinessFlowListDisplayEnricherTest` 验证多条待办共享一次关联批量查询和一次动态记录批量查询，并锁定 `displayFields`、业务参数、摘要及流程名称投影；相关 21 类共 98 项通过，0 失败、0 错误。模块 Reactor 编译与 `git diff --check` 通过。

## 2026-09-26 第三十四轮增量验证：流程任务事件状态协调

- P0：发起人修改节点、驳回证据、修改待办快照和 `NEED_MODIFY`/`IN_PROCESS` 双状态切换语义不变；终态关联不得被延迟任务事件改写。
- P0：任务事件状态同步继续使用独立事务且失败只记录，不反向中断审批动作；表单保存自愈路径继续可用。
- P1：使用 Coordinator + State Transition 将任务事件、修改节点修复和运行态状态同步迁出 Facade，终态回调与公开事务入口保持原位。
- 验证：generator Reactor 编译、任务事件/生命周期/状态修复/任务表单定向回归、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：既有 `BusinessFlowServiceLifecycleTest` 继续覆盖发起人修改、重提回流程中、终态拒绝延迟任务事件和回调写入失败回滚；日志主体已切换到新 Coordinator。相关 21 类共 98 项通过，0 失败、0 错误；模块 Reactor 编译与 `git diff --check` 通过。

## 2026-09-26 第三十五轮增量验证：终态回调与状态写入策略

- P0：回调幂等、终态归一、配置状态字段写入、回调动作幂等键、业务事件与审批结果事件发布顺序保持不变；任何状态写入失败必须阻止关联表提前落终态。
- P0：单据模式、低代码业务绑定模式和 Adapter 跳过直写的状态策略保持一致，运行表名不一致继续拒绝。
- P1：先提取共享状态 Transition Service，再使用 Coordinator 迁出终态回调编排；公开 `@Transactional`/`@FlowCallback` 方法继续留 Facade。
- 验证：generator Reactor 编译、生命周期/状态字段/回调动作相关回归、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：新增 `BusinessFlowStatusTransitionServiceTest` 覆盖单据状态映射、低代码绑定状态映射、Adapter 跳过平台直写及运行表不一致拒绝；既有生命周期用例继续覆盖重复终态回调与状态写入失败时关联表不提前更新。相关 23 类共 105 项通过，0 失败、0 错误；generator Reactor 编译与 `git diff --check` 通过。

## 2026-09-26 第三十六轮增量验证：流程启动编排与绑定选择

- P0：手动、能力、兼容、触发器和业务流程节点五类启动入口的租户上下文、稳定/轮次业务 Key、权限开关和草稿运行配置策略保持不变。
- P0：流程模型 Key 选择顺序、启用 FLOW/历史 APPROVAL 绑定回退、启动锁、记录权限读取、Flowable 调用、关联落库及业务状态写入顺序保持不变。
- P1：使用 Resolver + Coordinator 整体迁出绑定候选选择和流程启动编排；公开事务入口继续留 Facade，新增生产类均少于 1000 行。
- 验证：generator Reactor 编译、业务 Key/启动锁/生命周期/绑定编解码/性能契约回归、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：新增 `BusinessFlowBindingResolverTest` 覆盖 FLOW 优先、历史 APPROVAL 回退、规范对象候选回退和停用绑定只读展示；新增 `BusinessFlowStartCoordinatorTest` 覆盖普通启动、能力入口稳定业务 Key、关联先落库再写运行态及运行中流程防重复远程启动。相关 25 类共 112 项通过，0 失败、0 错误；generator Reactor 编译与 `git diff --check` 通过。

## 2026-09-26 第三十七轮增量验证：任务表单上下文编排

- P0：待办、可办理、历史只读三类上下文的任务访问校验、Flowable 表单快照复用、运行身份解析与只读投影保持不变。
- P0：低代码/代码/外链表单分派，字段与子表权限、记录裁剪、UI 文档、打印应用身份及性能阶段日志保持原协议。
- P1：使用 Facade + Coordinator 迁出任务表单查询和上下文组装，并以独立 Profiler 承担跨 Resolver 的阶段采集；公开 API 保持不变。
- 验证：generator Reactor 编译、表单资产/子表权限/业务 Key/打印身份/性能契约回归、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：打印身份测试改为直接验证 Context Coordinator；性能契约改为检查一次 Flowable 表单快照、运行配置/对象复用与跨组件 Profiler。相关 25 类共 112 项通过，0 失败、0 错误；generator Reactor 编译与 `git diff --check` 通过。

## 2026-09-26 第三十八轮增量验证：任务表单命令编排

- P0：待办表单数据必须在 Flowable 审批/驳回调用前完成权限过滤、必填校验和主子表保存；任何保存失败不得继续流程动作。
- P0：同意、驳回、驳回发起人、退回、审计恢复、修改后重提和发起人撤回的路由、租户校验、终态回调与中间态回写顺序保持不变。
- P1：使用 Command Coordinator 整体迁出表单写入和任务动作；`BusinessFlowService` 仅保留公开 `@Transactional` 边界，新生产类必须少于 1000 行。
- 验证：generator Reactor 编译、业务流程定向回归、动作先保存后调用性能契约、`git diff --check` 和类行数检查；不启动真实服务。

验证结果：任务表单保存、动作路由、重提/撤回、终态与中间态同步已整体迁入 `BusinessFlowTaskCommandCoordinator`；动作先保存后调用契约与业务流程定向回归通过。

## 2026-09-26 第三十九轮增量验证：表单资产目录与绑定视图组装

- P0：应用页表单、对象设计器表单、发布运行表单、字段注册表回退和代码 Provider 资产的优先级、去重及内部字段可见性保持不变。
- P0：正式 FLOW 绑定与历史单据默认流程继续输出相同的主流程完整性摘要和兼容来源。
- P1：使用 Catalog + Assembler 迁出资产查询、Schema 回退和绑定视图映射，清理仅为旧测试存在的私有转发方法。
- 验证结果：generator Reactor 编译通过；业务流程相关 25 个测试类共 112 项通过，0 失败、0 错误；`BusinessFlowService` 953 行，新类分别 630/428/123 行，均低于 1000 行。

## 2026-09-26 第四十轮增量验证：动态 CRUD 写入规则引擎

- P0：模型约束、编辑 Schema `unique`、更新时未提交字段回退原值、空值忽略与租户内唯一性查询语义保持不变。
- P0：显式生成规则优先于约定编码规则，单据编号不重复生成；字段别名、允许写入集和主表 payload 回写保持原顺序。
- P1：使用 Validator + Policy 分别迁出唯一约束和自动编码/单据号生成；`DynamicCrudService` 保留事务、写入流程和调用顺序。
- 验证：generator Reactor 编译，动态 CRUD 自动生成/金额/结构化字段/加密/主子表回归，业务流程依赖回归，`git diff --check` 和类行数检查。

验证结果：新增 `DynamicCrudUniquenessValidatorTest` 覆盖模型唯一约束归一化、部分更新未改字段跳过及编辑 Schema 唯一声明；动态 CRUD 与业务流程相关 35 个测试类共 148 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 5642 行，新类 444/553 行。

## 2026-09-26 第四十一轮增量验证：动态 CRUD 树查询引擎

- P0：完整树与懒加载树的字段映射、排序、数据权限、祖先补链、只读标记、读取安全流水线和叶子节点协议保持不变。
- P0：普通查询与自定义查询的 `includeChildren` 展开、前端已展开多值兼容、系统组织/区域树和外部 CRUD 树源解析保持不变。
- P1：使用 Query Engine 整体迁出树配置解析、树查询/组装和层级条件展开；`DynamicCrudService` 仅保留运行时数据源、数据权限和读取流水线编排。
- 验证：新增树引擎单测，重跑动态 CRUD 与业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：新增 `DynamicCrudTreeQueryEngineTest` 覆盖配置别名与覆盖优先级、完整树祖先补链和只读标记、懒加载叶子探测、递归层级展开及前端预展开条件；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 4878 行，新引擎 772 行。

## 2026-09-26 第四十二轮增量验证：动态字段值处理流水线

- P0：读链路的字段别名、解密、金额分转元、结构化字段解析、虚拟公式、字典/名称翻译、脱敏及打印严格模式顺序保持不变。
- P0：写链路的金额元转分、结构化字段 JSON 化、持久化加密和脱敏占位值忽略规则保持不变；加密、金额精度和打印安全失败继续 fail-closed。
- P1：使用 Pipeline/Chain of Responsibility 集中字段值转换，入口只保留数据权限富化和事务编排；新生产类少于 1000 行。
- 验证：重跑金额、结构化字段、加密生命周期、打印读取、动态 CRUD 和业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：金额、结构化值和加密生命周期测试改为直接验证新 Pipeline，打印读取继续覆盖严格翻译/脱敏与数据权限富化；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 4165 行，新 Pipeline 782 行。

## 2026-09-26 第四十三轮增量验证：运行时关系查询规划器

- P0：主表/子表字段映射、关系方向推断、展示伴随字段、Join 条件、排序白名单、聚合子表行和展开行稳定 Key 协议保持不变。
- P0：分页、自定义查询、导出和主子表读写继续复用同一份运行时关系快照，不改变查询触发条件和保存模式判断。
- P1：使用 Planner/Compiler 迁出运行时关系编译和 Join 查询计划；入口仅消费不可变关系快照，新生产类少于 1000 行。
- 验证：重跑主子表、自动生成、动态查询与业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：分页、自定义查询、导出和主子表读写已统一委托 `DynamicCrudRuntimeRelationPlanner`；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 3455 行，新 Planner 875 行。

## 2026-09-26 第四十四轮增量验证：动态写入字段策略

- P0：编辑 Schema、模型字段、伴随名称列、存储公式字段和事务命令字段的白名单及真实列映射保持不变，系统字段继续不可写。
- P0：条件更新的期望值加密、数值比较、组合条件和存储公式的旧值合并/回写顺序保持不变；越权字段、缺失物理列和无权限记录继续拒绝。
- P1：使用 Policy 集中写入字段许可、命令条件和存储公式准备；入口只保留事务顺序与仓储调用，新生产类少于 1000 行。
- 验证：重跑自动生成、金额、结构化字段、加密、主子表和业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：编辑/模型字段白名单、事务命令条件、真实列映射和存储公式准备已统一委托 `DynamicCrudWriteFieldPolicy`；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 2974 行，新 Policy 665 行。

## 2026-09-27 第五十八轮增量验证：Flowable 流程图服务

- P0：流程实例可见性校验、历史/运行节点查询上限、当前节点与已完成节点高亮、无坐标时部署资源回退保持不变。
- P0：节点状态、连线状态、降级原因、任务处理人和批量用户展示投影保持不变；流程图服务不得参与审批动作或业务状态迁移。
- P1：使用 Facade + Assembler 边界隔离 BPMN XML/PNG 与视图组装，任务服务仅保留接口委托；新生产类少于 1000 行。
- 验证：执行 flow Reactor 编译，重跑流程图、组织用户批量查询、任务动作授权、状态流转、会签和待办性能契约；执行 `git diff --check` 和类行数检查，不启动真实服务。

验证结果：BPMN 图读取、图片生成、节点/连线状态及人员展示已统一委托 `FlowProcessDiagramService`；相关 8 个测试类共 31 项通过，0 失败、0 错误。flow Reactor 编译与 `git diff --check` 通过；`FlowTaskServiceImpl` 2848 行，新服务 768 行。

## 2026-09-26 第四十五轮增量验证：主子表持久化引擎

- P0：主子表详情的数据权限、子表外键修复、关系值解析、读取流水线和审计元数据保持不变。
- P0：主表字段过滤、子表必填/长度/数值约束、merge/replace 保存、越权行拒绝和聚合刷新顺序保持不变。
- P1：使用 Engine 集中主子表详情读取与持久化，Facade 仅保留事务入口、数据权限条件和审计挂载；新生产类少于 1000 行。
- 验证：重跑主子表、任务字段权限、金额、结构化字段、加密和业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：详情查询、外键候选回退、子表校验和 merge/replace 写入已统一委托 `DynamicCrudMasterDetailEngine`；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 2448 行，新 Engine 902 行。

## 2026-09-27 第四十六轮增量验证：审批节点可编辑数据协调器

- P0：主表可写字段、子表关系别名、子表读/增/改/删权限和只读快照过滤保持不变，所有拒绝必须发生在仓储写入前。
- P0：审批表单保存继续强制 merge 语义，旧值公式、唯一约束、加密/金额/结构化值转换、行归属校验和聚合刷新顺序保持不变。
- P1：使用 Coordinator 隔离审批节点权限投影与持久化；Facade 仅保留事务、审计、运行上下文及普通更新回退，新生产类少于 1000 行。
- 验证：重跑主子表、任务字段权限和业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：审批节点主子表权限归一化、行级动作校验及受限保存已委托 `DynamicCrudTaskEditableCoordinator`；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 2151 行，新 Coordinator 497 行。

## 2026-09-27 第四十七轮增量验证：普通 Join 跨表持久化引擎

- P0：普通 Join 模型新增/更新的主子字段分流、公式和唯一约束、真实关联值解析、子表 insert/upsert 及聚合刷新顺序保持不变。
- P0：主表数据权限、主键剔除、脱敏占位值忽略、金额/结构化值/加密转换和空写入拒绝语义保持不变。
- P1：使用 Persistence Engine 隔离跨表写入，Facade 只保留事务入口与数据权限条件；新生产类少于 1000 行。
- 验证：重跑动态 CRUD 和业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：普通 Join 模型的跨表新增、更新和子表 upsert 已委托 `DynamicCrudJoinedPersistenceEngine`；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 1955 行，新 Engine 284 行。

## 2026-09-27 第四十八轮增量验证：动态读模型协调器

- P0：分页、同步/异步导出、定时候选、树、自定义查询、详情、打印和批量读取的数据源上下文、数据权限及读取流水线保持不变。
- P0：Join 查询触发、子表聚合/展开行键、搜索字段与操作符白名单、设计预览配置选择和审计元数据保持不变。
- P1：使用 Read Coordinator 聚合所有读取用例，Facade 仅保留稳定公开 API；新生产类少于 1000 行。
- 验证：重跑树、打印、搜索协议、主子表及业务流程依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：所有动态读取入口已委托 `DynamicCrudReadCoordinator`；旧搜索协议测试改为直接验证 Coordinator，不再反射 Facade 私有实现。动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 1308 行，新 Coordinator 885 行。

## 2026-09-27 第四十九轮增量验证：动态写模型协调器

- P0：普通表单、内部自动化与事务命令的字段白名单、真实列映射、主键剔除、公式、唯一约束、金额/结构化值/加密处理顺序保持不变。
- P0：主子表/普通 Join 分派、数据权限条件、expected 条件、数值上下界、行锁门禁、审计来源和聚合刷新语义保持不变。
- P1：使用 Mutation Coordinator 聚合写命令执行，Facade 仅保留公开事务、运行数据源、审计和数据权限边界；所有生产类少于 1000 行。
- 验证：重跑动态 CRUD、业务流程生命周期和表单资产依赖回归；执行 generator Reactor 编译、`git diff --check` 和类行数检查，不启动真实服务。

验证结果：普通/内部/事务命令的新增、更新、原子数值调整与行锁门禁已委托 `DynamicCrudMutationCoordinator`；动态 CRUD 与业务流程相关 36 个测试类共 153 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`DynamicCrudService` 770 行，新 Coordinator 496 行。

## 2026-09-27 第五十轮增量验证：业务对象表单 Schema 组装器

- P0：持久化 formDesignerSchema、编辑区内嵌 Schema、旧 fieldRefs/fieldSettings 和 form-create 规则的回退优先级保持不变。
- P0：字段组件推断、默认占位符、必填规则、栅格布局、表单级布局以及 H5 页面分区/底部栏透传保持不变。
- P1：使用 Assembler + Adapter 迁出默认表单生成与历史协议迁移；设计器聚合服务仅保留窄委托，新生产类少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：表单 Schema 解析、默认组装和两类历史协议迁移已委托 `BusinessObjectFormSchemaAssembler`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 3908 行，新 Assembler 765 行。

## 2026-09-27 第五十一轮增量验证：业务对象运行时表单投影器

- P0：表单字段引用、隐藏字段动态可见性、fieldSettings 合并、嵌套容器布局和页面组件保留规则保持不变。
- P0：标签、栅格、校验、动态选项、form-create 元数据、弹窗/抽屉/工作区打开方式及折叠配置保持不变。
- P1：使用 Projector 迁出设计协议到运行时编辑区协议的单向编译；聚合服务保留编译顺序与模型/页面上下文，新生产类少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：运行时字段设置、布局树与编辑区属性已委托 `BusinessObjectRuntimeFormProjector`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 3471 行，新 Projector 623 行。

## 2026-09-27 第五十二轮增量验证：业务对象视图 Schema 投影器

- P0：搜索、列表、详情默认字段、排序、对齐、格式化和覆盖项保持不变。
- P0：主表与子表字段引用清洗、列表网格 searchFieldRefs/searchFieldSettings 同步以及既有非模型 fieldSettings 保留规则保持不变。
- P1：使用 Projector 统一视图 Schema 的默认组装、清洗与页面区域投影；Facade 仅保留编译时序和窄委托，新生产类少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：视图 Schema 解析、默认组装、字段清洗和搜索/列表/详情区域投影已委托 `BusinessObjectViewSchemaProjector`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 3131 行，新 Projector 548 行。

## 2026-09-27 第五十三轮增量验证：业务对象字段联动策略

- P0：旧 linkageSchema 与表单 governance.fieldLinkages 的优先级、深复制和设置继承保持不变。
- P0：字典、远程、组织和对象引用联动到字段 cascade/dict/reference 元数据的翻译规则保持不变，停用规则和旧托管元数据继续正确清理。
- P1：使用 Policy + Translator 隔离兼容决策和运行时元数据翻译；Facade 保留调用时序，新生产类少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：联动配置解析、治理规则合并和字段 cascade 翻译已委托 `BusinessObjectLinkagePolicy`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 2979 行，新 Policy 232 行。

## 2026-09-27 第五十四轮增量验证：业务对象关系协调与运行时投影

- P0：关系保存/删除、关系类型与状态、目标对象显示字段、主子表外键自动补齐和关系快照恢复语义保持不变。
- P0：运行时模型 relations、主/子 page modelRefs、既有中文页签、表单子表顺序、编辑区字段引用和 master-detail 布局切换保持不变。
- P1：使用 Coordinator 管理关系写入生命周期，使用 Projector 隔离持久化关系到运行时模型/页面协议的投影策略；Facade 仅保留事务入口和窄委托，新生产类均少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：关系持久化、自动主子表关系与快照恢复已委托 `BusinessObjectRelationCoordinator`，运行时 relations/modelRefs 投影已委托 `BusinessObjectRelationProjector`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 2151 行，Coordinator 553 行，Projector 592 行。

## 2026-09-27 第五十五轮增量验证：业务对象字段设计策略

- P0：设计字段重建时字典、记录选择器、编码规则、引用对象、公式、级联、动态选项和多选存储元数据保持不变。
- P0：通用输入组件不得覆盖已有业务组件；系统字段保留、字段 Schema 构建和模型归一化顺序保持不变。
- P1：使用 Policy 隔离字段元数据合并与业务组件保护；清理没有生产调用且契约明确禁止进入保存路径的旧 Payload 降级链，新生产类少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：字段重建与运行时元数据保护已委托 `BusinessObjectFieldDesignPolicy`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 1684 行，新 Policy 231 行。

## 2026-09-27 第五十六轮增量验证：历史页面协议适配器

- P0：旧 searchSchema/editSchema/columnsSchema 的字段引用、控件、字典、校验、列宽、排序、固定列和渲染配置迁移保持不变。
- P0：Page Zone 别名归一、重复区域合并、默认区域补齐和 tree-panel 布局识别保持不变；新格式显式配置继续优先于旧格式。
- P1：使用 Adapter 隔离旧协议翻译；删除前序 Assembler/Projector 已接管后无调用的旧工具链，新生产类少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：历史页面协议与区域归一已委托 `BusinessObjectLegacyPageSchemaAdapter`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 1160 行，新 Adapter 411 行。

## 2026-09-27 第五十七轮增量验证：草稿 Schema 持久化网关

- P0：对象关联模型/运行配置的查找优先级、默认模型 Schema、领域兜底、命名规范、审计策略与运行数据源快照保持不变。
- P0：草稿校验、模型表与配置表写入字段、版本递增、发布状态、租户/审计/逻辑删除策略和表单 Schema 运行选项合并保持不变。
- P1：使用 Gateway 隔离 Mapper、领域服务、数据源服务和配置持久化；Facade 只保留设计事务与编排，新生产类均少于 1000 行。
- 验证：执行 generator Reactor 编译，重跑业务对象设计器/发布/数据库同步及运行布局相关测试，执行 `git diff --check` 和类行数检查；不启动真实服务。

验证结果：模型/运行配置加载、Schema 补全、校验和持久化已委托 `BusinessObjectDraftSchemaGateway`；相关 14 个测试类共 70 项通过，0 失败、0 错误。generator Reactor 编译与 `git diff --check` 通过；`BusinessObjectDesignerService` 904 行，新 Gateway 467 行。
