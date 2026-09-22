# 执行记录
## 2026-09-21
- 在独立 worktree forge-admin-main / codex/open-platform-experience 继续，保留前期开放平台改动及原有 .DS_Store，不修改 H5。
- 读取 AGENTS、DESIGN、测试标准、偏好与 capability 踩坑，使用 Forge 流程开发 Skill 及低代码/状态/验收/SQL 参考。
- 确认低代码 BusinessMessageChannelService 的第三方通道仍返回 TODO，纳入本轮实际打通；不伪装已实现待办同步。

### 实现
- Admin 编排层新增应用集成 API、DTO、Mapper XML 和 Service；插件间不新增循环依赖。新增 V1.0.183 两张引用表，所有查询限定租户/应用，配置保存带 revision，能力发布与关联同事务。
- 复用现有平台连接、消息模板、身份绑定、消息中心和能力发布器。保存应用协同绑定只维护专属通道，解绑停用；指定连接新增租户一致性防护。
- SEND_MESSAGE 节点增加站内信/本应用企业协同选择，明确收件人为流程发起人；使用 runId + nodeId 幂等键。审批回调在可信租户上下文执行，不要求伪造交互登录。入队与实际投递状态在文案中区分。
- 新增 Pinia 应用集成 store 及四页签；迟到请求隔离、防双击、空态/错误、当前能力选择、授权版本策略/失效时间、服务端分页。注册锁定已发布应用来源，不纳入历史全局能力。
- 复用 Naive 主题变量，适配窄屏、深色、表格横向滚动和模态框高度。消息配置拆分为叶子组件，原样外提旧 CSS，ActionAndApprovalNodeConfig.vue 降至 800 行。
- 企业微信入口支持显式连接、保存并恢复原应用深链；授权和回调拒绝已关闭的免登连接。

### 增量验证
- Vitest 最后执行：5 文件、62 tests passed（22:44:52）；ESLint 定向校验无输出，成功。
- Vite build 通过，28.01s；JDK17 Admin reactor 46 模块 BUILD SUCCESS，23.441s（22:45:12）。
- 最后后端测试（22:48:50）：本轮 20/20、原开放平台 37/37，均 failures=0 / errors=0。
- 定向测试首次遇到 Mockito 对 Long 未 stub 的默认值为 0；应用成员断言改为精确匹配 capabilityId，并拒绝非正 ID，重跑通过。
- 常规后端全量测试编译有既有构造器/缺少 import 问题，使用独立 verification/pom.xml 复用真实源码执行本轮定向测试，未修改无关测试。
- xmllint、迁移静态扫描与 git diff --check 通过。没有执行数据库脚本或初始化数据。
- 浏览器夹具真实组件验证连接保存、已保存/未保存提示、能力卡片、授权弹窗/列表回填、调用记录、390px 深色注册步骤及应用锁定；mock 发布后仅关闭一层注册框。390px 页面无横向整体溢出，console error=[]。
- 临时服务和测试标签已关闭，viewport override 已恢复；保留用户原有预览标签。

### 复现命令
从独立 worktree 执行，JAVA_HOME 使用 JDK17，Maven 依赖已安装到本轮临时仓库：

```bash
# forge-admin-ui 目录
./node_modules/.bin/vitest run src/views/ai/capability/__tests__ src/stores/application/__tests__ src/views/app-center/__tests__/application-integration-entry.spec.js src/components/business-process-designer/__tests__/business-process-designer.spec.js
pnpm build

# forge-server 目录
mvn -pl forge-admin-server -am install -Dmaven.test.skip=true

# 仓库根目录（使用同一 Maven 本地仓库）
mvn -f code-copilot/changes/lowcode-application-integrations/verification/pom.xml -Penable-tests test
mvn -f code-copilot/changes/open-platform-experience/verification/pom.xml -Penable-tests test
git diff --check
```

### 交付边界
- 完成代码与本地定向验证，未启动 Admin/Flow、未连接/修改数据库、未真实免登/发信/授权、未部署。
- 钉钉、飞书、企业微信待办同步、任意收件人设计器和外部主子表填报不包含在本期；UI 明示相应边界。
- 没有生成 commit 或 push；原 H5 工作树未改动，现有 .DS_Store 改动保留。
- 使用和上线说明见 usage.md；真实环境验收项留在 tasks.md。

