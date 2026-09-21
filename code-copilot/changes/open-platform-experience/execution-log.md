# 执行记录

## 2026-09-21
- 从独立 worktree 的 `codex/main-feature-development` 创建 `codex/open-platform-experience`，基线 c06cb0f7。
- 工作区原有 `.DS_Store` 修改保留。
- 完成目录、注册器、客户端、系统服务适配及能力安全规范梳理。
- 本机未发现 Maven/JDK，新 worktree 尚无前端依赖；验证时补充实际结果。

## 实施与复核

- 目录与接入系统重新分工；注册器三步引导，技术字段折叠、默认生成编码、发布前来源/字段确认。
- 提取注册、调用指南、在线测试、客户端工作台逻辑；Pinia 承载注册跨组件状态，SFC 均小于 800 行。
- 应用页面从发布快照解析业务对象；通用单表表单使用已发布运行配置与字段白名单，复杂来源明确拒绝。
- 新增 REST 显式纳管注解及受控定义，字典查询示例；新增低代码原子建单回执；撤回走既有业务流程服务及实例校验。
- 使用流程开发 Skill 的边界约束：保持原业务流程服务、委托用户、租户/组织、发起人检查和状态回写；不直接修改流程表。
- 测试中修复：空策略字段导致白名单误筛空、数据源快照未扁平化检查、撤回实例固定、草稿关闭重开竞态、异步校验期间双击、Naive UI 卡片内容类名错误导致底部按钮裁切。
- 对照菜单迁移核对 `/open-platform/*` 路由；“机器客户端”只重命名内置菜单，不扩大授权。

## 验证证据（2026-09-21 21:20 收尾）

- 前端依赖在独立 worktree 安装；恢复 pnpm 自动附带的 workspace 配置修改，package.json、lockfile、workspace 配置均无交付差异。
- 官方临时 JDK 17/Maven 位于 `/private/tmp/forge-open-platform-java.CanL0f`，下载校验摘要通过；使用独立 Maven repository，没有修改系统 Java/Maven 或用户全局配置。
- 定向 ESLint：退出码 0，无 errors/warnings。
- Vitest：`Test Files 2 passed (2)`，`Tests 22 passed (22)`（21:18:39）；具体命令见 test-spec。
- 生产构建：`✓ built in 28.95s`，退出码 0。保留存量 CSS/体积/构建插件计时警告，不宣称全项目零警告。日志 `/private/tmp/forge-open-platform-build-final.log`。
- 能力模块 reactor（含 system/core 依赖）40 模块 install：`BUILD SUCCESS`。日志 `/private/tmp/forge-open-platform-backend-final-install.log`。
- 常规 reactor 测试首先被存量 `DynamicCrudPrintReadTest` 构造器缺少 DataAuditCaptureService 阻断；单独模块测试又被 `SecureActionCatalogServiceTest` 缺少 SecureActionDescriptor 导入阻断。没有为此修改无关测试。
- 使用交付的 `verification/pom.xml` 编译/执行相关 9 类测试：`Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（21:15:31）。包含调整后的自动装配测试，不是只跑新类。日志 `/private/tmp/forge-open-platform-isolated-tests.log`。
- Admin 完整聚合打包 46 模块：`forge-admin SUCCESS`、`BUILD SUCCESS`（21:20:55）。中间依赖下载耗时约 4 分钟，最终通过。日志 `/private/tmp/forge-open-platform-admin-package.log`。
- `xmllint --noout --nonet`：Mapper XML、verification POM 通过。
- SQL 版本唯一、防重复保护、审计字段与幂等索引检查通过；181/182 无 `${...}` 占位符，不含 DROP/DELETE/TRUNCATE。
- `git diff --check` 通过；本 worktree 无 H5 文件差异，原 H5 工作区及分支未切换、未编辑。

## 浏览器验证及环境收尾

- 临时夹具使用真实注册器/接入工作台组件，API 全部模拟；`127.0.0.1:5197`，未访问真实业务接口。
- 验证应用选择、页面绑定、表单字段及必填锁定、确认页、REST 接口参数展示、授权工作台空态，浅色/深色和 390×844 窄屏；最终 console errors 为空。
- 预览只验证组件交互/布局，不代表整页路由或真实服务 E2E。默认视口已恢复、临时浏览器页已关闭。
- 本轮 Vite 预览（终端会话 29424）已通过 Ctrl-C 停止，退出码 130；未停止用户其他服务。Maven 聚合打包正常结束。
- 未启动 Admin/Flow、未连接数据库/Redis、未运行 Flyway、未操作真实客户端凭据或授权、未执行真实审批。SQL/权限人工审查与测试环境联调仍是上线前门槛。
- 未生成 Git commit，未 push，未部署。后续开发继续在此独立 worktree；使用与上线指南见 integration-guide.md。
