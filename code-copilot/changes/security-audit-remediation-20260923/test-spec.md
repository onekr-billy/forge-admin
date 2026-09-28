# 安全审计问题整改增量测试计划

## 1. 当前基线

- 本文件对应 `security-audit-remediation-20260923`，目前只生成 Spec/Tasks，尚未修改业务代码。
- 已有证据：后端 JDK 17 编译通过；前端生产构建通过；`forge-plugin-system` 定向测试 115 个中 114 个通过，1 个因过期 SQL 路径失败。
- 默认 Java 8 运行 Java 17 测试会失败；后续统一使用 JDK 17。
- `pnpm audit --json` 曾因工具异常 `reference.startsWith is not a function` 未完成，不能作为安全结论。

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
- 不同 client 并发登录不互相修改 Token TTL、并发和共享策略。

### 数据连接和 SQL 预览

- 环回、私网、云元数据、未允许协议/驱动、非 `SELECT 1` 测试 SQL、超时和超大响应均拒绝。
- `preview-sql` 无数据集 ACL、跨租户、未发布或无查询权限均拒绝；可用数据集仍执行行/列权限和脱敏。

### 文件

- uploadId 跨用户、跨租户、过期、超分片、超大小和错误存储类型均拒绝。
- 分片完成保留私有属性；私有文件的 download/url/Base64/bytes/导出均要求授权。
- 批量删除按字符串 fileId 生效，不能删除其他租户或其他上传者文件。

## 4. P2 正确性验证

- 数据集 page 1/page 2 返回不同记录，`total` 与 count 一致，缓存不串页。
- SQL parser 拒绝注释绕过、大小写绕过、换行绕过、多语句、锁、延时函数、文件函数、系统表和危险 UNION。
- 注册确认密码、格式、租户、禁用用户找回密码和统一密码策略测试通过。
- `mvn dependency:tree` 不再将 `fastjson:1.2.83` 带入运行时，或有经批准的兼容例外。

## 5. 低代码/流程专项验证

### 巨型类拆分与行为保持

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
