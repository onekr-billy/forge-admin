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