## 2026-09-22 本地增量收尾

### 范围与发现
- 用户要求“继续”，按本地代码完善和验收继续；未收到新的部署目标或真实环境操作授权，维持原安全边界。
- 重读流程开发 Skill、低代码/回调/验收参考，复用已有 spec/tasks/test-spec；检查了主应用扫描、发布器开关、流程验证及消息运行态，不另建流程通知链路。
- 定向测试证实：较旧的配置刷新会覆盖刚保存的 revision；刷新前或刷新中选择连接会丢失。修复为写操作使旧读取失效、写入中禁止同域刷新、刷新保留用户选择。
- 消息节点补充运行时模板启用校验、布尔开关类型和稳定节点 ID 校验；专属应用通道类型发生漂移时拒绝，避免存量动作把异常通道降级为 WEB。继续沿用现有消息中心、幂等键、租户和审批权限。

### 红/绿验证证据
- `vitest run src/stores/application/__tests__/integration-store.spec.js`：修复前 4 failed / 8 passed，明确复现旧 revision 回写及选择丢失。
- 独立后端验证 POM：修复前 24 tests / 4 failures，都是新增消息边界用例；修复后 24 tests / 0 failures / 0 errors（00:04:52）。
- 完整本轮前端定向矩阵：66 tests / 5 files passed（00:03:17）；定向 ESLint 成功且输出为空。
- Admin reactor 使用同一临时 Maven 仓库重建：46 模块 BUILD SUCCESS，22.333s（00:03:45）；之后才运行依赖新构件的测试。
- 原开放平台后端回归：37 tests / 0 failures / 0 errors，3.118s（00:06:21）。
- 前端执行 `node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build`：通过，26.75s。保留原有 CSS/chunk/timing 提示，不声称全项目零警告。
- `git diff --check` 通过；未修改本轮之外的迁移、包锁、H5 工作树或 .DS_Store。

### 环境及命令说明
- 按偏好尝试 `source /Users/mini32g/.nvm/nvm.sh && nvm use v20.19.0`，返回 N/A；未安装新 Node，使用现有 Node v24.21.0。
- `pnpm --ignore-workspace build` 在自动依赖检查阶段因 `ERR_PNPM_IGNORED_BUILDS` 停止，还未进入构建；没有批准额外依赖脚本。直接运行 package.json 原有 build 脚本正文完成验证。pnpm 自动加入的 allowBuilds 占位项仅恢复本轮产生部分，`git diff --exit-code -- forge-admin-ui/pnpm-workspace.yaml` 确认与基线相同。
- 本轮 Java 命令沿用 `JAVA_HOME=/private/tmp/forge-open-platform-java.CanL0f/amazon-corretto-17.jdk/Contents/Home`，Maven 路径为该临时目录下 `apache-maven-3.9.11/bin/mvn`，追加 `-Dmaven.repo.local=/private/tmp/forge-open-platform-java.CanL0f/repository`；测试显式 `-Penable-tests`。
- 前端定向测试命令沿用上一节的 5 路径矩阵；ESLint 本轮只校验 `integrationStore.js`、`integration-store.spec.js`、`ApplicationCollaboration.vue`。

### UI 与清理
- 只启动 `127.0.0.1:5198` 模拟 API 组件夹具；浏览器实际点击未保存选择、刷新、保存绑定，确认刷新后选择仍保留、保存后免登入口和消息通道回填。
- 控制台 error=[]；本轮没有设置 viewport override。创建的浏览器标签已关闭，临时 Vite 执行会话 30104 已发送 Ctrl-C 并确认退出 130，没有停止用户进程。
- 没有部署、真实数据库/企业微信调用、commit 或 push。真实环境验收清单仍保持未完成状态，不能用这些 mock/单元测试替代。

## 2026-09-22 控制台细节与配置闭环

