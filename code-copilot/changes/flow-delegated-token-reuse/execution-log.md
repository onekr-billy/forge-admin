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

## 4. 备注

- `ClientCredentialSurfaceContractTest.operationLogShouldHardExcludeCredentialEndpoints` 失败为**既有问题**：该用例读取 `forge-admin-server/sql/初始化脚本.sql`，此文件在 HEAD 中也不存在（早已从仓库移除），与本轮改动无关。
- 部署顺序：`forge-starter-core` → `forge-starter-auth` → `forge-starter-log` → `forge-plugin-system` 已全部 install 到本地仓库；admin 服务需重新打包部署后生效（`mvn install -DskipTests` 全量 + 重启 admin）。
- 端到端验证建议：部署后浏览器登录 → 连续访问流程页面（触发 10+ 次流程调用）→ 观察浏览器不被登出，日志中"流程委托临时会话签发"每身份约 60s 一条而非每请求一条。
