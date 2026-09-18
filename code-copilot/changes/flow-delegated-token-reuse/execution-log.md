# 流程委托会话复用修复 Execution Log

> 变更名：`flow-delegated-token-reuse`
> 执行时间：2026-09-18

## 1. 根因验证

- Sa-Token 1.38.0 `sa-token-core.jar` 字节码反编译确认：`SaTokenConfig` 构造器 `bipush 12` → `maxLoginCount` 默认 12；仓库各服务 yml 仅有 `is-concurrent: true`，无 `max-login-count` 覆盖。
- `FlowClient` 每次 HTTP 请求前调用 `tokenProvider.getToken()`（FlowClient.java L533）→ 每次调用新建委托会话。
- 委托设备名含随机 UUID → `isShare` 同设备复用永不命中 → 会话按调用次数堆积。
- 误导日志链路确认：`SystemSaTokenListener.doLogin` 无差别打"用户登录成功"；`removeOnlineUser` 中 `executeWithTokenTenant` 返回 true 仅代表租户解析成功而非更新行数>0。

## 2. 改动明细

| 文件 | 改动 |
|------|------|
| `forge-starter-core/.../constant/FlowDelegationConstants.java`（新增） | 共享常量 `FLOW_DELEGATION_DEVICE_PREFIX = "mcp-flow:"`，auth/log/plugin-system 三方引用 |
| `forge-starter-auth/.../config/SaTokenFlowTokenProvider.java` | 核心修复：按 `userId:tenantId:orgId:clientId` 缓存委托 Token，剩余 TTL>10s 复用；未命中按身份键串行签发（双检）；签发逻辑拆出 `issueDelegatedToken`；时钟注入 `LongSupplier`；设备前缀改用共享常量 |
| `forge-starter-auth/.../test/.../SaTokenFlowTokenProviderTest.java` | 新增 2 用例：`shouldReuseDelegatedTokenWithinRefreshWindowAndRotateNearExpiry`（时钟推进 0s→30s→55s，验证仅 2 次签发）、`shouldIssueSeparateDelegatedTokensForDifferentIdentityScopes` |
| `forge-plugin-system/.../listener/SystemSaTokenListener.java` | `doLogin` 对 `mcp-flow` 设备打"流程委托临时会话签发"，不与真实登录混淆 |
| `forge-starter-log/.../listener/LoginLogListener.java` | `doLogin` 对 `mcp-flow` 设备跳过数据库登录日志 |
| `forge-plugin-system/.../service/impl/SysOnlineUserServiceImpl.java` | `removeOnlineUser` 捕获更新行数：>0 才打"移除在线用户成功"，=0 降级 debug |

## 3. 验证记录

```bash
# 1. 上游模块安装（core 新常量必须先 install，下游才可编译）
mvn -o -pl forge-framework/forge-starter-parent/forge-starter-core,\
forge-framework/forge-starter-parent/forge-starter-auth,\
forge-framework/forge-starter-parent/forge-starter-log install -DskipTests
# BUILD SUCCESS

# 2. starter-auth 全量测试（含新增复用/隔离用例，共 7 个 provider 用例）
mvn -o -pl forge-framework/forge-starter-parent/forge-starter-auth test -Penable-tests
# Tests run: 38, Failures: 0, Errors: 0 — BUILD SUCCESS

# 3. starter-log 编译验证（该模块无测试目录）
mvn -o -pl forge-framework/forge-starter-parent/forge-starter-log test -Penable-tests
# BUILD SUCCESS

# 4. plugin-system 相关回归
mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-system test -Penable-tests \
  -Dtest='SysOnlineUserServiceSecurityTest,ClientCredentialSurfaceContractTest,SysResourceAssignableTreeContractTest'
# SysOnlineUserServiceSecurityTest: 6/6 通过
# SysResourceAssignableTreeContractTest: 2/2 通过
# ClientCredentialSurfaceContractTest: 3/4 — 1 个既有失败（见 4. 备注）
```

## 4. R3：复用上线后复发，浏览器路径回归透传（2026-09-18 第二轮）

### 现场证据（用户日志）

