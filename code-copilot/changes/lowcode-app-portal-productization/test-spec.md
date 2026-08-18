# 低代码应用门户产品化测试规格

> 变更：`lowcode-app-portal-productization`
> 基线：本文件按 `code-copilot/rules/automated-testing-standard.md` 建立，后续仅追加本轮增量验证。

## 验证范围

| 阶段 | 增量范围 | 必跑验证 |
|---|---|---|
| P0 | Flyway、应用门户协议、路由、运行态渲染 | SQL 静态扫描、Generator 编译、前端构建、门户单测 |
| P1 | 设置、发布、快照与回滚 | Generator 编译、前端构建、快照/slug 单测 |
| P2 | 创建向导、模板、Excel、应用市场 | Generator 编译、前端构建、向导组件单测 |
| P3 | AI 助理、工作台分发、移动端 | 相关模块编译、前端构建、协议单测 |

## 本轮增量验证

执行前先读取本目录的 `spec.md`、`tasks.md`、`execution-log.md`，只针对本轮变更文件扩展验证。后端测试默认使用 `-Penable-tests`，并核对 Surefire 的 `Tests run` 汇总，避免根 POM 跳过测试造成假通过。

### 低成本检查

```bash
git diff --check
rg -n '\$\{[^}]+\}' forge-server/db/migration
```

### 后端

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests
mvn -pl forge-admin-server -am package -DskipTests
```

涉及测试源码时再执行：

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am test-compile -Penable-tests
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -Penable-tests test -Dtest='*BusinessApplication*Test'
```

本变更新增/重点回归测试：

- `BusinessApplicationServiceTest`：slug 校验、配置持久化、查询 scope 与可信创建人。
- `BusinessApplicationRuntimeServiceTest`：发布快照、页面权限过滤与门户配置回放。
- `BusinessApplicationReadinessServiceTest`：门户/AI 助理发布就绪检查。
- `BusinessApplicationExcelImportServiceTest`：安全文件名、首 Sheet 预览、对象/页面草稿初始化。
- `BusinessApplicationAiAssistantServiceTest`：发布态配置、页面授权和能力边界。
- `BusinessApplicationAiInitializeServiceTest`：确认后的流程建议只创建应用级最小流程设计草稿，并绑定本次生成的主业务对象。

### 前端

```bash
source ~/.nvm/nvm.sh && nvm use v20.19.0
cd forge-admin-ui
NODE_OPTIONS=--max-old-space-size=8192 pnpm build
```

当前仓库 `pnpm-workspace.yaml` 缺少有效 `packages`，pnpm 8 会在执行脚本前报错。确认该问题与本变更无关后，使用已安装依赖中的直接入口执行同等验证：

```bash
./node_modules/.bin/eslint <本变更前端文件与目录>
./node_modules/.bin/vitest run src/views/app-center/__tests__/app-template-catalog.spec.js \
  src/views/app-center/__tests__/application-create-result.spec.js
node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build
```

涉及组件交互时，启动 Vite 后使用 Playwright 以无头 Chromium 验证门户、设置、发布和创建向导；只停止本轮启动的服务。

## 跳过项

- 真实 MySQL/Flyway 执行、Admin/Flow 启动、登录 Token 和端到端业务数据验证由用户按偏好自行执行；本轮不启动真实服务或改动数据库。
- 钉钉/企业微信凭证分发不使用伪造凭证验收；仅验证服务端输入校验、脱敏和失败关闭协议。
- Forge 首页投放、组织私有模板持久化和真实数据型 AI 查询/写入没有现成仓库协议，只验证当前配置态与安全边界，不将其记为完成的外部效果。
