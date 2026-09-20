# 低代码字段级数据变更审计 Execution Log

> change: `lowcode-field-change-audit`

## 2026-09-20 实施

- 用户要求根据 Spec 完成需求，进入 apply。
- 第 9 节未逐项书面确认的事项按 Spec 建议默认值实施：同库在线低代码、失败关闭、人工修改/删除可要求原因、不自动授敏感原值权限。
- 真实 Admin/Flow 启动、Flyway 落库和浏览器联调按用户偏好不自动执行。

## 2026-09-20 编码验证

- 后端：`mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am test -Penable-tests -Dtest=DataAuditDiffEngineTest,DataAuditPayloadSupportTest,DataAuditValueProtectorTest,DynamicCrudMoneyValueTest,DynamicCrudServiceAutoGenerationTest,DynamicCrudCryptoLifecycleTest,DynamicCrudStructuredValueTest`
  - 结果：Tests run: 21, Failures: 0。JDK 17。
- 前端：`pnpm --ignore-workspace exec vitest run src/components/data-audit/__tests__/data-audit-submit.spec.js`
  - 结果：2 passed。Node v20.19.0。
- 前端 eslint（审计相关新文件 + AiCrudPage 新增导入）：无 error。
- 未启动真实服务，未执行 Flyway 落库，未做浏览器 E2E。P0 事务/并发/流程项仍为待验。

## 2026-09-20 体验重构与增量验证

- 重构范围：对象级采集启停、变更原因必填、详情历史展示统一配置；业务详情历史入口；应用中心审计查询条件业务化；记录与字段模糊查询。
- 修复详情入口根因：详情读取现在会先加载审计策略索引，关联查询详情也会挂载 `_dataAudit` 元数据，避免服务重启后首次访问或关联对象详情被误判为未配置。
- 后端：`mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am test -Penable-tests -Dtest=DataAuditPolicyServiceTest,DataAuditRecordMetaBuilderTest,DataAuditRecordLabelResolverTest,DataAuditMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false`
  - 结果：Tests run: 8, Failures: 0, Errors: 0。JDK 17；generator 及依赖模块编译成功。
- 前端：`pnpm --ignore-workspace exec vitest run src/components/data-audit/__tests__/data-audit-submit.spec.js src/views/app-center/__tests__/data-audit-search.spec.js`
  - 结果：2 个测试文件、6 个测试全部通过。Node v20.19.0。
- 前端目标 ESLint：审计 API、配置/详情组件、管理员页面、AiCrudPage 与页面设置面板无 error、无 warning。
- 前端生产构建：`NODE_OPTIONS=--max-old-space-size=8192 pnpm --ignore-workspace build` 成功；仅有仓库既有 Vite 导入提示与 `style.css` 的 `//` CSS 注释警告。
- Flyway：`V1.0.178__add_data_audit_detail_visibility.sql` 单文件 `${...}` 占位符扫描无输出；未执行真实数据库迁移。
- 未启动 Admin/Flow/Vite 服务，未改动真实数据库，未做浏览器点击验收；按既有用户偏好留待实际环境联调。

## 2026-09-20 应用页面联动与时间轴

- 管理员页筛选改为“应用 → 页面 → 字段”联动：只返回当前用户获授权对象所在应用的数据页面，页面选定后使用业务字段名称选择，不再输入对象或字段编码。
- 业务详情的字段筛选改为按当前对象自动加载的选择器；历史列表改为按时间倒序的时间轴，节点展开时按需加载字段差异。
- 后端：`DataAuditFilterOptionServiceTest`、策略、详情元数据、记录标签及 Mapper 契约共 10 项测试通过，0 failure，0 error；JDK 17，generator 及依赖模块编译成功。
- 前端：审计提交与应用/页面/字段联动 2 个测试文件、7 项测试通过；目标 ESLint 无 error、无 warning。
- 前端生产构建成功；仅有仓库既有 Vite 导入提示与 `style.css` 的 `//` CSS 注释警告。
- 未启动 Admin/Flow/Vite 服务，未执行真实数据库迁移，未做浏览器点击验收。

## 2026-09-20 超级管理员审计范围修复

