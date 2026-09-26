# 执行记录

## 2026-09-26 首个切口

- 变更：提取业务流程启动锁管理与低代码字段元数据编译，原入口构造签名、事务释放时机及运行配置 JSON 协议不变。
- `git diff --check`：通过。
- 在 `forge-server/` 执行 `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：33 个 Reactor 模块编译成功（`BUILD SUCCESS`，03:51）。本机原无 JDK/Maven，使用临时目录中的校验过官方哈希的 JDK 17 和 Maven 3.9.16；未加入仓库。
- 执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowStartLockManagerTest,RuntimeFieldMetadataCompilerTest,BusinessFlowServiceLifecycleTest,BusinessFlowServiceBusinessKeyTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`：最终执行通过；6 个测试类合计 38 个测试，0 失败、0 错误。第一次因新增测试错误地认为第二个同字典字段不生成翻译规则而失败；修正预期后通过，生产代码无须因此更改。随后补上 Redisson 分支测试并重跑通过。
- 未覆盖：真实 Redis 分布式锁、数据库事务与 Flowable 联调；本轮未启动服务、未修改 API/SQL/状态机。后续拆状态修复和回调时必须补服务级验证。本轮无服务 PID 需停止。

## 2026-09-26 第二个切口

- 变更：从 `LowcodeRuntimeConfigBuilder` 提取运行时关系解析、树配置和主子表配置外壳；子表字段渲染仍通过原入口的策略回调执行，未变更字段级编译逻辑。入口文件由上一轮的 3,595 行降至 3,115 行。
- `git diff --check`：通过。
- 在 `forge-server/` 执行 `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：修正机械搬迁时的两处编译错误（方法签名位置、剩余单键查找）后通过，最终命令退出码 0。
- 执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=RuntimeChildTableCompilerTest,RuntimeTreeConfigBuilderTest,RuntimePageRelationResolverTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest,RuntimeFieldMetadataCompilerTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 个测试类共 29 项，0 失败、0 错误。
- 最终合并运行首轮与本轮 9 个相关测试类（同一 Maven 命令的 `-Dtest` 列表增加 `BusinessFlowStartLockManagerTest,BusinessFlowServiceLifecycleTest,BusinessFlowServiceBusinessKeyTest`）：合计 42 项，0 失败、0 错误，命令退出码 0。
- 未覆盖：真实数据库/Flowable 联调；本轮没有 API、SQL 或状态机变更，也未启动服务。完整发布版 JSON 快照与子表字段渲染迁移留到后续切口。本轮无服务 PID 需停止。

## 2026-09-26 第三个切口

- 变更：从 `BusinessFlowService` 提取租户内运行配置解析与流程状态字段修复协作者；保留公开构造器的原有 13 个参数及 Spring 单构造器装配、回调事务边界和写入失败传播。补未发布草稿流程的终态回调回归。`BusinessFlowService` 当前 9004 行；新类分别 35/150 行，仍未达到单类 1000 行目标。
- 规范：`AGENTS.md` 与 `code-copilot/rules/coding-style.md` 写入 Java 单类原则上不超过 1000 行、新增类不得超限、存量类分阶段收敛和逐阶段验证要求。
- `git diff --check`：通过。generator Reactor 的 `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。
- 首次定向测试在默认沙箱下因 Mockito inline mock maker 无法附加 JVM agent 而于测试初始化阶段报错（20 项均未进入业务断言）；以允许本机 JVM attach 的权限重跑同一命令后退出码 0。`BusinessFlowStatusRepairServiceTest` 5 项、`BusinessFlowServiceLifecycleTest` 7 项、`BusinessFlowStatusFieldServiceTest` 4 项、`BusinessFlowServiceBusinessKeyTest` 4 项，共 20 项，0 失败、0 错误。
- 未覆盖：真实 MySQL/Redis/Flowable 联调和其他巨型类的 1000 行收敛；用户按既有偏好执行服务级验证，本轮未启动服务、未修改 SQL/API，且无服务 PID 需停止。

## 2026-09-26 第四至第六个切口：运行配置编译