```
流程委托临时会话签发 - loginId: 1, device: mcp-flow:1:6:UUID
09:16:07.272 SystemSaTokenListener.doLogout - 用户登出 - loginId: 1
09:16:07.325 移除在线用户成功（token=9586b201，Chrome/OSX）
【登录日志】类型: LOGOUT ... 登出成功
```

同毫秒签发→挤下浏览器会话：池子已满，新签发触发 `logoutByMaxLoginCount` 注销最早会话（浏览器）。

### 诊断结论

1. 设备名 `mcp-flow:1:6:UUID` 中 clientId=1 是 R2 会话路径硬编码的 `INTERACTIVE_CLIENT_ID`——这是**浏览器在线用户**触发 admin→flow 调用签发的委托会话，不是 MCP；
2. 生产代码全仓 Grep：`ExecutionIdentityContextHolder.open` 仅 4 处（McpExecutionContextLifecycle / OpenGatewayContextBridge / HighRiskApprovalCallbackService / TenantBusinessDataSourceTaskDecorator），均为程序化调用；浏览器路径签发委托属 R2 设计错误；
3. flow 侧 `requireTrustedDelegation` 仅保护 `/start-delegated*` 两个 MCP 入口，`/start` 接受任何有效会话——浏览器 token 透传功能兼容；
4. 堆积源叠加：`is-share: false` + `timeout: 1800s`，开发期频繁重登旧会话 30 分钟内仍占位，四服务共享同一账号会话池。

### 改动

| 文件 | 改动 |
|------|------|
| `SaTokenFlowTokenProvider.java` | `getToken()` 浏览器路径回归透传 `stpLogic.getTokenValue()`（零签发）；删除 `toSessionExecutionIdentity`/`INTERACTIVE_CLIENT_ID`/`loginUserSupplier`；保留 MCP 路径 F1 复用机制；补回测试在用的单参构造器 |
| `SaTokenFlowTokenProviderTest.java` | `shouldKeepExistingSaTokenBehaviorWhenLoginUserUnavailable`/`shouldIssueDelegatedTokenForLoggedInUserWithoutExecutionIdentity` 改写为 `shouldPassThroughBrowserTokenWithoutIssuingDelegation`（透传 + `never createLoginSession`）与 `shouldReturnNullWhenNoIdentityAndNotLoggedIn` |
| admin/flow/app/report 四服务 `application.yml` | `sa-token.max-login-count: ${FORGE_AUTH_MAX_LOGIN_COUNT:100}` 防御纵深 |

### 验证

```bash
mvn -o -pl forge-framework/forge-starter-parent/forge-starter-auth test -Penable-tests
# Tests run: 38, Failures: 0, Errors: 0 — BUILD SUCCESS
mvn -o -pl forge-framework/forge-starter-parent/forge-starter-auth install -DskipTests
# starter-auth 已 install
mvn -o -pl forge-flow/forge-flow-server test -Penable-tests -Dtest=FlowDelegatedIdentityControllerTest
# Tests run: 8, Failures: 0 — BUILD SUCCESS（委托准入链路回归）
```

部署提示：admin/flow/app/report 四服务均需重启生效（代码 + yml 配置）。

## 5. 备注

- `ClientCredentialSurfaceContractTest.operationLogShouldHardExcludeCredentialEndpoints` 失败为**既有问题**：该用例读取 `forge-admin-server/sql/初始化脚本.sql`，此文件在 HEAD 中也不存在（早已从仓库移除），与本轮改动无关。
- 部署顺序：`forge-starter-core` → `forge-starter-auth` → `forge-starter-log` → `forge-plugin-system` 已全部 install 到本地仓库；admin 服务需重新打包部署后生效（`mvn install -DskipTests` 全量 + 重启 admin）。
- 端到端验证建议：部署后浏览器登录 → 连续访问流程页面（触发 10+ 次流程调用）→ 观察浏览器不被登出；R3 后常规页面操作**不应再出现**"流程委托临时会话签发"日志，仅 MCP/网关/高危回调/异步任务会触发且每身份约 60s 一条。
- 若需排查会话池现状：`redis-cli` 后 `KEYS satoken:login:token:*` 计数，或反序列化 `satoken:login:session:{loginId}` 查看设备名分布。
