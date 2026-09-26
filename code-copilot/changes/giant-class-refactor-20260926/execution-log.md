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