- 从 `LowcodeRuntimeConfigBuilder` 提取 `RuntimePageRefFieldFactory`、`RuntimeChildFieldCompiler`、`RuntimeActionCompiler`、`RuntimeRelationLookupCompiler` 及共享系统字段集合。页面引用解码、主子表字段筛选/排序、动作协议和关系查找分别有单一职责；入口仍组装运行配置并复用原编辑字段渲染。入口由 3115 行降至 2556 行。
- 补 4 个协作者测试类与完整主子表 JSON 片段断言。首次测试的快照预期漏列原有 `placeholder` 字段、动作测试有多余右括号；修正测试后生产逻辑不因测试预期改变。
- generator Reactor 定向命令：`mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessProcessRunViewAssemblerTest,BusinessProcessOrchestratorTest,RuntimeRelationLookupCompilerTest,RuntimeActionCompilerTest,RuntimeChildFieldCompilerTest,RuntimePageRefFieldFactoryTest,RuntimeChildTableCompilerTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`。本轮最终 9 个类、48 项，0 失败、0 错误，退出码 0。

## 2026-09-26 第七至第十个切口：Flowable 监听、编排与监控

- `FlowTaskNotifyListener` 只迁出 H5 链接、卡片描述和转义/截断渲染到 `FlowNotificationContentRenderer`，入口 960 行；异步 `AFTER_COMMIT`、租户上下文、投递渠道未改。`FlowTaskEventListener` 只迁出用户 ID/显示名/流程 Key 解析到 `FlowTaskIdentityResolver`，入口 957 行；事件订阅和任务镜像未改。
- `BusinessProcessOrchestrator` 迁出运行记录、节点时间线 VO 和版本节点名称映射到 `BusinessProcessRunViewAssembler`，入口 953 行；运行状态机和事务未改。
- `FlowMonitorServiceImpl` 迁出管理员实例/统计/任务树纯视图映射到 `FlowMonitorViewAssembler`，入口 972 行；租户校验、批量任务查询、清理事务未改。同步更新任务树源码契约检查位置。
- flow Reactor 定向命令：`mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am -Dtest=FlowMonitorViewAssemblerTest,FlowMonitorBatchQueryContractTest,FlowMonitorTaskTreeContractTest,FlowNotificationContentRendererTest,FlowTaskIdentityResolverTest,FlowNotifyConfigTest,FlowTaskEventListenerTest,FlowTaskEventListenerCreatedAutoClaimTest,FlowTaskEventListenerTimeoutContractTest -Dsurefire.failIfNoSpecifiedTests=false test`。最终 9 个类、26 项，0 失败、0 错误，退出码 0。预期异常回退测试产生 WARN 堆栈，但断言通过。
- 首次 flow 编译在默认沙箱下因本机 `.m2` 访问受限，改用获准权限后同一代码编译通过；未发现编译缺陷。最终分别执行 generator 与 flow Reactor 的 `mvn -q -pl ... -am -DskipTests compile`，均退出码 0。`git diff --check` 通过。
- 未覆盖：Admin/Flow 服务、真实数据库/Redis/Flowable、协同通知渠道联调；遵循用户既有偏好，本轮未启动服务、未写数据库、无服务 PID。`LowcodeRuntimeConfigBuilder`、`BusinessFlowService` 及其他目标类仍超 1000 行，后续继续按边界拆分，不标为完成。

## 2026-09-26 第十一个切口：表单容器选项

- 从 `LowcodeRuntimeConfigBuilder` 提取弹窗宽度优先级、打开方式与多标签工作区默认值到 `RuntimeFormContainerOptionsCompiler`；入口由 2556 行降至 2467 行。保留现有 JSON 键名和计算顺序。
- 增加 `RuntimeFormContainerOptionsCompilerTest` 3 项，覆盖编辑区/CRUD 配置优先级、默认宽度、非法模式和工作区参数覆盖。与前述 9 个相关 generator 测试类合并执行，共 10 类、51 项，0 失败、0 错误，退出码 0。
- 本轮只移动纯配置计算，不更改表单保存、接口或数据库；真实服务验证仍未执行。

## 2026-09-26 第十二个切口：树字段装饰

