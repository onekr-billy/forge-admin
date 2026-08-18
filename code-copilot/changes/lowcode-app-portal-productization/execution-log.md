# 执行日志 — 低代码应用门户产品化改造

## 2026-08-17 前置审计

- 范围：读取 `AGENTS.md`、项目记忆、Spec/Tasks、自动化测试标准；确认第 11 章技术决策，并采用第 13 章建议值。
- 现状：`forge-server/db/migration` 最新版本为 `V1.0.123`，Task 1 预定使用 `V1.0.124`。
- 现状：`BusinessApplicationRuntimeService` 已从不可变快照读取 `options.inAppBuilder` 并按权限过滤节点；`GridBlockRenderer` 已支持 AiCrudPage/AiForm/内容区块运行态复用。
- 现状：`ai_business_application.options` 为 JSON 扩展配置；本变更新增门户配置列并保留旧 options 兼容读取。
- 警告：真实 MySQL/Flyway、Admin/Flow 启动和 E2E 未执行，遵循用户环境偏好；后续必须由用户回填运行证据。

## 2026-08-17 至 2026-08-18 顺序实施

### P0 门户底座

- 新增 `V1.0.124__add_business_application_portal_config.sql`，扩展 `portal_slug`、`portal_config`、`ai_assistant_config`，初始化存量 slug/配置、逻辑删除唯一索引及隐藏路由权限资源。
- 后端支持 code/slug 解析、slug 合法性与唯一性校验、门户配置持久化、发布快照回放和当前用户页面权限过滤。
- 前端新增 `/app/:applicationCodeOrSlug`、`app-portal` 独立布局、门户导航/水印/空态/页面渲染；保留原设计态运行入口。

### P1 设置、发布和快照

- 新增应用设置页：基础、访问、导航、权限、全球化、高级配置。
- 新增应用发布页：就绪度、发布状态、访问链接/二维码、历史版本、回滚、AI 助理与分发配置。
- 发布与回滚快照包含门户和 AI 助理配置；发布前校验 slug、页面与助理绑定合法性。

### P2 创建和市场

- 新增智能、模板、Excel、空白四种创建方式；失败初始化保留应用草稿以便重试。
- Excel 只解析首 Sheet，读取表头和最多 50 行样例，生成对象、列表页、表单页草稿；不导入业务行、不自动建表。
- 应用中心新增“我创建的/我有权限的/最近使用”和“应用市场”；`CREATED` 使用可信会话用户 ID，`RECENT` 仍经租户/权限查询。
- 官方/推荐模板可立即启用或生成源码；组织私有模板因无持久化协议展示明确空态。

### P3 AI、分发和移动端

- 门户 AI 对话重新加载不可变发布快照，校验当前用户可见页面、已发布 `pageIds` 和能力；只传页面结构，不读取/写入真实业务行。
- `AiClientImpl` 移除请求正文、响应正文、系统提示词和上下文值日志，只保留长度、键数量及路由元数据。
- Forge/钉钉分发只保存配置；钉钉只接收受管连接器标识并写入 `PENDING_EXTERNAL_SYNC`，不接收 AppKey/AppSecret。
- 门户新增移动导航、流式页面区块和宽表横向操作；H5 复用同一发布快照，通过 `?display=h5` 链接和二维码访问。
- 最终审查补充应用中心 query 状态回填：组件复用或外部导航改变 `view/scope/filter/page` 时同步界面，避免重复请求。

## 2026-08-18 自动化验证

### 静态检查

```bash
git diff --check
rg -n '\$\{[^}]+\}' forge-server/db/migration
```

- `git diff --check`：通过，无空白错误。
- `${...}` 扫描：本变更 `V1.0.124` 无命中；仅命中既有 `V1.0.72` 的消息模板变量，非 Flyway 属性占位符。
- `.DS_Store`：保留用户原有根目录修改和 `forge/.DS_Store` 删除，本变更未处理这两项。

### 后端编译

