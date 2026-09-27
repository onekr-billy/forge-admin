# 安全审计问题整改增量测试计划

## 1. 当前基线

- 本文件对应 `security-audit-remediation-20260923`。截至 2026-09-27，P0/P1 中能够通过源码和模块测试闭环的缺陷已进入实现与验证阶段。
- 按用户要求，T4.2 巨型前端组件拆分和 T4.3 后端巨型类拆分不属于本轮修复范围；相关任务保留但不计入本轮完成度。
- 后端统一使用 JDK 17；Admin 依赖反应堆 46 个模块编译通过，Flow Server 依赖反应堆 38 个模块编译通过。
- `forge-admin-ui` 与 `forge-report-ui` 生产构建通过。Admin 的 `pnpm --ignore-workspace build` 因 pnpm 非 TTY 依赖目录确认而中止，随后直接执行 package.json 中同一 Vite 构建命令通过；没有删除或重装现有依赖。
- 本机 Mockito inline/Byte Buddy 在部分测试中无法 self-attach；这类结果按环境阻断记录，不视为代码通过，也不覆盖此前同测试已通过的证据。
- `pnpm audit --json` 曾因工具异常 `reference.startsWith is not a function` 未完成，不能作为安全结论。
- `ClientCredentialSurfaceContractTest` 的历史失效 SQL 路径已改为仓库实际初始化脚本，显式 JDK 17 下 4/4 通过；仓库级 Maven Toolchain/CI 固定仍未完成。

## 1.1 2026-09-27 本轮实际证据

- 静态扫描通过：`forge-report-ui/src` 无 `new Function`、`AsyncFunction`、`eval(`、动态 `constructor` 和 `v-html`；Java 源码无 `engine.eval`/`ScriptEngineManager`；无 BPMN 原文 preview/substring 日志。
- JDK 17 Admin 全依赖编译通过：`mvn -DskipTests -pl forge-admin-server -am compile`，46/46 模块成功。
- JDK 17 Flow Server 全依赖编译通过；BPMN/流程监控 13 个非 Mockito 定向测试通过。
- 密码策略与客户端登录配置 8 个定向测试通过；密码找回 9 个 Mockito 测试本轮因 Byte Buddy attach 失败而环境阻断，未误记为通过。
- 数据 SQL/预览、外部接口权限、API fail-closed、幂等、验证码、文件访问、事件租户/条件和社会化登录均已执行对应模块定向测试，详见 `execution-log.md`。
- `git diff --check` 通过；用户已有 `.DS_Store` 修改未触碰、未纳入本变更。

## 1.2 2026-09-27 A-20 租约/fencing 增量验证计划

- 范围：`ai_business_process_run` 租约字段、run claim/heartbeat/fencing CAS、节点 attempt 的租约约束、事务提交后执行和稳定副作用幂等键。
- 先执行 `git diff --check`、Flyway 版本/占位符/防重复静态检查和 Mapper XML 契约测试。
- 定向执行 `BusinessProcessOrchestratorTest` 与 `BusinessProcessRunLeaseSqlContractTest`，覆盖 run claim 失败、续租失败、节点 claim 失败和明确 attemptId 完成。
- 使用 JDK 17 编译 `forge-plugin-generator` 及其依赖；共享流程运行实体、Mapper 或装配失败即阻断本阶段提交。
- 本地未连接真实 MySQL/Flowable，不把 Flyway 实库执行、多节点故障切换和远程调用补偿记录为通过；Outbox 仍单独保留为后续任务。

### 实际结果

- `BusinessProcessOrchestratorTest` 16、`BusinessProcessRunLeaseCoordinatorTest` 2、`BusinessProcessRunLeaseSqlContractTest` 1：共 19 个通过；覆盖认领/续租失败关闭、明确 attemptId、提交后独立线程派发、心跳租户上下文恢复和 SQL fencing 条件。
- 默认 Mockito inline 在一次复跑中因 Byte Buddy 无法 self-attach 环境阻断；使用本地同版本 `byte-buddy-agent` 显式加载后，19 个用例全部通过。另以无 Mockito 方式独立执行租约协调器与 SQL 契约测试，3/3 通过。
- Generator 及 33 个依赖反应堆模块在上述测试命令中编译成功；Admin 全依赖 JDK 17 编译 46/46 成功。真实 MySQL 迁移、多 JVM 租约接管和 Flowable 远程补偿仍未执行，不据此宣称通过。

## 1.3 2026-09-28 开放网关启动门禁与分页缓存增量验证