### 实现与根因
- 在 `forge-admin-main / codex/open-platform-experience` 独立工作树继续，保留已有 `.DS_Store`；原 H5 工作树仍为 `codex/mobile-lowcode-runtime-refactor`、`32915fedd4057fc035fb11925bee4f32b4788912`，工作区干净。
- 先扩展现有 spec/tasks/test-spec，读取 DESIGN、自动化测试标准、Forge 流程开发 Skill 及相关参考，按既有链路做增量修复。
- 顶部导航使用数据库菜单 `/open-platform/capability-*`，文件自动路由却为 `/ai/capability/*`，而 guard 不会注册菜单路由，导致 404。新增统一页面路由描述、兼容别名并复用到 header。导航 SQL 排除了 visible=0 的调用记录/授权工具页，补充这两页原有查询权限的精确校验，避免修复后仍 403；没有全局路由放行或后端接口权限调整。
- 企业连接和接入系统改为同页工作区，保留原列表状态；编辑使用单层局部弹窗。统一紧凑间距、主题色、表格操作列和窄屏表单，应用集成改为连接/免登/消息的配置行布局。
- 连接详情兼容扁平对象及嵌套 VO；读取/提交按连接或客户端生命周期隔离，保存防重复，失败保留编辑。客户端切换或关闭清理子弹窗和旧请求状态。
- 后端配置检查增加 Secret 与企业微信 CorpID/AgentId 条件。专属消息通道启用标记只表示当前应用是否绑定，实际投递复用现有动态校验，避免绑定时无 MESSAGE 永久锁死通道。解绑仍停用，不降级。历史旧通道提供“重新应用配置”入口，升级注意项写入 usage.md。

### 验证证据
- 新增后端用例首次 27 tests / 3 failures，复现缺凭据误报可用与消息通道状态固化；修复后 27/27 通过，最后执行 12:32:12、3.332s。
- 前端最终 11 files / 98 tests passed，12:37:50、2.53s，含最后补充的隐藏工具页权限矩阵。测试存在既有 designer component 重复注册提示，断言均通过。
- 本轮修改和新增的 20 个 JS/Vue 文件全部定向 ESLint 通过，0 errors / warnings。
- JDK17 Admin reactor 46 模块 BUILD SUCCESS，21.341s；最终 Vite 生产构建通过，29.95s。未运行存在既有测试编译问题的全仓测试；复用独立验证 POM，无无关测试源码修改。
- `git diff --check` 通过；修改 SFC 最大 778 行。没有迁移或包锁变化。

本轮前端增量矩阵在 UI 目录执行：

```bash
./node_modules/.bin/vitest run src/views/ai/capability/__tests__ src/stores/application/__tests__ src/views/app-center/__tests__/application-integration-entry.spec.js src/components/business-process-designer/__tests__/business-process-designer.spec.js src/router/guards/__tests__ src/views/system/collaboration/__tests__
node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build
```

Java 使用上一节同一 JDK17 / Maven / 临时依赖仓库。执行 Admin `-pl forge-admin-server -am install -Dmaven.test.skip=true` 后，执行本变更 `verification/pom.xml -Penable-tests test`。本轮未重复运行未修改的原开放平台 37 项后端矩阵，沿用上一节通过记录。

本地日志：`/private/tmp/forge-console-frontend-tests-final.log`、`/private/tmp/forge-console-vite-build-final.log`、`/private/tmp/forge-console-admin-build.log`、`/private/tmp/forge-console-backend-tests-final.log`。

### 浏览器与交付边界
- localhost:5199 夹具加载真实路由页面和配置组件，但使用有状态模拟 API、列表外壳及用户选择占位组件。已点击顶部三页导航、连接应用编辑/能力绑定、应用集成保存回填，并检查桌面/390px/深色样式；不冒充完整系统 E2E。
- 移动端页面无整体横向溢出；局部表格横向滚动，弹窗内容独立滚动，保存按钮可见。修复最初两个夹具 mock export 缺失后，无新增浏览器 error。
- 未连接真实数据库、企业微信或外部系统，未启动业务服务/执行迁移/部署，未 commit/push。用户原有 file:// 页面是旧静态预览，真实工程改动不会自动更新它。
- 验收结束已恢复浏览器 viewport、关闭本轮标签 6，保留用户原静态预览标签；仅停止本轮 localhost:5199 服务（执行会话 80679，退出 130）。最终 git diff --check 通过。