- 从 `LowcodeRuntimeConfigBuilder` 迁出运行时树字段组件类型、当前对象 tree API 选项源及已有源保留逻辑到 `RuntimeTreeFieldDecorator`；入口由 2467 行降至 2283 行。共享的有效选项源判断仍由搜索字段调用新组件，避免两套标准。
- 首次定向编译发现搜索字段也使用原判断方法，补共享调用后重跑通过。`RuntimeTreeFieldDecoratorTest` 3 项，加上 `RuntimeTreeConfigBuilderTest`、`RuntimeFormContainerOptionsCompilerTest`、`LowcodeRuntimeConfigBuilderTest`、`GeneratedLowcodeRuntimeConfigBuilderTest`，共 5 类、32 项，0 失败、0 错误，退出码 0。`git diff --check` 通过。
- 树 API、查询、Mapper 与运行时状态未更改；本轮未启动服务，真实数据库/Flowable 验证仍未执行。

## 2026-09-26 第十三至第十四个切口：字段组件与在线 DDL 策略

- `RuntimeFieldComponentResolver` 接管搜索/编辑组件归一、记录选择器判定、动态选项伴随标签与占位符；`LowcodeRuntimeConfigBuilder` 由 2283 行降至 2077 行。新增 4 项解析器单测，整体运行配置入口测试保持通过。
- `LowcodeOnlineDdlPolicy` 接管预览检查和实际执行共用的在线 DDL 白名单及拒绝异常；`LowcodeDdlService` 由 1022 行降至 985 行。新增 3 项策略测试，覆盖追加 DDL 允许、修改/删除拒绝与预检一致性。SQL 生成、执行方法及异常文案不变。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile` 退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=LowcodeOnlineDdlPolicyTest,LowcodeDdlAdditiveColumnTest,LowcodeDdlExplicitIndexTest,RuntimeFieldComponentResolverTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 类共 36 项，0 失败、0 错误，退出码 0。`git diff --check` 通过。
- 未连接数据库、未执行真实 DDL，也未启动 Admin/Flow 服务；本轮无服务 PID 需停止。真实发布和在线 DDL 的服务级验证仍待用户按既有分工执行。

## 2026-09-26 第十五个切口：模型 BPMN 部署预检

- 从 `FlowModelServiceImpl` 提取流程 Key、process id 替换、重复连线与旧会签表达式兼容规范化、连线引用及结构/可执行节点校验到无状态 `FlowModelBpmnPreflight`；原服务由 1243 行降至 979 行，新组件 249 行。部署入口的预检顺序、异常文案、租户和版本事务保持不变。源码契约测试改为同时验证入口调用和组件内错误路径。
- flow Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am -Dtest=FlowModelBpmnPreflightTest,FlowModelServiceImplTest,FlowModelDeploymentValidationContractTest,FlowModelVersionCleanupContractTest,FlowModelVersionGovernanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：5 类共 18 项，0 失败、0 错误，退出码 0。
- 未启动 Flow 服务、未连接真实数据库/Flowable，故未做部署端到端验收；本轮无服务 PID 需停止。真实 BPMN 部署与旧模型兼容仍需服务级验证。

## 2026-09-26 第十六个切口：运行时设计器布局读取

- 从 `LowcodeRuntimeConfigBuilder` 提取列表布局块的字段设置与属性、嵌套表单规则、画布元素和通用区域定位到无状态 `RuntimeDesignerLayoutReader`；入口由 2077 行降至 1924 行，新组件 179 行。原字段设置覆盖顺序、搜索区 `searchFieldSettings` 优先级、表格全局对齐回退和运行时 JSON 字段不变。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=RuntimeDesignerLayoutReaderTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`：3 类共 27 项，0 失败、0 错误，退出码 0。
- 未启动 Admin 服务或连接真实数据库；本轮不改设计态持久化、发布或动态 SQL。完整线上配置发布验收仍需服务级环境。

## 2026-09-26 第十七个切口：表单规则运行时映射