- 风险范围：identity/open-gateway 开启时的密钥强度、默认 client/grant、SERVICE/HYBRID 身份租户/用户/组织绑定，以及数据集分页缓存串页。
- 配置门禁：空 pepper、长度不足、低字符多样性、常见占位值和三组相同 pepper 均必须阻止启动；默认关闭时不创建身份服务和数据审计 runner。
- 数据门禁：全租户活动 client/grant 中存在保留 client code、无效 auth mode、签名密钥缺失、服务身份绑定失效、能力/有效版本失效或动作字段策略缺失时必须阻止启动。
- 安全链路回归：OAuth/HMAC 验证、防重放、scope/RBAC、限流、幂等、业务/流程动作适配、高风险提交/回调及版本化 KEK 测试必须通过。
- 缓存隔离：相同 datasetId、查询参数和用户上下文下，pageNum 或 pageSize 任一变化都必须生成不同缓存项。
- 实际结果：启动门禁 10/10、platform 安全链路 45/45、replay guard 10/10、actions 12/12、高风险 15/15、数据缓存/执行器 6/6 通过；Admin 聚合编译 46/46 成功。
- 环境限制：没有真实 MySQL/Redis/开放网关，未执行生产 client/grant 实库扫描、网关压测和生产启用审批；这些结果不得替代上线人工门禁。

## 1.4 2026-09-28 外部响应字段映射增量验证

- 协议范围：仅允许版本化 `FIELD_MAP_V1` JSON 中的 `sourcePath`、`fieldMapping`、`targetPath`；路径只允许安全字段名和源数组数字下标，不提供函数、递归、通配符、网络或任意代码能力。
- 边界验证：配置最大 16 KiB、路径最大 256 字符/16 层、映射字段最大 128、集合最大 10,000 条、映射操作最大 250,000 次、输出最大 2 MiB；超限统一 fail-closed。
- 迁移验证：旧 JavaScript/未知配置在保存前和运行时均明确拒绝，不回退到主 JVM 执行；管理页只展示字段映射协议。
- 审计验证：新增/修改禁止 `OperationLog` 保存请求与响应，只记录操作、租户、操作者、API 标识、协议版本、SHA-256 摘要和结果。
- 实际结果：`JsonPathAdapterTest`、`ExternalApiControllerTransformValidationTest`、`ExternalControllerPermissionContractTest`、`ExternalProxyServiceImplTest` 共 10/10 通过；Admin 聚合编译 46/46 成功。
- 前端结果：`manage.vue` 新增部分 ESLint 通过（保留存量 template ref 规则例外），Vite 生产构建成功；只有仓库已有 CSS 注释和动态导入告警。

## 1.5 2026-09-28 数据连接与 SQL 预览审计脱敏验证

- `DataDatasetRuntimeController.query`、已保存数据集预览、临时 SQL 预览、数据连接新增/修改/测试必须显式配置 `OperationLog` 不保存请求参数和响应结果。
- 运行查询日志只记录 datasetId、类型、分页、字段数、参数键和 SQL SHA-256 摘要；预览日志只记录操作者/租户、数据集/连接标识、参数数量、行数、SQL 摘要、结果和异常类型。
- 连接与预览失败不得记录 JDBC URL、用户名、密码、完整 SQL 或 JDBC 异常消息；返回客户端的错误也使用固定诊断文案。
- 实际结果：`DataConnectionControllerSecurityTest`、`DataDatasetControllerSecurityTest`、`DataQueryExecutorTest` 共 10/10 通过，包含携带伪造密码和 SQL 的 JDBC 异常不返回原文用例；Admin 聚合编译 46/46 成功。

## 1.6 2026-09-28 幂等 Token Redis 故障边界验证

- 确定性返回：Lua 返回 `1` 时只允许一个请求进入业务，返回 `0` 表示 Token 不存在、过期或已消费，重复请求不进入委托处理器。
- 不确定性返回：Redis 命令超时或主节点切换时，Lua 可能已消费但客户端未收到结果；应用层不得自动重试这个不可幂等的判定调用。
- 故障时必须统一 fail-closed，不执行业务委托，不记录完整 Token，并返回“重新获取 Token 后重试”的固定错误。
- 实际结果：`RedisTokenServiceTest` 12、`TokenRequiredStrategyHandlerTest` 3，共 15/15 通过；显式验证 timeout/failover 各只执行一次 Redis Lua，第二个预置“成功”结果不会被读取。

## 2. P0 必跑验证

### 动态脚本与 HTML

