# 流程委托会话复用与浏览器会话挤下线修复 Spec

> 变更名：`flow-delegated-token-reuse`
> 状态：`review`
> 创建日期：2026-09-18
> 严重级别：P0（用户会话被自动注销）

## 1. 需求

修复「浏览器 Token 被自动注销（未点击退出）」的严重问题：流程服务调用不断创建临时登录会话，堆积后触发 Sa-Token 会话数量上限，把最早的浏览器会话挤下线。同时修正误导性日志（临时会话被记为"用户登录成功/主动登出"，`Updates: 0` 仍打印"移除在线用户成功"）。

## 2. 根因

`SaTokenFlowTokenProvider.createDelegatedFlowToken`（原 L134）每次 `FlowClient` HTTP 调用前：

1. 生成随机设备名 `mcp-flow:…:UUID` → Sa-Token `isShare` 同设备复用永远不命中；
2. `createLoginSession(userId, loginModel)` 与浏览器会话共用同一 `StpLogic` 和**同一账号会话池**（`satoken:login:session:{userId}`）；
3. Sa-Token 1.38.0 默认 `max-login-count=12`（字节码验证 `bipush 12`，仓库无覆盖配置，仅 `is-concurrent: true`）；
4. `createLoginSession` 尾部触发 `logoutByMaxLoginCount` → 注销最早的会话 = 浏览器会话。

放大器：委托 Token TTL 60s，一次页面加载的并发流程请求（每个 FlowClient 调用都取一次 token）即可在 TTL 内堆满 12 枚 → 浏览器会话被挤掉。

**关于"admin 和 flow 共享 Redis"**：共享 Redis 是委托机制的前提（flow 服务必须能校验 admin 签发的临时 Token），不是 bug 本身。问题在于委托会话与浏览器会话写入**同一个按 userId 计数的账号会话池**，且每调用新建会话，数量上限按池计数。

### R3 复盘：复用上线后挤下线仍复发（用户日志实证）

用户日志：`流程委托临时会话签发 - device: mcp-flow:1:6:UUID` 后同毫秒 `doLogout - loginId: 1`，被注销的 token 是浏览器会话（Chrome UA、"移除在线用户成功"）——池子已满，新签发的第 N 枚把最早的浏览器会话挤掉。两个教训：

1. **设计错误（本轮修正）**：R2 把「浏览器在线用户调流程」也改成了签发委托会话（会话路径构建 sessionIdentity + `INTERACTIVE_CLIENT_ID`）。常规页面操作高频发生，即使有复用，每 50s 仍新增 1 枚；委托签发的本职场景应仅限 MCP/开放网关/高危回调等**无浏览器会话**的程序化调用（生产代码里 `ExecutionIdentityContextHolder.open` 仅 4 处，全部属于此类）。
2. **堆积源叠加**：`is-share: false` + `timeout: 1800s`——开发期每次重新登录产生新会话，旧会话 30 分钟内仍占位；四个服务共享同一账号会话池；叠加委托会话后 30 分钟窗口堆满 12 枚完全现实。

## 3. 方案

### F1 委托 Token 进程内复用（仅限 MCP 路径，forge-starter-auth）

`SaTokenFlowTokenProvider`：

- 按 `userId:tenantId:orgId:clientId` 缓存委托 Token，剩余 TTL > 10s（刷新余量）时直接复用，不再 `createLoginSession`；
- 未命中/临近过期时按身份键 `synchronized` 串行签发（双检），防止并发突发调用堆积会话；
- 换发时新旧 Token 短暂重叠（≤10s），每进程每身份同时存活 ≤2 枚委托会话，远离 12 上限；
- 时钟改为注入 `LongSupplier`（默认 `System::currentTimeMillis`）以便测试。

效果：浏览器会话（通常 1-2 枚）+ 委托会话（每进程每身份 ≤2 枚）≈ 3-4 枚 < 12，`logoutByMaxLoginCount` 不再被触发。

### F2 日志分类（3 处）

- `SystemSaTokenListener.doLogin`：`mcp-flow` 设备 → 打"流程委托临时会话签发"，不再打"用户登录成功"；
- `LoginLogListener.doLogin`：`mcp-flow` 设备 → 跳过数据库登录日志（避免刷爆登录日志表、干扰真实登录审计）；
- `SysOnlineUserServiceImpl.removeOnlineUser`：更新行数 >0 才打"移除在线用户成功"；=0 时降为 debug"未命中在线记录（可能为流程委托临时会话）"。

新增共享常量 `forge-starter-core` → `FlowDelegationConstants.FLOW_DELEGATION_DEVICE_PREFIX`（auth/log/plugin-system 三方共同引用，均依赖 core）。

### F3 浏览器会话回归透传（R3 核心修正，SaTokenFlowTokenProvider）

`getToken()` 简化为两条路径：