- 从 `LowcodeRuntimeConfigBuilder` 提取 form-create 组件类型、必填/验证规则、样式与网格跨度映射到无状态 `RuntimeFormRuleSettingResolver`；入口由 1924 行降至 1724 行，新组件 227 行。保留原字段设置覆盖顺序，网格列数仅在规则声明 `col` 时按需解析。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=RuntimeFormRuleSettingResolverTest,RuntimeDesignerLayoutReaderTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`：4 类共 30 项，0 失败、0 错误，退出码 0。`git diff --check` 通过。
- 未启动 Admin 服务或连接真实数据库；真实设计器保存/发布/编辑联调仍未覆盖，本轮无服务 PID 需停止。

## 2026-09-26 第十八个切口：编辑字段编译

- 从 `LowcodeRuntimeConfigBuilder` 提取编辑字段渲染、运行时验证规则、公式只读、引用伴随列和 form-create 元数据到 `RuntimeEditFieldCompiler`；基础属性白名单、对齐及系统字段判断迁入 `RuntimeFieldPresentationSupport`。入口由 1724 行降至 1373 行；新类分别 294/115 行，均低于 1000 行。主子表回调仍调用同一编译器。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=RuntimeEditFieldCompilerTest,RuntimeFormRuleSettingResolverTest,RuntimeDesignerLayoutReaderTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest,RuntimeChildTableCompilerTest,RuntimeRelationLookupCompilerTest -Dsurefire.failIfNoSpecifiedTests=false test`：7 类共 37 项，0 失败、0 错误，退出码 0。`git diff --check` 通过。
- 未启动 Admin 服务或连接真实数据库；本轮不改 API、SQL、表单保存或字段权限。真实运行页编辑联调仍未覆盖，无服务 PID 需停止。

## 2026-09-26 第十九个切口：列表列编译

- 从 `LowcodeRuntimeConfigBuilder` 提取列表列渲染、列宽/固定列、开关值、点击动作和子表标题前缀到 `RuntimeTableColumnCompiler`；入口由 1373 行降至 1184 行，新编译器 209 行。字段元数据判定仍复用原 `RuntimeFieldMetadataCompiler` 实例，公开的托管流程状态列方法保持入口签名。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=RuntimeTableColumnCompilerTest,RuntimeEditFieldCompilerTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest,RuntimeChildTableCompilerTest -Dsurefire.failIfNoSpecifiedTests=false test`：5 类共 33 项，0 失败、0 错误，退出码 0。
- 未启动 Admin 服务或连接真实数据库；列表查询和真实页面渲染未做端到端验收，无服务 PID 需停止。

## 2026-09-26 第二十个切口：运行字段目录

- 从 `LowcodeRuntimeConfigBuilder` 提取主子表字段引用、显式网格选列、旧页面布局回退、字段目录及托管流程状态列补齐到 `RuntimeFieldCatalogResolver`；入口由 1184 行降至 955 行，新组件 275 行。保留公开 `buildManagedFlowStatusColumn` 方法、字段顺序和显式隐藏规则。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=RuntimeFieldCatalogResolverTest,RuntimeTableColumnCompilerTest,RuntimeEditFieldCompilerTest,RuntimeChildTableCompilerTest,LowcodeRuntimeConfigBuilderTest,GeneratedLowcodeRuntimeConfigBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 类共 37 项，0 失败、0 错误，退出码 0。`git diff --check` 通过。
- 未启动 Admin 服务或连接真实数据库；真实页面布局、发布快照与流程状态列回显的端到端验收仍待服务环境。无服务 PID 需停止。

## 2026-09-26 第二十一个切口：业务流程节点表单权限

- 从 `BusinessFlowService` 提取节点表单绑定、主/子表字段权限归一化到无状态 `BusinessFlowNodeFormNormalizer`，共用 JSON/布尔值读取迁入 `BusinessFlowJsonReader`。入口由 9004 行降至 8708 行；新类分别 235/118 行。流程配置保存、实际任务写入权限、事务和回调状态机仍在原调用路径。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：补齐原入口仍使用的表单/编辑模式归一方法导入后，退出码 0。
- 定向回归首次发现旧 `BusinessFlowServiceFormAssetMergeTest.taskRuntimeReadsSelectedApplicationPageForm` 模拟了 `detail`，而现有应用页资产读取路径调用 `loadInAppBuilder`；单独复现后仅修正该测试桩，不改生产资产读取路径。最终执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowNodeFormNormalizerTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowServiceLifecycleTest,BusinessFlowServiceBusinessKeyTest -Dsurefire.failIfNoSpecifiedTests=false test`：5 类共 31 项，0 失败、0 错误，退出码 0。生命周期测试中预期的写入失败 WARN 被断言覆盖。
- `git diff --check`：通过。未启动 Admin/Flow 服务，也未连接 MySQL/Redis/Flowable；节点权限真实保存及审批端到端验证尚未覆盖，无服务 PID 需停止。