环境：Java 17，`JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home`。

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests
mvn -pl forge-admin-server -am compile -DskipTests
```

- Generator reactor：通过，最终复跑 32/32 模块成功。
- Admin reactor：通过，45/45 模块成功，覆盖本变更同时修改的 `forge-plugin-ai`。
- 编译警告为既有 deprecated/unchecked/Lombok builder 提示，本轮无新增编译错误。

### 后端测试

标准 reactor `test-compile -am` 未通过，阻断点是仓库既有测试与生产构造器漂移：

- `forge-plugin-message/MessageServiceImplTest`
- `BusinessObjectPublishServiceFieldEventTest`
- `BusinessObjectPublishServiceCommandTest`

该问题与本变更生产源码无关，但不能将全量测试编译记为通过。为验证新增测试，使用 Maven 生成的测试 classpath 单独编译：

- `BusinessApplicationExcelImportServiceTest`
- `BusinessApplicationAiAssistantServiceTest`

随后执行：

```bash
cd forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator
mvn surefire:test -Penable-tests -Dtest='*BusinessApplication*Test'
```

- 结果：92 tests，0 failures，0 errors。
- 新增 Excel 测试发现并修复 `safeFileName`：无 `/` 的普通文件名此前会变为空串，导致合法 `.xlsx` 被拒绝。

### 前端 Lint、单测和构建

环境：Node.js 20.19.0。仓库 `pnpm-workspace.yaml` 缺少有效 `packages`，pnpm 8 在执行命令前报 `packages field missing or empty`；因此使用现有 `node_modules` 的直接入口。

```bash
cd forge-admin-ui
./node_modules/.bin/eslint --fix <本变更前端文件与目录>
./node_modules/.bin/eslint <本变更前端文件与目录>
./node_modules/.bin/vitest run \
  src/views/app-center/__tests__/app-template-catalog.spec.js \
  src/views/app-center/__tests__/application-create-result.spec.js
node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build
```

- ESLint：初次 0 errors / 119 个可修复换行 warnings；格式化后 0 errors / 0 warnings。
- Vitest：2 files、6 tests 全部通过。
- Vite：Node 20.19.0 下构建通过，9062 modules transformed，`built in 38.82s`。
- 构建保留的既有警告：Vite native config-loader 兼容提示、`UserSelectModal` 组件重名、CSS `//` 注释、两个 ineffective dynamic import、plugin timing 信息。

## 未执行与后续环境验收

- 未启动 Admin/Flow/Vite 开发服务；无本轮服务需要清理。
- 未连接或修改真实 MySQL/Redis，未执行 Flyway；需在隔离数据库验证新库、存量库、重复执行和索引结果。
- 未执行登录 Token、curl 新增接口、发布/回滚和权限用户的真实 API 链路。
- 未执行 Playwright、浏览器和移动真机验收。
- 未执行 Forge 首页实际投放和钉钉 Connector 同步：仓库当前没有对应持久化/调用协议，不能宣称外部效果完成。
- AI 助理没有执行真实数据查询或写入；当前能力是发布页面结构范围内的安全指导。

## 2026-08-18 Review 修复增量

### 修复范围

- 统一应用中心与正式门户的应用可见性判定：支持全员、指定角色、指定部门/组织、指定用户和应用管理员；用户、角色、组织范围均以可信会话上下文为准。
- 正式门户基于已发布版本快照中的 `portalConfig.permission` 二次校验；应用管理员绕过页面级 RBAC，`systemMenuVisible=false` 不再自动放行无页面权限的页面。
- 发布态 slug 只从已发布版本快照解析，设计态修改不会在重新发布前影响正式访问；修复 Mapper 重复包含基础列片段的问题。
- 新增 Forge 工作台投放查询接口，首页改为读取真实投放列表；当前用户投放保存可信 `targetUserId`，角色投放保存 `roleIds`，禁用/取消投放后不再展示；发布页增加取消工作台分发操作。
- 钉钉分发继续仅保存受管连接器标识和 `PENDING_EXTERNAL_SYNC`，未伪造外部同步成功。
- 将门户 slug 逻辑删除唯一索引调整为 `(tenant_id, portal_slug, del_flag)`，与既有 BIGINT 主键墓碑逻辑删除语义一致，允许删除后同 slug 重建。
- 门户根节点、水印时间消费配置中的语言、时区和日期格式；高级缓存/版本留存配置明确标注当前仅随发布快照保存，未伪造缓存清理任务。

### Review 修复验证

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests
mvn -pl forge-admin-server -am compile -DskipTests

cd forge-framework/forge-plugin-parent/forge-plugin-generator
mvn -Penable-tests -Dtest='*BusinessApplication*Test' -DfailIfNoTests=false surefire:test
```

- Generator reactor：32/32 模块编译通过。
- Admin reactor：45/45 模块编译通过。
- BusinessApplication 相关测试：96 tests，0 failures，0 errors。
- 全量 `test-compile` 仍被仓库既有测试构造器漂移阻断（`BusinessObjectPublishServiceFieldEventTest`、`BusinessObjectPublishServiceCommandTest`），与本轮生产代码无关。

```bash
cd forge-admin-ui
./node_modules/.bin/eslint <本轮修改的前端文件与目录>
./node_modules/.bin/vitest run \
  src/views/app-center/__tests__/home-workbench-apps.spec.js \
  src/views/app-center/__tests__/portal-config.spec.js \
  src/views/app-center/__tests__/app-template-catalog.spec.js \
  src/views/app-center/__tests__/application-create-result.spec.js