- 运行日志确认修改事件已写入 `ai_data_audit_event`、`ai_data_audit_field` 并推进 `ai_data_audit_cursor`；管理员页面与应用下拉为空的直接原因是超级管理员无显式角色审计范围时，`DataAuditScopeMapper.selectByRoleIds` 返回空集合。
- 范围规则调整为：超级管理员读取当前租户全部已配置策略对象，包含已停用但保留历史的对象；普通用户继续按角色审计对象范围控制。列表、筛选选项和事件详情共用该规则。
- 当前日志只有一条明确的 `UPDATE` 审计事件，没有发现 `DynamicCrudController.delete/remove/batchDelete` 请求，无法把本次用户描述的删除操作确认成主记录删除；现有主记录、批量和子表删除入口代码仍保留审计采集。
- 首次定向测试因本机 Mockito inline Byte Buddy 无法附着 JDK 17 而失败；测试替身改用仓库现有的 JDK 动态代理方式后通过，该失败不涉及业务代码。
- 后端：`mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am test -Penable-tests -Dtest=DataAuditScopeServiceTest,DataAuditFilterOptionServiceTest,DataAuditPolicyServiceTest,DataAuditRecordMetaBuilderTest,DataAuditRecordLabelResolverTest,DataAuditMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false`
  - 结果：Tests run: 13, Failures: 0, Errors: 0；generator 及依赖模块编译成功，JDK 17。
- 静态检查：`git diff --check` 通过；相关新增文件无行尾空白。
- 未重启用户当前运行的 Admin 进程，修复需重启 Admin 后才会生效；未改动真实数据库，未把浏览器/API 复验标记为通过。

## 2026-09-20 字段容量与时间轴增量修复

- 数字字段不再把 MySQL 整数显示宽度当成“最大长度”：业务字段面板增加最小值、最大值；decimal 增加总位数、小数位和实际数据库范围说明。表单设计器的数字/金额组件也直接显示页面级数值范围。
- 运行表单统一下发 `maxlength/min/max/precision`，字段级约束和数据库物理范围共同限制页面配置；页面设置不能放宽字段容量。
- 新增保存前字段校验器并接入动态 CRUD 主表各写入口：字符列、多选/文件/图片序列化值、整数范围、decimal 总位数/小数位、MONEY 元/分转换前范围均在 JDBC 前返回业务错误；子表规则同步补充数据类型、长度和精度校验。
- 模型发布校验提前拒绝非法 varchar/char 容量、decimal 精度和 min 大于 max。
- 详情时间轴自动加载当前页字段差异，主表变化优先显示；默认直接展示前 4 项，支持展开全部，CREATE/UPDATE/DELETE 使用对应的前后值文案。管理员详情弹窗复用相同展示。
- 后端：JDK 17 下运行字段约束、运行配置、动态 CRUD 金额/结构化值/自动生成和审计差异共 38 项测试，0 failure、0 error；generator 及依赖模块编译成功。
- 前端：审计显示、提交、应用页面联动共 3 个测试文件、10 项测试通过；相关字段面板和审计组件目标 ESLint 无错误；`pnpm build` 成功，仅保留仓库既有 Vite 导入与 CSS 注释警告。
- `git diff --check` 通过。未重启 Admin/Vite，未连接真实数据库执行保存或浏览器点击验收。

## 2026-09-20 主表差异与大量历史修复

- 根因：业务详情事件分页接口允许 `ai:dataAudit:record`，但事件详情和字段明细 Controller 只允许 `ai:dataAudit:detail`；字段请求在进入已支持 RECORD 模式的查询服务前被拦截，前端又把失败静默显示成无字段变化。
- 权限修复：事件详情和字段分页允许记录、列表、详情任一入口权限进入，查询服务继续根据 `accessMode=RECORD/AUDIT` 校验当前记录或审计对象范围。
- 展示修复：字段明细明确分成“主表字段变化”和“子表变化”，主表优先显示字段名称、字段编码、修改前值和修改后值；加载失败展示具体错误空态。
- 大量历史：业务详情时间轴采用服务端分页，默认每页 10 次，支持 10/20/50 切换，并显示历史总数；每页字段差异仍自动加载。
- 后端：`mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Penable-tests -Dtest=DataAuditControllerPermissionTest,DataAuditDiffEngineTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - 结果：Tests run: 4, Failures: 0, Errors: 0；generator 及依赖模块编译成功，JDK 17。
- 前端：`pnpm --ignore-workspace exec vitest run src/components/data-audit/__tests__/data-audit-display.spec.js src/components/data-audit/__tests__/data-audit-submit.spec.js src/views/app-center/__tests__/data-audit-search.spec.js`
  - 结果：3 个测试文件、11 个测试全部通过，Node v20.19.0。
- 目标 ESLint 无 error、无 warning；前端生产构建成功。构建仅报告仓库既有 Vite import 与 CSS `//` 注释警告。
- 未启动真实服务、未修改数据库、未执行登录态浏览器验收；Admin 重启后需用只有记录权限的账号复核业务详情字段明细。