## 2026-09-26 第二十二个切口：审批表单字段目录

- 从 `BusinessFlowService` 迁出控件类型优先级、弱类型推断、设计器字段与子表列目录、字段预览到 `BusinessFlowTaskFormControlTypes` 和 `BusinessFlowFormFieldCatalog`。入口由 8708 行降至 8420 行；新类分别 107/231 行。应用页资产选择、表单权限执行、流程事务及状态机未迁移。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowFormFieldCatalogTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceLifecycleTest,BusinessFlowNodeFormNormalizerTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：7 类共 41 项，0 失败、0 错误，退出码 0。生命周期用例预期的失败回滚 WARN 不构成测试失败。
- `git diff --check`：通过。未启动 Admin/Flow 服务，未连接真实 MySQL/Redis/Flowable；审批页面与复杂子表资产的端到端联调仍未覆盖，无服务 PID 需停止。

## 2026-09-26 第二十三个切口：代码应用表单资产元数据合并

- 从 `BusinessFlowService` 迁出代码应用 Provider 资产与绑定元数据合并、展示属性覆盖、非公开字段过滤、资产移除与字段预览到 `BusinessCodeAppFormAssetMerger`。入口由 8420 行降至 8082 行；新类 371 行。租户绑定读取、Provider 调用、审批字段权限及事务边界未移动。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessCodeAppFormAssetMergerTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowFormFieldCatalogTest,BusinessFlowNodeFormNormalizerTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceLifecycleTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：8 类共 45 项，0 失败、0 错误，退出码 0。生命周期测试中预期的写入失败 WARN 被断言覆盖。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；Provider 与真实 BPMN/审批页面的端到端联调仍未覆盖，无服务 PID 需停止。

## 2026-09-26 第二十四个切口：业务流程绑定配置编解码

- 从 `BusinessFlowService` 迁出绑定 DTO/JSON 互转、旧 Key 兼容、默认业务表绑定、发起模式及变量映射归一化到 `BusinessFlowBindingCodec`。入口由 8082 行降至 7860 行；新类 266 行。租户绑定查询、持久化事务、状态字段同步及 Flowable 调用未移动。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。首次定向测试中 `BusinessFlowPerformanceContractTest` 的源码字符串断言仍要求旧的未限定方法名；更新为检查入口调用 `BusinessFlowBindingCodec.ensureBusinessBinding` 后，执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowBindingCodecTest,BusinessCodeAppFormAssetMergerTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowFormFieldCatalogTest,BusinessFlowNodeFormNormalizerTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceLifecycleTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：9 类共 50 项，0 失败、0 错误，退出码 0。生命周期测试的预期失败回滚 WARN 不构成测试失败。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；流程绑定保存与 BPMN 表单权限的服务级联调仍未覆盖，无服务 PID 需停止。

## 2026-09-26 第二十五个切口：流程启动上下文组装

- 使用 Assembler + Strategy，从 `BusinessFlowService` 迁出流程变量映射、字段 camel/snake 别名、调用方变量合并、服务端保留变量防覆盖及标题模板组装到 `BusinessFlowStartContextAssembler`；主表包装兼容读取迁入 `BusinessFlowRecordValues`。入口由 7860 行降至 7683 行，新类分别 143/98 行。服务端业务上下文的最终写入、Flowable 调用、事务与状态机仍在原入口。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowStartContextAssemblerTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowServiceLifecycleTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：5 类共 34 项，0 失败、0 错误，退出码 0。生命周期测试中的预期写入失败 WARN 被断言覆盖。
- `git diff --check`：通过。未启动 Admin/Flow 服务，也未连接真实 MySQL/Redis/Flowable；真实流程启动、审批人变量与标题的端到端联调仍未覆盖，无服务 PID 需停止。