- 有 `ExecutionIdentity`（MCP / 开放网关 / 高危回调 / 异步身份装饰器）→ 签发委托 Token（F1 复用机制保留）；
- 无显式身份但浏览器在线 → **直接透传 `stpLogic.getTokenValue()`**，零新增会话。删除会话路径的身份构建（`toSessionExecutionIdentity`/`INTERACTIVE_CLIENT_ID`/`loginUserSupplier`）。

功能兼容性验证：flow 侧 `requireTrustedDelegation` 只保护 `/start-delegated*` 两个 MCP 专用入口（生产调用方为 `startProcessForDelegatedUser`/`startHighRiskApprovalForDelegatedUser`，全部走 MCP 路径签发委托 Token，带 marker）；浏览器页面发起流程走 `/start/{modelKey}`，接受任何有效登录会话，透传 token 被标准校验识别。

### F4 max-login-count 显式防御纵深（4 个 application.yml）

admin/flow/app/report 四服务 `sa-token` 段显式配置 `max-login-count: ${FORGE_AUTH_MAX_LOGIN_COUNT:100}`（默认 12）。R2 曾否决"仅调大上限"作为**唯一**修复；R3 后浏览器路径零签发、MCP 路径每进程每身份 ≤2 枚，调大上限不再是掩盖问题的治标，而是防御 `is-share: false` 频繁重登叠加多端登录挤掉浏览器会话的纵深保障。

### 被否决的替代方案

- **仅调大 `max-login-count` / 关闭上限（作为唯一修复）**：治标不治本，会话堆积依旧消耗 Redis，且放宽真实登录并发语义；R3 后作为纵深配置保留（见 F4）；
- **独立 StpLogic（第二 loginType）完全隔离**：彻底但需 flow 服务侧同步改造校验链路（拦截器、SessionHelper、租户上下文），侵入面大，留作后续架构选项；
- **依赖 Sa-Token `isShare` 同设备复用**：设备名确定性改造受 `is-concurrent`/`isShare` 配置耦合，且共享 Token 的续期语义不可控，不如显式缓存；
- **为浏览器路径签发委托会话（R2 实际做法）**：已被 R3 用户日志证伪——常规页面操作高频发生，复用只能降频不能归零，与频繁重登叠加后仍会堆满会话池。

## 4. 影响范围

| 范围 | 说明 |
|------|------|
| 改动文件 | `FlowDelegationConstants.java`（新增）、`SaTokenFlowTokenProvider.java`、`SaTokenFlowTokenProviderTest.java`、`SystemSaTokenListener.java`、`LoginLogListener.java`、`SysOnlineUserServiceImpl.java`、admin/flow/app/report 四服务 `application.yml` |
| 行为变化 | MCP 路径：同一身份 60s 窗口内流程调用复用同一枚委托 Token；浏览器路径：直接透传现有会话，零新增；委托会话不再计入登录日志/在线用户日志；账号会话上限显式提升到 100（可用 FORGE_AUTH_MAX_LOGIN_COUNT 覆盖） |
| 不变 | 委托 Token 协议（仍是普通 Sa-Token，flow 侧校验零改动）、TTL 60s、`FlowDelegationSessionVerifier` 校验链路、真实登录/登出日志、`/start-delegated*` 仅限 MCP 的准入语义 |
| 无 DB 变更 | 无表结构、无 Flyway 脚本 |

### 已知边界

- 多实例部署：每个 admin/app 实例独立缓存，每身份每实例 ≤2 枚委托会话，仅限 MCP 路径，浏览器路径不参与签发。
- 委托会话被外部注销（如管理员清会话）时，缓存最长 60s 内仍可能返回失效 Token，与既有 TTL 语义一致，可接受。
- 权限快照复用期间（≤50s）不反映最新权限变更，与单次签发的 60s TTL 语义一致。

## 5. 验收

- [x] 同一身份连续流程调用仅签发 1 枚委托 Token（单测验证 `createLoginSession` 调用次数）
- [x] 临近过期（剩余 <10s）自动换发新 Token，不把临期 Token 交给调用方
- [x] 不同身份（用户/组织/客户端）各自独立 Token，互不串用
- [x] 委托会话签发日志为"流程委托临时会话签发"，不写数据库登录日志
- [x] `removeOnlineUser` 仅在真正更新到行时打"移除在线用户成功"
- [x] 浏览器在线会话直接透传现有 token，不为常规页面操作签发任何委托会话（单测断言 `never createLoginSession`）
- [x] 无登录态且无显式身份时返回 null，不签发
- [x] 四服务 `sa-token.max-login-count` 显式配置（环境变量可覆盖）
- [x] flow 侧委托准入链路回归（`FlowDelegatedIdentityControllerTest` 8/8）
- [x] starter-auth 38/38 回归通过