## 2026-09-20 主表字段采集完整性修复

- 用户新产生的事件仍只有 `__childRows` 子表摘要，且事件头显示“子表 1 项”；这表明本次主表字段明细在采集持久化阶段已经缺失，前端没有可展示的数据。
- 根因：采集服务只在 `modelSchema.fields` 整体为空时使用物理快照字段兜底；只要模型字段非空，模型中遗漏、编码不一致或列映射过期的真实主表列就完全不会进入差异引擎。
- 修复：新增审计字段解析器，合并模型与 `editSchema` 元数据，并在每次主表差异计算前用更新前后快照增量补齐业务列；字段编码按驼峰/下划线对齐，真实快照列优先用于取值，系统字段继续排除。
- 后端：JDK 17 执行 generator 全部 `DataAudit*Test`，共 24 项测试，0 failure、0 error；generator 及依赖模块编译成功。
- 一次脱离 reactor 的 generator 单模块复跑读取了本地旧版 `forge-flow-client`，既有 `BusinessDocumentRuntimeServiceTest` 编译时报缺少批量任务方法；改回项目规定的 `-pl ... -am` 完整依赖链后编译及 24 项审计测试全部通过。
- 自动化覆盖模型字段非空但遗漏 `field_number`、编辑表单中文名称“业务数量”、前后值 `1 → 2`、过期列映射修正、敏感元数据保留和 `update_by` 排除。
- 现有旧事件没有持久化主表前后值，无法从子表摘要可靠重建；修复需重启 Admin，并以重启后新产生的修改事件验收。未启动服务、未连接真实数据库、未做登录态浏览器复验。

## 2026-09-20 20:14 事务提交阶段的业务快照修复

- 用户提供的新事件落库行只有子表删除摘要。记录标题中的两个值只是记录标识，不是修改前后值；上一轮字段解析测试没有覆盖 open、Repository 写钩子与事务提交回调之间的时序。
- 代码缺陷：`DynamicCrudService` 在方法返回前关闭运行数据源 Scope，Spring 事务在方法返回后执行 `beforeCommit`。原 `RepositoryRowReader` 直接依赖此时线程上下文，会退回平台 JDBC；平台连接若看到同名记录旧值，主表差异就被判为无变化。子表删除用显式 null 标记，不需回读，因此仍能留下摘要。
- 先复现：新增 `DataAuditCaptureServiceTest`，使用真实采集服务、策略索引、Holder 写钩子和事务同步回调，Repository 替身按数据源上下文返回不同快照。原代码运行结果为 `Tests run: 1, Failures: 1, Errors: 0`，断言“同一事件必须包含两项主表变化和一项子表摘要”得到 `expected: 3 but was: 1`。日志：`/private/tmp/forge-audit-capture-before.log`。
- 修复：写入时保存每行读取器及实际主键列；读取器捕获运行数据源上下文，并只在行读取期间恢复该上下文。自增主键先保存表级信息，生成 ID 后绑定到行；标题回读复用捕获的读取信息。正常返回或异常均恢复调用方上下文。
- 通过命令（工作目录 `forge-server`）：

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 17) PATH="$JAVA_HOME/bin:$PATH" \
  mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am \
    -Penable-tests '-Dtest=DataAudit*Test' -Dsurefire.failIfNoSpecifiedTests=false test
  ```

- 结果：`Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`。其中采集链路新增 8 项：主表与子表共同采集、主表多次更新取最终实际值、同值无事件、自增自定义主键、不同行数据源保留及调用方上下文恢复、读取异常阻断并恢复、回滚清理、默认平台数据源兼容。日志：`/private/tmp/forge-audit-capture-after.log`。
- 读取失败测试刻意产生“数据审计持久化失败”错误日志，断言已验证该失败会向外抛出并恢复上下文，不是构建失败。
- 验证范围：这是服务级模拟事务生命周期测试，未执行真实 SQL，也未证明用户当前配置一定触发此场景。真实 MySQL、跨数据源事务原子性和登录态浏览器复验仍待验；未启动/重启任何用户服务、未修改真实数据库。本轮只改 Java，未重复前端构建。
- 历史缺失的主表前值不做推测回填；Admin 重启后以新修改事件验收，检查同一 `event_id` 下既有空 `relation_key` 的主表字段，也有非空 `relation_key` 的子表摘要。