## 2026-09-26 第二十六个切口：待办任务访问策略

- 使用 Policy Object 从 `BusinessFlowService` 迁出待办任务存在性/状态、签收人和候选人、写权限，以及流程实例、业务 Key、节点和流程定义一致性校验到 `BusinessFlowTaskAccessPolicy`。入口由 7683 行降至 7492 行，新策略 215 行；任务详情仍由入口从 Flow 服务读取，当前登录人仍由入口解析。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowTaskAccessPolicyTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceLifecycleTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 类共 39 项，0 失败、0 错误，退出码 0。生命周期测试中的预期写入失败 WARN 被断言覆盖。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实任务认领、候选组和跨租户场景仍需服务级联调，无服务 PID 需停止。

## 2026-09-26 第二十七个切口：审批子表权限策略

- 使用 Policy Object 从 `BusinessFlowService` 迁出子表/字段权限匹配、动态 CRUD 保存白名单、主子表请求拆包、子表键别名匹配、返回行字段裁剪和诊断摘要到 `BusinessFlowTaskChildPolicy`。入口由 7492 行降至 7014 行，新策略 494 行；节点表单加载、业务记录查询和保存调用仍在原入口。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。首轮新增测试使用了缺少 `field` 的非现行权限项，导致两项断言失败；按节点权限协议补齐 `field` 后未改生产逻辑，最终执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowTaskChildPolicyTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowNodeFormNormalizerTest,BusinessFlowServiceLifecycleTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 类共 38 项，0 失败、0 错误，退出码 0。生命周期测试中的预期写入失败 WARN 被断言覆盖。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实主子表暂存、审批保存及候选节点权限仍需服务级联调，无服务 PID 需停止。

## 2026-09-26 第二十八个切口：业务表单资产组装

- 使用 Assembler 从 `BusinessFlowService` 迁出对象设计器、运行配置和字段注册表表单资产的收集、去重补全、运行态 schema 回退及字段目录标准化到 `BusinessFlowFormAssetAssembler`；通过窄回调复用原运行态布局与子表字段目录规则。入口由 7014 行降至 6456 行，新 Assembler 654 行，均未引入 Flowable、数据库查询或保存副作用。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。首轮回归发现旧测试仍反射已迁移的字段注册表资产方法，改为直接验证 Assembler 后重跑；最终执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowFormAssetAssemblerTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowFormFieldCatalogTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 类共 33 项，0 失败、0 错误，退出码 0。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实应用页、对象设计器与运行配置混合资产的端到端联调仍需服务环境，无服务 PID 需停止。

## 2026-09-26 第二十九个切口：应用页面表单资产解析

- 使用 Resolver + Cache-Aside 从 `BusinessFlowService` 迁出应用草稿页面/表单资产定位、稳定 formKey 编解码、旧 CRUD 页面单一默认资产兼容和应用 builder/表单资产两级短缓存到 `BusinessFlowApplicationPageFormResolver`。入口由 6456 行降至 6002 行，新 Resolver 530 行；可选的应用服务通过 Supplier 延迟获取，保留既有字段注入兼容性。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowApplicationPageFormResolverTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowFormAssetAssemblerTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：6 类共 32 项，0 失败、0 错误，退出码 0。性能契约同步验证 Resolver 内的缓存与直接定位路径，而非要求实现继续位于 Facade 源文件。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实应用草稿切换、缓存过期和多租户页面解析仍需服务级联调，无服务 PID 需停止。

## 2026-09-26 第三十个切口：任务表单成块拆分

