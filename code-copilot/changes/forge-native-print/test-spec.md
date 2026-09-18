# 测试与验收基线

> 状态：M1 增量验证中；代码单测已开始，浏览器/构建结果逐项追加。
>
> 依据：[spec.md](spec.md)、[tasks.md](tasks.md)、`code-copilot/rules/automated-testing-standard.md`

## 1. 原则与分工

- 后续每轮先读本文件与 execution-log，只增加本轮差异，不重建计划。
- 单测针对分页边界、业务规则、权限与并发；不写仅断言文件名或实现字符串的低价值测试。
- 纯函数可使用固定测量结果验证分页；真实字体/图片/页面高度必须在浏览器验证。
- 数据全部合成，不使用真实姓名、业务单据、签名或附件。
- 本轮不启动真实 Admin/Flow、不运行数据库迁移；后续实际联调继续按用户既有分工执行并回填。

## 2. P0 必过矩阵

| 用例 | 关联需求 | 预期 |
|---|---|---|
| 协议往返及未知版本/元素 | F01 | 不丢配置；不支持的版本/元素拒绝并定位 |
| 字段 0/false/null/超长 ID | F04/F08 | 0 和 false 正常输出；ID 无精度损失 |
| 金额分→元/负数/大额 | F08 | 定点格式一致，无浮点误差 |
| 毫米坐标与缩放 | F03/F05 | 50%/100%/150% 拖拽后存储尺寸一致 |
| 0/1/多页/500 行明细 | F06 | 行无漏失/重复，页数稳定，表头重复，合计仅末尾 |
| 恰好页满/剩余不足一行/合计另页 | F06 | 不多空页、不压页脚、不重叠 |
| 超高行/超高固定区块/超限页数 | F06/F12 | 明确阻止输出并定位；无死循环/截断 |
| 中英文长文本/中文标点/字体替换 | F06/F12 | 真实浏览器测量与预览一致；缺字字体失败可见 |
| 图片延迟/403/解码失败/超时 | F12/F16 | 不输出缺签名的“成功单据”，可重试且资源清理 |
| 并发编辑/重复发布/停用 | F02/F13 | CAS 拒绝覆盖；旧版本不可变；停用旧版本不可绕过 |
| 应用发布/回滚/模板删除 | F13 | 引用固定版本；回滚一致；被引用模板不能直接删除 |
| 字段删除/改型/表单重命名 | F07 | 发布检查定位失效绑定；稳定身份不随展示名称漂移 |
| 列表行/详情的统一打印动作 | F09 | 同一记录得到一致授权上下文；不信任前端 row 正文；未保存记录拒绝 |
| 跨租户/无记录权/未发布模板 | F15 | prepare 拒绝；不返回正文与文件引用 |
| 隐藏主字段/子表列/脱敏字段 | F08/F15 | 响应中不含不可见值；模板绑定无法绕过 |
| 伪造 task/instance/run/record 组合 | F10/F15 | 服务端验证关联并拒绝不一致请求 |
| 待办/已办/我发起身份 | F10 | 分别验证授权；不能只凭 readonly 读取 |
| 会签/退回重提/同节点多次办理 | F11 | 不同 taskId 保留，单据不混不同实例轨迹 |
| 实时数据与归档语义 | F10/F12 | 明确 CURRENT/生成时间；不声称历史原样 |
| 模板脚本/非法 HTML/原型链绑定/任意 URL | F01/F15 | 服务端和渲染层白名单拒绝，不执行动态代码 |
| 模板切换/预览关闭/路由卸载 | F16 | 无残留事件、iframe、临时 DOM/Blob URL，无任务串数据 |
| 下载代码打印运行 | F14 | 模板/绑定/依赖完整，与在线使用同一协议解释器 |
| 打印审计事件 | F15 | 只记身份/结果，不记正文；DIALOG_OPENED 不表示出纸 |

## 3. P1 与人工验收

- 键盘操作、框选/多选、复制粘贴、撤销/重做；保存失败保留草稿。
- 明暗主题、桌面窄窗口、中文文案；纸张保持白底，不出现横向溢出遮挡按钮。
- 模板选择：一个直接预览、多个可选、无模板明确提示；无设计权用户仍可按运行权限打印。
- 多行表头/表头横向合并；表格前后文本顺延；页码/总页数稳定。
- 浏览器取消打印不提示“打印成功”；用户另存 PDF 的文件能够打开，页数/内容与预览一致。
- 性能采样：10/100/500 行的测量时间、分页时间、页数、峰值资源；记录机器/浏览器与字体，不预先宣称性能达标。
- 实机检查 A4 100% 缩放、关闭浏览器额外页眉页脚，尺量纸面尺寸与边距；不同打印机分别记录偏差。