- 主线程代码扫描：`rg -n "new Function|AsyncFunction|\bFunction\(" forge-report-ui/src`，目标是仅允许隔离执行器或明确测试夹具。
- 恶意脚本用例：读取 `window`、`document.cookie`、`localStorage`、`fetch`、原型链、构造器和无限循环均被拒绝或超时终止。
- HTML 用例：事件属性、`javascript:` URL、SVG 外链和未允许标签均被清洗；纯文本保留原文显示。

### 外部脚本适配器

- `engine.eval` 主 JVM 静态扫描和单元测试必须无可执行路径。
- 允许 DSL 输入通过；未知函数、超长输入、超时、异常和结果超限必须失败关闭。

## 3. P1 必跑验证

### 权限与网关

- 未配置 API 资源、缓存异常、隐藏 API、普通用户访问外部配置和代理接口均返回 401/403，不得返回业务数据。
- 资源迁移重复执行不产生重复行；Controller 路由覆盖报告无遗漏。
- open-gateway/flow-actions/identity 无环境变量时均为关闭；开启时缺少 pepper、默认 client/grant 或组织绑定失败启动/调用。

### 认证、幂等与验证码

- 密码修改、找回和管理员重置后旧 Token 请求失败。
- 两个并发请求使用同一幂等 Token，业务方法只执行一次。
- 两个并发验证码校验最多一次成功；发送间隔和日配额不能被并发绕过。
- 同一短信/邮箱目标在失败窗口达到阈值后必须短时锁定，锁定期间不得继续校验或调用发送器；Redis 查询、计数或发送占位异常时统一 fail-closed。
- 不同 client 并发登录不互相修改 Token TTL、并发和共享策略。

### 数据连接和 SQL 预览

- 环回、私网、云元数据、未允许协议/驱动、非 `SELECT 1` 测试 SQL、超时和超大响应均拒绝。
- `preview-sql` 无数据集 ACL、跨租户、未发布或无查询权限均拒绝；可用数据集仍执行行/列权限和脱敏。

### 文件

- uploadId 跨用户、跨租户、过期、超分片、超大小和错误存储类型均拒绝。
- 对外 uploadId 不包含对象存储 bucket/key/provider uploadId；Redis 会话和逐片状态允许另一节点续传，本地存储在共享挂载目录下可恢复磁盘会话。
- 完成请求必须与服务端记录的连续分片、ETag、逐片大小、总大小、MIME 和扩展名一致；过期或合并异常会终止存储端上传并清理会话。
- 分片完成保留私有属性；私有文件的 download/url/Base64/bytes/导出均要求授权。
- 批量删除按字符串 fileId 生效，不能删除其他租户或其他上传者文件。
- `sys_file_metadata.status` 固定为 `1=正常/0=已删除`，实体 `@TableLogic`、自定义 Mapper SQL 和 Flyway 迁移保持一致。

## 4. P2 正确性验证

- 数据集 page 1/page 2 返回不同记录，`total` 与 count 一致，缓存不串页。
- SQL parser 拒绝注释绕过、大小写绕过、换行绕过、多语句、锁、延时函数、文件函数、系统表和危险 UNION。
- 注册确认密码、格式、租户、禁用用户找回密码和统一密码策略测试通过。
- `mvn dependency:tree` 不再将 `fastjson:1.2.83` 带入运行时，或有经批准的兼容例外。

## 5. 低代码/流程专项验证

### 巨型类拆分与行为保持（本轮排除）

以下用例属于后续架构改造验收基线，本轮按用户要求不执行、不计入安全缺陷修复完成度。

- 生成目标类的行数、方法数、依赖数、圈复杂度、事务方法和远程调用基线；拆分后单类不超过 800 行，且不存在仅转发调用的规避类。
- 对 `BusinessFlowService`、`DynamicCrudService`、`BusinessObjectDesignerService`、`LowcodeRuntimeConfigBuilder`、`FlowTaskServiceImpl` 和监听器执行 API、权限、租户、逻辑删除、状态、幂等和审计行为快照对比。
- 子表跨租户、跨主记录、逻辑删除记录和并发更新/删除均被拒绝；动态字段、表名和列名不接受运行时 Schema 白名单之外的输入。

### 事件租户与流程并发

- 缺失 `tenantId` 的事件被拒绝或进入隔离失败队列，不能静默落到租户 1；事件 DTO 伪造其他租户、来源或签名失败。
- 未知事件条件操作符、未知字段类型和格式错误表达式均 fail-closed；重复事件只产生一个 run。
- 同一 run 并发执行时只有一个 worker 获得租约；同一节点只有一个 attempt claim 成功，complete 必须按该 attemptId 原子更新。
- 过期租约只能被一个恢复 worker 接管；重复审批回调、乱序回调、跨租户回调和远程超时不能错误改变终态。
- Flowable 远程成功后本地写回失败可通过 Outbox/补偿恢复；本地事务回滚但远程已成功不会再次无条件启动流程。

