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

### 运行环境暴露的共享字典缓存回归

门户环境验收过程中若组织树触发 `LinkedHashMap cannot be cast to SysDictData`，增量执行：

```bash
cd forge-server
mvn -Penable-tests -pl forge-framework/forge-starter-parent/forge-starter-cache \
  -Dtest=ForgeCacheAspectTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-system \
  -Dtest=SytemDictValueProviderLegacyCacheTest,SysDictDataServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl forge-admin-server -am -DskipTests compile
```

验收标准：缓存命中的泛型集合元素按方法声明类型恢复，历史 Map 字典项可正向/反向翻译，`SysOrgTreeVO.orgType` 不再抛强转异常。

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

### 2026-08-18 字段组件完整支持增量

验证范围：左侧字段货架、字段默认模型、后端组件校验、表单布局编译、运行时类型保留、数组型字段持久化和高级 Widget 渲染。

```bash
cd forge-server
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests

cd forge-framework/forge-plugin-parent/forge-plugin-generator
mvn -Penable-tests \
  -Dtest='BusinessObjectDesignerPageSchemaTest,LowcodeRuntimeConfigBuilderTest,DynamicCrudStructuredValueTest' \
  -DfailIfNoTests=false surefire:test
```

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh && nvm use v20.19.0
./node_modules/.bin/eslint <字段组件本轮文件>
./node_modules/.bin/vitest run \
  src/components/ai-form/__tests__/AiFormItem.spec.js \
  src/views/app-center/components/designer/__tests__/business-form-runtime-compile.spec.js \
  src/views/app-center/__tests__/home-workbench-apps.spec.js \
  src/views/app-center/__tests__/portal-config.spec.js \
  src/views/app-center/__tests__/app-template-catalog.spec.js \
  src/views/app-center/__tests__/application-create-result.spec.js
node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build
```

验收标准：左侧 33 个字段组件均有字段合同和运行时渲染分支；年份/月/日期及范围控件按格式化字符串回显和提交；后端校验全部放行；13 个扩展组件发布不降级；5 个数组型组件可 JSON 往返；19 个独立 Widget 不被编译器过滤。

### 2026-08-18 Task 18 页面形态与表单保存增量

验证范围：页面形态草稿生成、五种基础字段合同、对象字段复用、表单设计器对象信息、已有数据字段锁定、页面保存 API 元数据事务/DDL 顺序和 Controller 权限。

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh && nvm use v20.19.0
./node_modules/.bin/eslint \
  'src/views/app-center/application-runtime.[applicationCode].vue' \
  src/views/app-center/components/designer/PageTypeSelector.vue \
  src/views/app-center/in-app-builder/page-shape-design.js \
  src/views/app-center/in-app-builder/page-form-object-promotion.js \
  src/views/app-center/in-app-builder/in-app-builder-schema.js \
  src/views/app-center/in-app-builder/__tests__/page-shape-design.spec.js \
  src/views/app-center/components/designer/forge-form-designer/ForgeFormCanvasNode.vue \
  src/views/app-center/components/designer/forge-form-designer/ForgePropertyPanel.vue \
  src/views/app-center/components/designer/forge-form-designer/ForgeFormDesigner.vue \
  src/api/business-application.js
./node_modules/.bin/vitest run \
  src/views/app-center/in-app-builder/__tests__/page-shape-design.spec.js \
  src/views/app-center/components/designer/form-first/__tests__/formDesignerSchema.spec.js
NODE_OPTIONS=--max-old-space-size=8192 \
  ./node_modules/.bin/vite build
```

结果：定向 ESLint 通过；Vitest 2 files、15 tests 全部通过；Vite 生产构建通过（9066 modules transformed）。新增用例确认对象字段拖入画布后继承 `fieldBinding.locked`；保留仓库既有 config-loader、组件重名、CSS `//` 注释、ineffective dynamic import 和 plugin timing 警告。

```bash
cd forge-server
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile

cd forge-framework/forge-plugin-parent/forge-plugin-generator
# 根 POM 的 test-compile 仍会被仓库既有构造器漂移阻断；先复用 Maven 生成的测试 classpath 编译本轮测试。
javac -cp "target/classes:target/test-classes:$(<target/test-classpath.txt)" -d target/test-classes \
  src/test/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationPageDesignServiceTest.java \
  src/test/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationPageFieldGuardTest.java \
  src/test/java/com/mdframe/forge/plugin/generator/controller/BusinessApplicationControllerTest.java
JAVA_TOOL_OPTIONS='-javaagent:/Users/yaomindong/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar' \
  mvn -Penable-tests \
  -Dtest='BusinessApplicationPageDesignServiceTest,BusinessApplicationPageFieldGuardTest,BusinessApplicationControllerTest' \
  -DfailIfNoTests=false surefire:test
```

结果：Generator reactor 32/32 模块编译通过；本轮后端定向测试 17 tests、0 failures、0 errors。新增用例覆盖锁定组件字段编码变更、组件类型变更和旧草稿锁标记回填前的兼容；测试使用显式 Byte Buddy agent 绕过当前 JVM 无法 self-attach 的环境限制，未修改 POM 或测试资源。

### 2026-08-19 Review 修复增量

验证范围：工作台重定向、页面管理选择、设计资源树单页面项、发布快照分发和门户权限 ID 校验。

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh && nvm use v20.19.0
./node_modules/.bin/vitest run \
  src/views/app-center/__tests__/workspace-redirect.spec.js \
  src/views/app-center/in-app-builder/__tests__/page-management.spec.js \
  src/views/app-center/__tests__/application-designer-navigation.spec.js \
  src/views/app-center/__tests__/portal-config.spec.js
```

```bash
cd forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
JAVA_TOOL_OPTIONS='-javaagent:/Users/yaomindong/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar' \
  mvn -Penable-tests \
  -Dtest='BusinessApplicationServiceTest,BusinessApplicationRuntimeServiceTest' \
  -DfailIfNoTests=false surefire:test
```

## 跳过项

- 真实 MySQL/Flyway 执行、Admin/Flow 启动、登录 Token 和端到端业务数据验证由用户按偏好自行执行；本轮不启动真实服务或改动数据库。
- 钉钉/企业微信凭证分发不使用伪造凭证验收；仅验证服务端输入校验、脱敏和失败关闭协议。
- Forge 首页投放、组织私有模板持久化和真实数据型 AI 查询/写入没有现成仓库协议，只验证当前配置态与安全边界，不将其记为完成的外部效果。