- 使用 Policy / Assembler / Coordinator 将主表字段权限、只读投影、必填与保存白名单、设计器/发布态 schema 和布局、子表与字段注册表合并、代码表单 Provider 查询/保存及元数据过滤从 `BusinessFlowService` 迁出。入口由 6002 行降至 4236 行，单轮净减少 1766 行；新增 `BusinessFlowTaskFormPolicy`、`BusinessFlowTaskChildAssembler`、`BusinessFlowTaskFormSchemaAssembler`、`BusinessFlowCodeFormCoordinator` 分别 477/658/626/267 行，均低于 1000 行。公开 API、事务、任务访问校验、动态 CRUD 保存、Flowable 调用和状态机仍在原入口。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：多次按拆分边界增量执行，最终退出码 0。旧子表控件测试从反射 Facade 私有方法改为直接验证新 Assembler；性能契约改为同时检查 Facade 委托与 schema Assembler 的缓存/轻量资产路径。
- 执行 `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessFlowTaskFormPolicyTest,BusinessFlowServiceFormAssetMergeTest,BusinessFlowServiceChildFieldControlTest,BusinessFlowServiceBusinessKeyTest,BusinessFlowServicePrintIdentityTest,BusinessFlowTaskAccessPolicyTest,BusinessFlowTaskChildPolicyTest,BusinessFlowNodeFormNormalizerTest,BusinessFlowFormFieldCatalogTest,BusinessFlowFormAssetAssemblerTest,BusinessFlowApplicationPageFormResolverTest,BusinessCodeAppFormAssetMergerTest,BusinessFlowBindingCodecTest,BusinessFlowStartContextAssemblerTest,BusinessFlowStatusRepairServiceTest,BusinessFlowStatusFieldServiceTest,BusinessFlowServiceLifecycleTest,BusinessFlowPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：18 类共 90 项，0 失败、0 错误，退出码 0；生命周期测试中预期的写入失败 WARN 被断言覆盖。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接 MySQL/Redis/Flowable；真实审批页面、Provider 与主子表暂存端到端仍需服务环境验证，本轮无服务 PID 需停止。

## 2026-09-26 第三十一个切口：节点表单解析器

- 使用 Resolver + Layered Fallback 从 `BusinessFlowService` 迁出 Flowable 任务/实例表单读取、运行时 `businessFormRef` 合并、节点权限装配及应用页/对象表单资产选择到 `BusinessFlowTaskNodeFormResolver`；入口由 4236 行降至 3869 行，新 Resolver 431 行，运行身份快照 `TaskFormRuntimeContext` 17 行。资产目录、性能阶段和可选 FlowClient 均通过窄依赖注入，Facade 不保留测试专用转发方法。
- generator Reactor `mvn -q -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`：退出码 0。旧表单资产测试改为直接验证 Resolver；性能契约改为检查 Resolver 内的重复 RPC 跳过和轻量资产路径。新增 4 项 Resolver 行为测试，连同既有 18 类回归共 19 类 94 项，0 失败、0 错误。
- `git diff --check`：通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实审批任务与应用页面联合解析仍需服务级联调，无服务 PID 需停止。

## 2026-09-26 第三十二个切口：任务运行身份与业务上下文解析

- 使用 Resolver + Codec 从 `BusinessFlowService` 迁出发布/草稿运行配置、单据配置和业务对象规范化查找，task form/流程关联/应用页身份恢复，以及业务 Key 编解码。入口由 3869 行降至 3431 行；新增 `BusinessFlowRuntimeContextResolver` 466 行、`BusinessFlowIdentityCodec` 58 行，两个不可变快照分别 13/4 行，均低于 1000 行。任务保存、流程关联写入、事务和状态机仍由 Facade 持有。
- generator Reactor 编译首次发现列表/绑定路径仍调用已迁出的单据配置和任务对象方法；改为直接复用 Resolver 后重新编译退出码 0。旧业务 Key 测试取消对 Facade 私有方法的反射，性能契约改为验证 Resolver 内的配置复用与阶段记录。
- 执行 20 类业务流程增量回归共 97 项，0 失败、0 错误；生命周期测试中预期的写入失败 WARN 被 `assertThrows` 覆盖。`git diff --check` 通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；历史流程实例与真实应用页联合身份恢复仍需服务级联调，无服务 PID 需停止。

## 2026-09-26 第三十三个切口：业务待办列表展示装配

- 使用 Enricher + Batch Loader 从 `BusinessFlowService` 迁出流程关联批量查询、对象身份缓存、运行配置分组、低代码记录批量读取、代码 Provider 摘要和展示投影到 `BusinessFlowListDisplayEnricher`。入口由 3431 行降至 3086 行，新 Enricher 419 行；公开列表增强入口仅委托，批量与缓存策略不再散落于 Facade。
- 首次编译暴露自动提取边界夹带下一方法的未闭合注释，修正边界后 Reactor 编译退出码 0。新增列表测试验证两条待办只执行一次关联批量查询和一次动态记录批量查询，性能源码契约改为直接检查 Enricher。
- 执行 21 类业务流程增量回归共 98 项，0 失败、0 错误；生命周期测试中预期的写入失败 WARN 被 `assertThrows` 覆盖。`git diff --check` 通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实 Flowable 待办列表与代码 Provider 联调仍需服务环境，无服务 PID 需停止。

## 2026-09-26 第三十四个切口：流程任务事件状态协调

- 使用 Coordinator + State Transition 从 `BusinessFlowService` 迁出 Flowable 任务创建/完成事件、发起人修改节点识别与保存自愈、驳回证据读取、修改待办快照和运行态双状态切换到 `BusinessFlowTaskEventCoordinator`。入口由 3086 行降至 2834 行，新 Coordinator 385 行；终态回调和公开事务入口仍留 Facade。
- 首次编译发现迁移类缺少租户上下文导入，补齐后 Reactor 编译退出码 0。任务事件仍通过 `PROPAGATION_REQUIRES_NEW` 独立事务执行，失败只记录日志；可选 FlowClient/事务管理器继续用 Supplier 兼容原字段注入。
- 执行 21 类业务流程增量回归共 98 项，0 失败、0 错误；生命周期用例覆盖修改节点、重提、终态保护和预期失败回滚。`git diff --check` 通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实 Flowable 任务事件时序仍需服务级联调，无服务 PID 需停止。

## 2026-09-26 第三十五个切口：终态回调与状态写入策略

- 使用 Coordinator + Strategy 从 `BusinessFlowService` 迁出终态回调查找与幂等、结果归一、业务状态回写、回调动作、业务领域事件和审批结果事件；单据/低代码绑定/Adapter 状态策略统一进入 `BusinessFlowStatusTransitionService`。公开事务与 FlowCallback 注解仍保留在 Facade，状态写入失败继续阻止流程关联提前落终态。
- 入口由 2834 行降至 2488 行；新增 `BusinessFlowCallbackCoordinator` 403 行、`BusinessFlowStatusTransitionService` 96 行，均低于 1000 行。generator Reactor 编译退出码 0。
- 执行 `BusinessFlow*Test,BusinessCodeAppFormAssetMergerTest` 共 23 类 105 项，0 失败、0 错误；新增 4 项状态迁移策略测试，生命周期测试中预期的写入失败 WARN 被 `assertThrows` 覆盖。`git diff --check` 通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实回调投递与事务提交后事件消费仍需服务级联调，无服务 PID 需停止。

## 2026-09-26 第三十六个切口：流程启动编排与绑定选择

- 使用 Resolver + Coordinator 从 `BusinessFlowService` 迁出启用 FLOW/历史 APPROVAL 绑定候选选择，以及记录读取、模型 Key 选择、单据权限校验、启动锁、Flowable 调用、关联落库和运行态写入。公开事务与租户上下文入口留在 Facade；普通、能力、兼容、触发器和业务流程节点的策略显式传给 Coordinator。
- 入口由 2488 行降至 2165 行；新增 `BusinessFlowBindingResolver` 114 行、`BusinessFlowStartCoordinator` 367 行，均低于 1000 行。generator Reactor 编译退出码 0。
- 执行 `BusinessFlow*Test,BusinessCodeAppFormAssetMergerTest` 共 25 类 112 项，0 失败、0 错误；新增 7 项绑定选择与启动编排行为测试，性能契约改为直接检查 Coordinator。`git diff --check` 通过。未启动 Admin/Flow 服务、未连接真实 MySQL/Redis/Flowable；真实委托身份、分布式锁和远端成功后本地回滚恢复仍需服务级联调，无服务 PID 需停止。