### 流程监控和变量安全

- `FlowMonitorServiceImpl` 的旧统计、实例、活动节点和变量接口均无法跨租户读取；不存在未绑定 tenant context 的公共服务路径。
- 流程变量按白名单和敏感等级过滤/脱敏；读取行为记录操作者、租户、实例和字段摘要，不记录变量原文。

### BPMN 输入安全

- XML 外部实体、DOCTYPE、外部 schema 和危险解析配置被拒绝；单引号属性、命名空间前缀、属性顺序、CDATA 和换行不影响合法文档校验。
- 非法 sequenceFlow 引用、不支持的 scriptTask/callActivity/subProcess/serviceTask 执行委托和恶意表达式不能绕过白名单。
- process id 替换只修改目标 XML 节点，不误改文本、注释、CDATA 或其他 process；多 process 文档按明确策略拒绝或只允许指定主 process。
- BPMN 日志只记录模型 id、版本、hash、长度和错误定位，不输出原始 XML。

### 低代码发布一致性

- DDL 成功而配置失败、菜单成功而入口失败、重复 post processor、重试耗尽和死信场景均可对账并人工重放。
- 发布任务包含 requestId/schemaHash/tenantId/versionId/dataSource/operator，重复执行不产生重复菜单、入口或版本。
- 发布状态在预检、DDL、配置、后置同步和补偿之间单调可追踪，不把本地 `@Transactional` 视为 DDL 回滚保证。

## 6. 命令基线

```bash
cd forge-server
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Penable-tests -Dforge.compiler.skip=false -Dforge.tests.skip=false test
```

```bash
cd forge-admin-ui
pnpm --ignore-workspace lint
pnpm --ignore-workspace build
```

```bash
git diff --check
rg -n "new Function|AsyncFunction|engine\.eval|StrictHostKeyChecking=no|fastjson:1\.2\.83" forge-report-ui forge-server
```

仅文档阶段应执行 `git diff --check` 和关键路径静态检查；代码实现阶段按本文件增量追加实际命令、输出、接口返回和数据库结果。

## 7. 跳过项和环境限制

- 未配置本地 MySQL/Redis 时，不将真实接口、Flyway、并发 Lua 和 JDBC 网络边界写成通过；改用 Testcontainers 或明确记录跳过原因。
- 未启动服务时，不进行真实浏览器、网关、租户隔离和文件存储结论。
- 不停止工作区其他任务启动的进程，不删除现有数据库、缓存和测试数据。

## 8. 2026-09-27 验证码风控增量验证

- 风险范围：共享 Redis 计数原语、短信/邮箱验证码校验与发送、Admin/App 配置绑定。
- 定向行为：执行 `CaptchaServiceImplTest` 全量 25 个用例，覆盖失败阈值、短时锁、成功清理和 Redis fail-closed。
- 缓存契约：执行 `RedissonCacheServiceImplTest`，确认 `INCRBY + 首建 PEXPIRE` 使用单次 Lua，非正 TTL 拒绝。
- 聚合兼容：执行 `mvn -pl forge-admin-server -am -DskipTests compile`，验证共享 `ICacheService` 接口变化的 46 模块编译。
- 环境限制：无真实 Redis 集群，本轮不声明节点切换、网络分区或 Lua 实库并发压测通过。

## 9. 2026-09-27 密码凭证版本增量验证

- 风险范围：用户改密、找回密码、管理员重置、Sa-Token 受保护请求和多实例旧会话吊销。
- 数据契约：两条密码更新 SQL 必须在同一语句中执行 `password_version + 1`；Flyway 为存量用户补齐非空默认版本 0。
- 请求期校验：Token Session 版本与数据库权威版本一致才允许继续；用户禁用、删除、租户成员关系撤销、版本不一致或数据库读取异常均 fail-closed。
- 滚动兼容：迁移前创建且不含版本字段的会话按版本 0 处理；首次改密后数据库版本递增，所有旧会话立即失效。
- 产品配置：`FORGE_AUTH_KEEP_CURRENT_SESSION_AFTER_PASSWORD_CHANGE` 默认 false；显式开启时只刷新发起改密的当前会话版本，其余会话仍失效。找回密码和管理员重置不受该开关影响，始终吊销全部会话。
- 环境限制：本轮不连接真实 MySQL/Redis，不声明 Flyway 实库迁移、双 JVM 或网络分区演练通过。