## 4. 计划命令

以下为后续执行模板，不是本轮成功记录；文件/模块尚未新增前不得执行或伪造通过。

前端（工作目录 forge-admin-ui）：

```bash
source ~/.nvm/nvm.sh && nvm use v20.19.0
pnpm --ignore-workspace exec vitest run src/components/print src/stores/print
pnpm --ignore-workspace exec eslint src/components/print src/stores/print src/views/print src/api/print.js
NODE_OPTIONS=--max-old-space-size=8192 pnpm --ignore-workspace build
```

后端（工作目录 forge-server，先确认 Java 17 实际路径，不照抄旧机器路径）：

```bash
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-print -am compile -DskipTests
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests
mvn -pl forge-admin-server -am package -DskipTests
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-print -am test -Penable-tests -Dtest='Print*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

generator/代码业务的针对性测试按本轮实际类名指定模块与 `-Dtest`；检查报告确有测试运行，不能把跳过视为通过。发布 package 保持跳过测试的用户偏好。

SQL：扫描新增迁移防重复/租户/逻辑删除/敏感数据，并检查 `rg -n '\$\{[^}]+\}' <新增迁移路径>` 无业务占位符。XML 解析、重复 Flyway 版本检查随迁移变更执行。

浏览器：使用受控临时前端验证环境和合成 API 数据，具体脚本在 T08/M1 建立后加入本文件；打印结果使用真实浏览器输出或 PDF 检查，不以 jsdom 元素存在断言代替。记录启动/停止的服务。

## 5. 真实联调交付清单（用户执行）

1. 备份后运行新增迁移，确认 forge_schema_history 成功且表/索引/权限资源正确。
2. 分配设计、发布、普通打印使用者角色；准备同租户和跨租户合成记录。
3. 发布模板和应用，实际列表/详情打印；校验草稿更新不影响已发布版本。
4. 完成主子表审批、会签、退回重提，检查待办/已办/我发起打印范围、签名和实例归属。
5. 验证未授权字段/子表列/文件及伪造请求被拒绝。
6. 实际浏览器另存 PDF 与打印机出纸；核对行数、页数、金额、页边距和签名。
7. 停用/应用回滚后复验；填入版本号、环境、结果与证据路径。

## 6. 本轮增量：2026-09-18 提案文档

- 范围：proposal/spec/design/tasks/test-spec/execution-log。
- 应执行：Markdown 本地链接、现状引用路径、任务 ID/依赖、状态一致性、空白问题检查。
- Git diff：工作区缺少 Git 元数据，不创建 .git；对六份新增文档逐文件执行 `git diff --no-index --check -- /dev/null <文档>`。退出码 1 仅表示新增差异，空白诊断输出为空且无其它错误时通过。
- 不执行：依赖安装、构建、单测、浏览器、服务、迁移、打印机测试，原因是本轮无业务实现。

## 7. 本轮增量：M1 打印协议与运行时

- 单测范围：`src/components/print/`，目前 35 项；协议、绑定、资源、分页、渲染、iframe 生命周期。
- 资源清理测试发现 Set.forEach 透传额外参数，已修正 URL 回收适配；尺寸测试改用浮点容差。
- T03 加 style.js 共享 CSS；T04 加 codes.js 复用已安装编码库；T06 加 prepare.js 分离测量前绑定；渲染行为测试作为 T03/T04 的共同验证文件。
- T08 增加 verification 独立合成入口，不修改业务路由，不启动 Admin/Flow。
- 本机 v20.19.0 不存在，实际可用 Node v24.21.0。用现有依赖直接运行本地 Vitest/ESLint/Vite 入口，避免 pnpm 自动替换依赖。

```bash
# 工作目录 forge-admin-ui
source ~/.nvm/nvm.sh && nvm use v24.21.0
node node_modules/vitest/vitest.mjs run src/components/print
node node_modules/eslint/bin/eslint.js src/components/print
node --max-old-space-size=8192 node_modules/vite/bin/vite.js build
```

最终增量：8 文件 39 项通过，增加字体失败、预览卸载后的异步打印取消、打印会话取消、连续 keepWithNext 约束。ESLint 无错误、无警告。主项目构建与独立打印验证入口构建通过，具体输出见 execution-log。

实际浏览器：0/10/100/500 行分别为 1/1/4/17 页，行顺序和数量一致；100/500 行隔离打印 DOM 无溢出且 iframe 释放后为 0。字体不存在、图片损坏和超高行返回预期错误；按钮调用输出 DIALOG_OPENED 客户端事件。证据见 [verification/browser-results.json](verification/browser-results.json)。

仍未执行：保存 PDF、物理打印机、罕见字字形逐项检查、真实数据/权限/数据库/流程 E2E。目标已纠正为现有 forge-admin Git 仓库，不再需要初始化确认；复验和提交结果见后续追加，M2–M6 未开始。

## 8. 本轮增量：目标纠正与 Forge 仓库复验

- 执行目录：`/Users/mini32g/Desktop/project/forge-admin/forge-admin-ui`。
- 相同 M1 代码在目标依赖环境重新运行：8 文件 39 项测试通过；ESLint 无错误/警告。
- 主项目生产构建通过；独立验证入口构建通过，避免未接入路由的打印源码漏检。
- 目标浏览器验证：100 行 4 页、500 行 17 页；隔离输出 500 行，无溢出，3 图片就绪，释放后 iframe 为 0。三类故障按预期阻断。
- 前序不自动初始化 Git 的疑问已解除：正确项目有现成 Git，本地分支 `codex/forge-native-print`。SDD 文档提交为 `bc72aafc`，M1 代码随后单独提交，禁止 push。
- 未执行数据库、真实流程、物理打印或保存 PDF；后续阶段保持未开始。

## 9. M2 增量验证计划

- 先编写设计器状态行为测试并记录 RED，再实现命令/历史和组件。
- 覆盖缩放坐标、组合边界、拖动单次撤销、取消、复制唯一 ID、无效导入原子性、异步保存期间继续编辑、失败保存不清 dirty、卸载事件清理。
- 浏览器验证拖动/框选/属性/区块顺序/保存恢复/预览/明暗主题，继续使用独立合成入口，不连接真实服务。
- 本轮只扩大打印模块测试范围；执行目标 ESLint、主项目构建和验证入口构建。

M2 最终结果：10 文件 58 项通过（M1 39 + M2 19）；状态历史、手势缩放/取消/卸载、框选键盘、复制/无效导入/异步保存/存储失败/字段失效/表头合并与合计撤销已覆盖。区块 ID 重名回归先 RED 后 GREEN。ESLint、主项目和合成入口构建通过。浏览器增量及未执行范围见 execution-log 与 verification/browser-results-m2.json。

## 10. M3a 增量验证计划

- 先新增协议行为用例再实现：合成完整模板、零/假/null、大数、顺序无关哈希、未知/重复键、脚本属性、原型路径、外部图片、越界元素、页码上下文、表格合并和尺寸、1MiB/数量限制。
- DTO 使用 Jakarta Validator 验证必填、ID/revision/枚举/长度；MyBatis 解析真实 XML 检查绑定参数与租户/删除/CAS/行锁条件；迁移静态核对四表审计字段、活动唯一键、版本永久唯一、字典与权限防重复、无自动角色授予。
- 后端目标单测必须启用 -Penable-tests；编译打印模块并尝试 Admin 聚合 package -DskipTests。不启动 Admin/Flow/MySQL/Redis，不实跑迁移，真实事务竞争/租户拦截/DDL 留用户后续验收。
- 本机没有 Java/Maven，临时下载官方发行版并校验摘要，仅 process-local JAVA_HOME/PATH 与临时 Maven 缓存，不修改系统或提交工具二进制。

M3a 增强：H2 MySQL 模式加载同一 V1.0.168 DDL，仅去除 ENGINE/CHARSET/COLLATE 后运行 Mapper 行为测试；不会据此宣称 MySQL Flyway 已执行或已验证租户拦截器。

### M3a 最终结果

- Java：7 个测试类、72 项、0 失败/错误/跳过；包含 29 项跨语言共享协议样例、5 项 H2 Mapper 行为测试。使用根 enable-tests profile，确认 Surefire 实际运行。
- 前端兼容：`source ~/.nvm/nvm.sh && nvm use v24.21.0 && node code-copilot/changes/forge-native-print/verification/protocol-compatibility.mjs`，29/29。服务端额外拒绝未使用属性中的非法格式，与设计中严格边界一致。
- 构建：Java 17，打印模块及依赖测试成功；`mvn -pl forge-admin-server -am package -DskipTests` 串行聚合 46 模块通过（本机实际命令另有临时 Maven settings，见 execution-log）。
- 静态：4 个 Mapper XML 与 5 个 POM 解析、Flyway 版本唯一性、V1.0.168/V1.0.169 无业务占位符、空白检查通过。
- 不扩大前端页面回归：本轮未改任何 UI 源码，独立构建实际前端协议模块验证兼容；M1/M2 页面和 58 项既有测试没有重跑，沿用上一阶段证据。
- 未执行真实 MySQL/Flyway、Admin/Flow 启动、API/低代码/流程 E2E、浏览器打印、PDF 或物理打印。详见 verification/m3a-results.json。