node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build
```

- ESLint：0 errors、0 warnings。
- Vitest：4 files、8 tests 全部通过。
- Vite production build：9062 modules transformed，构建通过（约 40.84s）。
- 保留既有构建警告：native config-loader、`UserSelectModal` 重名、CSS `//` 注释、无效 dynamic import 和 plugin timing 信息。
- `git diff --check`、Mapper XML `xmllint --noout` 和 Flyway `${...}` 占位符扫描通过。

### 本轮未执行项

- 未执行真实 MySQL/Flyway、Admin/Flow/Vite 服务启动、登录 Token/curl 链路、Playwright/浏览器及移动真机验收；本轮未启动服务，无需清理。
- 未执行钉钉 Connector 外部同步，仓库当前无对应协议；未执行真实 AI 业务数据查询或写入，保持失败关闭。
- 组织私有模板持久化仍因仓库没有持久化协议保持空态，不宣称产品化完成。

## 2026-08-18 Final Fix 验证

### 最终修复内容

- 工作台入口只把设计态分发开关作为候选，名称、slug、应用可见范围、页面权限和可达首页统一从当前不可变发布快照计算。
- `/by-slug/{portalSlug}` 兼容接口改为发布态运行时投影；发布快照缺少历史 slug 时只回退稳定应用编码。
- 前端角色 ID 全程使用字符串，后端校验角色租户、逻辑删除、启用状态、门户基础权限及当前用户可管理范围。
- Flyway 的存量 slug 去重维度与唯一索引统一为 `(tenant_id, portal_slug, del_flag)`，不会因已删除历史记录改写当前有效应用 slug。
- AI 应用方案新增流程建议；用户确认初始化后只调用应用级 `BusinessProcessService.create()` 创建最小设计草稿，不部署 Flowable、不写 BPMN/节点表单绑定。
- 移除尚无自动赋权消费协议的默认角色输入项；缓存策略和版本保留继续明确为仅随发布快照保存。
- 组织私有模板、真实 AI 数据读写、钉钉/企微 Connector 明确保持延期，不伪造完成状态。

### 后端验证

环境：Java 17，`JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home`。

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests
mvn -pl forge-admin-server -am compile -DskipTests
```

- Generator reactor：32/32 模块 `BUILD SUCCESS`。
- Admin reactor：45/45 模块 `BUILD SUCCESS`。
- 保留的警告均为仓库既有 deprecated、unchecked 和 Lombok builder 提示。

标准测试源码编译：

```bash
cd forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator
mvn -Penable-tests -DskipTests test-compile
```

- 结果：失败；仍由既有 `BusinessObjectPublishServiceFieldEventTest`、`BusinessObjectPublishServiceCommandTest` 构造器参数漂移阻断。
- 为避免复用旧 class，使用 Maven 生成的 test classpath 重新 `javac` 编译本轮新增/修改的三个测试类，编译成功。

```bash
mvn -Penable-tests -Dtest='*BusinessApplication*Test' \
  -DfailIfNoTests=false surefire:test
```

- 结果：100 tests，0 failures，0 errors，0 skipped。
- 新增 `BusinessApplicationAiInitializeServiceTest` 证明流程建议绑定本次生成对象并创建应用级流程设计草稿。

### 前端与静态验证

```bash
cd forge-admin-ui
./node_modules/.bin/eslint \
  src/views/app-center/components/create/AppCreateAi.vue \
  src/views/app-center/components/portal/portal-config.js \
  src/views/app-center/components/settings/AppSettingsPermission.vue \
  src/views/app-center/components/publish/AppPublishDistribute.vue \
  src/views/home/index.vue src/api/business-application.js

./node_modules/.bin/vitest run \
  src/views/app-center/__tests__/home-workbench-apps.spec.js \
  src/views/app-center/__tests__/portal-config.spec.js \
  src/views/app-center/__tests__/app-template-catalog.spec.js \
  src/views/app-center/__tests__/application-create-result.spec.js

node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build
```

- ESLint：0 errors、0 warnings。
- Vitest：4 files、11 tests 全部通过。
- Vite：9062 modules transformed，生产构建通过（约 32.19s）。
- 构建仅保留既有 config-loader、组件重名、CSS `//` 注释、ineffective dynamic import 和 plugin timing 警告。

```bash
git diff --check
xmllint --noout forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/resources/mapper/BusinessApplicationMapper.xml
rg -n '\$\{[^}]+\}' forge-server/db/migration/V1.0.124__add_business_application_portal_config.sql
```

- 三项通过；Flyway 脚本无属性占位符。
- 未启动任何服务，无需清理端口或进程。

### 仍需环境/平台验收

- 隔离 MySQL 中执行 Flyway 并核验索引、存量回填和重复保护。
- 启动 Admin 后验证登录、发布/回滚、不同角色/组织/用户的门户与工作台 API 链路。
- Playwright/移动真机验证设置、发布、四种创建方式、门户导航和 H5。
- 平台先提供组织私有模板、受管 Connector、AI 业务操作、缓存执行器和版本清理协议，再继续相应验收。
