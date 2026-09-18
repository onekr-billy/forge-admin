# 执行记录

> 前序提案及 M1 实现误落在 `/Users/mini32g/Desktop/project/lawhub`。下方原始记录保留真实路径和结果，不作为 forge-admin 仓库复验结果。2026-09-18 用户纠正目标为 `/Users/mini32g/Desktop/project/forge-admin`；后续以文末纠正记录为准。

## 2026-09-18：SDD 提案与规划

### 输入与范围

- 用户明确要求参考 hiprint 自主实现打印，与现有 ForgeAdmin 低代码/流程结合，并按 SDD 先提案、规划任务、再编码。
- 创建 `forge-native-print` 独立变更，保留其它进行中变更。
- 本轮只产生六份文档：proposal.md、spec.md、design.md、tasks.md、test-spec.md、execution-log.md。
- 未修改业务前后端、package.json、POM、SQL、现有设计器或流程配置。

### 已读取规则与调查依据

- 根 AGENTS.md、code-copilot/AGENTS.md、用户偏好/踩坑索引（本会话已读）。
- `code-copilot/agents/copilot-prompt.md`：No Spec No Code、提案 HARD-GATE、/apply 前置条件。
- `code-copilot/rules/automated-testing-standard.md`、coding-style.md、前端 DESIGN.md。
- 流程开发 Skill 及低代码/采购示例参考（本会话前序分析已读）。
- 当前低代码字段/主子表详情、业务动作、流程表单上下文、历史签名、应用发布与协议快照实现。
- 现有 `lowcode-designer-unification` Spec/Tasks，避免与已有物料统一方向冲突。

### 命令与观察

1. `git status --short`：返回 `fatal: not a git repository`；根目录及前后端目录未发现 .git。未尝试初始化或自动提交。
2. `cat forge-docs/guide/sdd-workflow.md`：文件不存在。随后使用当前存在的 `code-copilot/agents/copilot-prompt.md` 与根规则作为 SDD 依据；未伪造或补写旧文档。
3. `rg --files` 搜索命令/模板：当前未找到项目内 `/propose`、`/apply` 命令文件，采用仓库已有 Spec/Tasks/Test/Log 结构并补 proposal/design。
4. `wc -l`：AiCrudPage.vue 6785 行、todo.vue 2465 行、FlowReadonlyFormPanel.vue 423 行、应用工作台入口 534 行；随后确认 FlowTaskDetailShell.vue 824 行。任务中登记公共入口复用/必要拆分条件。
5. package.json：当前 Vue 3/Naive UI/Pinia/Vite 8，已有 jsbarcode/qrcode-vue3；本轮未安装依赖。
6. 根 POM/插件 POM：打印插件尚不存在，design 中新增目录/类均标为拟实施；避免把未来接口描述为现状。

### 方案落点

- 首期自主协议/排版/设计器、模板版本、主子表与流程单据浏览器打印。
- 使用当前已保存授权数据；历史业务快照/后台归档/静默打印另列后续。
- 模板版本随应用发布固定；业务权限、节点字段与子表列权限在服务端落实。
- 新增权限与模板状态明确标记人工审查，暂未进入 /apply。

### 验证结果

首轮执行临时 Python 文档检查：本地链接、任务 ID/依赖和 15 处现状引用路径均通过；`git diff --no-index --check -- /dev/null <文档>` 发现 Markdown 双空格换行被计为 trailing whitespace。已改为显式引用空行；同时修正检查器对 no-index 退出码 1（文件存在差异）的判断，只有空白诊断输出/异常退出码视为失败。

最终复查通过（临时 Python 检查，退出码 0）：

```text
Documents: 6
Tasks: 51 total; 46 implementation/verification; 2 completed documentation tasks
Requirements covered: 16/16
Dependency graph: 40 task rows, no cycles
PASS: local links, whitespace, IDs, dependencies, task status and requirement coverage
```

- 六份 Markdown 的本地链接存在，逐文件 no-index whitespace 检查无诊断。
- 任务 ID 唯一、依赖目标存在、已列依赖无环；运行 DTO 前置顺序固定为 T25 → T27a → T26 → T27b。
- 只勾选 D00/D01 文档任务；D02 审查和所有编码/验收任务均未勾选。
- F01–F16 均有测试矩阵对应项；现状证据 15 个文件路径在首轮检查中均存在。
- 为代码 Review 和最终联调保留独立任务；不把本轮文档检查当成实现或业务验收。

未执行构建/业务测试，原因：本轮只有提案文档，首期实现尚未开始。

### 环境与清理

- 未启动任何服务、浏览器或打印会话；无本轮服务需要清理。
- 未连接数据库、Redis，未执行 Flyway；未修改桌面 hiprint 参考仓库。
- 未提交、推送或变更其它项目文件。

### 下一步

完成文档一致性校验，交付具体首期提案与任务供审查。收到首期范围与权限/发布方案确认后，将 Spec 状态更新为 implementing，再从 D02/T01 开始执行；不得提前勾选代码任务。

## 2026-09-18：开始 /apply，M1 增量实施

- 用户确认开始编码并要求分阶段 commit、不 push，D02 已完成，Spec 状态 implementing。
- 核对根、UI、Server 目录均无 `.git`。已异步询问在当前目录初始化本地 Git 或提供已有仓库路径，尚未收到回复；未初始化、未 commit、未 push。
- 开始前六份文档备份到 `/private/tmp/forge-native-print-20260918-pre-apply/`。
- Node 偏好 v20.19.0 当前未安装，已核实实际可用 v24.21.0，未安装依赖或替换 node_modules。
- T01/T02：协议白名单、毫米单位、受控路径、金额定点转换与目录校验；T03/T04：共用纯文本/图片/形状/表格/编码渲染器。
- T05：字体/鉴权图片/编码准备、10 秒时限、取消和 URL 释放；session 缓存、真实 DOM 测量。
- T06/T07：有序区块、文本按行续页、表格整行分页/重复表头/末尾合计/页码及保护上限。
- T08：不可变分页树、隔离 iframe、打印对话框事件、预览取消和错误展示；浏览器验证进行中。

### RED / GREEN 实际记录

- T01、T02、T05、T06/T07、T08 均先新增目标测试，再执行得到缺少实现模块的 RED，随后实现并复跑。
- T01 首轮 9/10 通过，毫米换算浮点精度不适合严格等号，改为 10 位容差后 10/10。
- T02 累计 16/16；渲染行为累计 18/18；资源失败清理回归修正后分页累计 32/32。
- 22:58 本机执行 `node node_modules/vitest/vitest.mjs run src/components/print`：7 文件、35 项全部通过，耗时 801ms。
- ESLint 初轮发现单行多语句，按规范分行修正；最终复查待追加。
- 未执行真实业务 API、数据库、Flyway、Flow 联调；未将这些测试标记为通过。

### M1 阶段出口结果

- 最终单测命令：工作目录 `lawhub-admin-ui`，`source ~/.nvm/nvm.sh && nvm use v24.21.0 && node node_modules/vitest/vitest.mjs run src/components/print`。
- 23:10 结果：8 文件、39 项通过。新增用例覆盖预览卸载过程中打印会话完成、准备完成后取消、显式字体不可用和连续 keepWithNext。
- 最终 ESLint：`node node_modules/eslint/bin/eslint.js src/components/print`，退出码 0，无错误/警告。此前最后一个测试回调单行多语句已分行修正。
- 主项目构建：`node --max-old-space-size=8192 node_modules/vite/bin/vite.js build`，退出码 0，9365 模块，38.09s。现有 Vite native config 导入提示和两条存量 CSS // 注释警告保留，未扩大修改范围。日志 `/private/tmp/forge-print-m1-build.log`。
- 独立打印验证入口构建：根目录 `node code-copilot/changes/forge-native-print/verification/serve.mjs --build`，退出码 0，最终 2867 模块，339ms。此入口明确覆盖尚未接入主应用路由的 M1 源码，不能用主应用构建代替。日志 `/private/tmp/forge-print-m1-verification-build.log`。安装依赖实际运行版本为 Vite 8.2.1，package.json 范围为 ^8.0.10。
- 合成验证服务只监听 127.0.0.1:4318；sandbox 初次监听被 EPERM 拦截，经工具授权成功启动，没有连接真实后端。
- Chromium 153：0/10/100/500 行得到 1/1/4/17 页，无明细重复、遗漏、顺序错误、单元格溢出或正文越界。100/500 行独立打印文档为 4/17 页，500 行检查 1566 单元格、3 图片，释放后 iframe 数量为 0。
- 缺少字体、损坏图片、超高明细分别得到 FONT_UNAVAILABLE、RESOURCE_FAILED、ELEMENT_TOO_TALL，打印被阻止；恢复正常数据可重新准备。
- 已查看 10 行预览截图；实际点击打印按钮收到 `{result:DIALOG_OPENED,pageCount:1}`，只作为客户端调用事件，不等同系统对话框已显示、保存 PDF 或物理出纸。
- 证据：`verification/browser-results.json`；复跑步骤：`verification/README.md`。
- T01–T08 本阶段完成；M2–M6 未开始。没有修改 package.json、lockfile、后端、SQL 或低代码/流程业务入口。
- 同工作区存在近期其它法律业务文件变动，非本次修改；没有加入任何提交范围。由于无 Git 元数据，不能据此重建或声称拥有其变更历史。
- 阶段 commit 仍待用户确认初始化当前 Git 或提供正确仓库位置。未初始化仓库，未 commit，未设置远程，未 push。
- 已关闭本次浏览器页并停止本次 Vite 进程（Ctrl+C，退出码 130）；未停止其它服务。验证入口 cacheDir 改为 `/private/tmp/forge-print-verification-cache`，清理本轮生成的 verification/.vite 缓存，避免提交第三方预构建产物。
- 空白检查首轮误包含 Vite 缓存中的第三方 CSS 字符串，清理后按实际源码/文档复查；最大新增 SFC 为 PrintPreview.vue 182 行。

## 2026-09-18：纠正目标仓库至 forge-admin

- 用户明确指出目标为 `/Users/mini32g/Desktop/project/forge-admin/`。前轮按会话 cwd 写入 LawHub 是助手目录判断错误。
- Forge 已有 Git，初始分支 main，初始工作区仅 `.DS_Store` 有变动；不初始化 Git、不将该既有改动纳入提交。
- 只复制本次新增的 `src/components/print/` 与 `code-copilot/changes/forge-native-print/`，目标两个目录此前均不存在。其余 LawHub 文件不迁入。
- 当前设计/任务/验收命令调整为 forge-admin-ui、forge-server 和 com.mdframe.forge；前序原始执行记录保留，避免把来源仓库结果当成目标仓库结果。
- 复验、阶段提交与来源目录清理结果待追加；本轮继续遵守不 push。

### Forge 目标仓库复验与提交

- 分支：`codex/forge-native-print`，从原 main 建立；SDD 文档提交 `bc72aafc`（6 文件）。
- 本轮读取目标 AGENTS.md、code-copilot/AGENTS.md、DESIGN.md、用户偏好、测试标准和 SDD 规则，核验 Spec 中目标目录与主子表/设计预览/动作接口实际存在。
- 在 `forge-admin-ui` 运行 `source ~/.nvm/nvm.sh && nvm use v24.21.0 && node node_modules/vitest/vitest.mjs run src/components/print`：8 文件 39 项通过，23:23 执行，1.32s。
- 同目录 `node node_modules/eslint/bin/eslint.js src/components/print`：退出码 0，无警告。
- 同目录 `node --max-old-space-size=8192 node_modules/vite/bin/vite.js build`：退出码 0；原有 Vite 导入兼容提示与 CSS 注释警告未扩大修改。日志 `/private/tmp/forge-native-print-target-build.log`。
- 根目录 `node code-copilot/changes/forge-native-print/verification/serve.mjs --build`：退出码 0，产物位于临时目录；日志 `/private/tmp/forge-native-print-target-verification-build.log`。
- 本轮验证服务仅监听 127.0.0.1:4318；没有启动 Admin/Flow/数据库。Chromium 153 的目标复验证据写入 `verification/browser-results-forge-admin.json`。
- 源码 36 文件逐字节迁移；验证脚本路径与设计中的目录/包名按目标工程修正。目标依赖文件、lockfile、现有页面、后端及迁移均未修改。
- M1 阶段提交覆盖打印模块、合成验证入口和更新后的 SDD 进度，`.DS_Store` 不进入提交；M2–M6 仍未开始。

- 迁移后逐字节核对 36 个源文件完全一致；误写的 LawHub 打印模块和 SDD 目录已移至 `/private/tmp/forge-native-print-misplaced-backup-20260918/{print,sdd}`，不再留在 LawHub 活跃源码/变更目录中，未修改其它 LawHub 文件。
- 已关闭本次 Forge 浏览器标签并 Ctrl+C 停止本次 Vite 进程（退出码 130）。目标主项目构建为 9275 模块、37.52s；独立打印构建为 2867 模块、1.70s。
- 最终提交前执行 `git diff --cached --check`，只允许 `forge-admin-ui/src/components/print/` 和当前 SDD 目录进入 M1 提交。

## 2026-09-18 23:35 — 2026-09-19 00:05：M2 原生打印设计器

### 范围与实现

- 目标始终为 `/Users/mini32g/Desktop/project/forge-admin`，分支 `codex/forge-native-print`；延续用户“只 commit、不 push”。未修改 LawHub，未将既有 `.DS_Store` 加入提交。
- 编码前读取目标仓库规范、DESIGN、用户偏好和测试标准，复用 M1 验证基线，在 tasks 中细分 T10/T12/T13，明确 M2 本地草稿边界。
- T09–T13：Pinia 状态、受限历史、缩放拖动、组合边界、框选/多选、复制/粘贴、删除/对齐、物料/字段拖入、区块排序、纸张/元素/表格属性、多行合并表头和合计、工作台、草稿/协议往返、未保存离开保护。
- 新增 SFC 最大 277 行；共享设计状态均在 Pinia，面板无多层 props/emit 透传。没有引入 hiprint 代码/依赖，没有修改 package.json 或 lockfile。
- M2 保存明确为本地模板草稿；真实服务端版本和低代码/流程 Provider 均未实现，不把合成演示视为业务接入。

### RED / GREEN 和复查修复

- 23:36 先写 history.spec，因未实现 store 得到 RED；实现后 8 项通过。首轮测试 import 多退一级，同时纠正为实际 src/stores 路径。
- 23:46 工作台行为测试先得到缺少 PrintDesigner/draftStorage 的 RED。实现时修复 Vue 插值中嵌套双花括号引发的模板解析错误；13 项增量测试通过。
- 后续补框选/键盘、非法纸张调整、字段失效、存储配额失败、表头合并拆分与合计撤销。
- 00:00 复查新增“正文区块 ID 等于 header”行为测试，实际失败；正文编辑键改为 section:<id> 后通过，协议内容不变。
- 独立验证入口首次误用 Pinia 物理路径与别名混合加载，出现两个 Pinia 实例；统一通过别名导入后重新加载验证通过。此问题只在新验证入口，不涉及现有主应用。
- 修复分数坐标在边界四舍五入后的潜在越界；缩放手势遵循鼠标开始时比例。预览弹窗限制内容区高度并独立滚动。

### 最终验证命令与结果

工作目录 `/Users/mini32g/Desktop/project/forge-admin/forge-admin-ui`，均先执行 `source ~/.nvm/nvm.sh && nvm use v24.21.0`：

1. `node node_modules/vitest/vitest.mjs run src/components/print`：00:01，10 文件 58 项通过，1.95s；包含 M1 39 项和 M2 19 项。
2. `node node_modules/eslint/bin/eslint.js src/components/print/designer src/stores/print src/views/print --fix`：退出码 0，最终无诊断；提交前再只读检查整个打印范围。
3. `node --max-old-space-size=8192 node_modules/vite/bin/vite.js build`：最终退出码 0，9339 模块，39.53s；日志 `/private/tmp/forge-print-m2-final-build.log`。现有 Vite native config 提示和存量 CSS 注释警告仍在，未修改无关文件。
4. 根目录 `node code-copilot/changes/forge-native-print/verification/serve.mjs --build`：退出码 0，2909 模块，353ms，临时产物。合成入口把整个组件栈打在一起，出现 chunk >500KB 提示；主应用使用页面分包，未为该验证提示调整业务构建。日志 `/private/tmp/forge-print-m2-verification-build.log`。

### 浏览器阶段证据

- `http://127.0.0.1:4318/?designer`，Chromium，合成 100 行数据。
- 80% 缩放拖动 60/24 px → x/y 19.844/7.938 mm；一条历史，撤销恢复原位。
- Shift 选中两项，方向键让 x 从 0/80 变为 1/81；框选实际选中 barcode/qrcode/synthetic-image 三项。
- 50% 缩放调整二维码 19 px：宽 25→35.054 mm，高度被区块限制到 30 mm；撤销恢复 25×25。
- 主字段拖入固定区块自动 FIELD 绑定，超出落点被夹到 x=145/y=20；复制生成新 ID，偏移 3 mm；恢复本地草稿保持先前位置。
- 新建时出现未保存提示，“继续编辑”保留模板；同一模板 JSON 校验导入后 dirty=false、无新增历史。切换失效字段禁用预览并定位对应表格，恢复目录后可预览。
- 100 行真实预览 4 页、3 图片 complete=true 且自然尺寸非零；与 M1 使用同一 PrintPreview。纸张保持白底，明暗主题、1280/960 px 宽窗口已目视截图检查。
- 添加表头行成功、区块上/下移动及两步撤销恢复原顺序；最后修改后的内部区块键选择与导入往返再次验证通过。
- 浏览器工具虚拟剪贴板阻止直接模拟粘贴快捷键，未将这次工具调用记为通过；浏览器的复制/粘贴按钮与单测复制 ID/撤销路径通过。没有操作用户系统剪贴板。
- 结构化证据 `verification/browser-results-m2.json`；截图在本次工具记录中已查看，没有伪造截图文件。

### 清理与未执行项

- 本次仅启动验证 Vite，PID 45377，监听 127.0.0.1:4318。最终 Ctrl+C 退出码 130；浏览器标签已关闭，临时 viewport 已 reset。
- 未启动 Admin/Flow/MySQL/Redis，未执行 Flyway、真实接口/权限/业务流程 E2E、保存 PDF 或物理打印。
- M2 完成阶段出口，M3–M6 保持未开始；新打印功能还不能宣称已接入真实业务。
- 提交只包含本阶段打印源码/测试与对应 SDD 文档、合成验证入口；commit 标题为 `[forge-native-print] 完成 M2 原生打印设计器与草稿编辑`，不 push。

## 2026-09-19：M3a 后端持久化与协议验证

### 范围与阶段拆分

- 正确目录 `/Users/mini32g/Desktop/project/forge-admin`，分支 `codex/forge-native-print`；原 `.DS_Store` 不改动、不提交。只做本地 commit，未 push。
- 执行前复用根 AGENTS、code-copilot/AGENTS、preferences、pitfalls/backend、automated-testing-standard、当前 SDD；应用 `.agents/skills/forge-codegen-crud/SKILL.md` 的实体、SQL、字典权限约定。先补 T14 的 BOM 文件与 T21 拆分，再编码。
- M3a 单独交付 T14–T21；M3b T22–T28 仍待实现。没有 Controller、Provider 运行接口或真实前端保存，不将本阶段写成全部 M3 完成。
- POM 单向 generator → print → 技术 starter，Admin 显式聚合；打印插件只有技术依赖，H2 为 test scope。没有 hiprint 源码/依赖或新的生产端打印库。
- 新迁移 V1.0.168 建四表，V1.0.169 建 5 个 sys_print_* 字典和四项权限；不向角色自动授权、不创建未完成的列表菜单。没有执行迁移，没有修改历史 SQL。
- Mapper 使用明确租户/逻辑删除条件、CAS 修订号、模板行锁、版本归属验证与审计 actor 限定；版本仅 insert/select，历史最大版本号包含删除记录以保持永久唯一。默认绑定应用行锁和应用快照引用保护留 T24/T22 的服务/SPI 完成。
- 文档模型与验证器拆成 8 个小类；统一技术限制，UTF-8 1MiB/深度/重复 JSON 键/尾随 JSON/字段与样式白名单/图片来源/几何/表格跨度；输出规范化 JSON 与 SHA-256，不执行表达式。失败诊断不记录原始模板值或 parser 原文。

### RED / GREEN 与修复

- 先写 PrintProtocolValidatorTest/合成模板；在实现缺失时用 Java 17 编译 PrintProtocolSmokeTest，确实报 PrintProtocolValidator 不存在（3 个错误，退出码 1），记录 `/private/tmp/forge-print-protocol-red.log`；再实现验证器。
- 首次协议编译发现 Java 正则字符串转义错误，修正后独立 javac 通过；未将此轮失败当成功。
- 冷缓存 Maven 构建启动后才加入 H2 测试依赖，首次 reactor 使用旧 POM 快照，testCompile 缺 org.h2.jdbcx；重新执行完整命令加载当前 POM 后通过。独立 JUnit 先后 34/43 项通过，最终以正式 Maven 的 72 项报告为准。
- 审查补回发布事务 rollback、错模板版本、绑定修订失效、全局元素/UTF-8/内联图片限制和 29 组共享兼容样例；按 coding-style 用 AST 给所有新增 Java 控制语句补齐大括号，再复跑测试。
- 首次 Admin `-T 2` 聚合因 Maven resolver `Could not acquire lock(s)` 失败；串行重试成功，不归因为打印源码。没有为工具故障更改产品代码、POM 版本或降低测试断言。

### 实际验证

工具：本机无可用 Java/Maven，临时下载 [Adoptium Java 17](https://adoptium.net/installation/archives) 与 [Apache Maven 3.9.9](https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/)，校验 SHA-256/SHA-512 后使用。JDK 17.0.20.1+1，Maven 3.9.9；均位于 `/private/tmp/forge-print-toolchain`，未安装系统软件或改 shell 配置。初期下载超时后续传完成，备用 Corretto 下载已停止。

后端工作目录 `forge-server`，先 `source /private/tmp/forge-print-toolchain/env.sh`；临时 settings 仅使用 Maven Central 和 `/private/tmp/forge-print-maven-repository` 隔离缓存：

1. `mvn -s /private/tmp/forge-print-maven-settings.xml -B -ntp -pl forge-framework/forge-plugin-parent/forge-plugin-print -am test -Penable-tests -Dtest='Print*Test' -Dsurefire.failIfNoSpecifiedTests=false`：最终 BUILD SUCCESS，72 tests，0 failures/errors/skipped。日志 `/private/tmp/forge-print-m3-tests-final.log`。
2. `mvn -s /private/tmp/forge-print-maven-settings.xml -B -ntp -e -pl forge-admin-server -am package -DskipTests`：串行 46 模块 BUILD SUCCESS，含 Print、Generator、Admin，耗时见结构化证据。日志 `/private/tmp/forge-print-m3-admin-package-serial.log`。Package 按用户偏好跳过测试，不能用它替代第 1 项。
3. 根目录 Node v24.21.0 执行 `node code-copilot/changes/forge-native-print/verification/protocol-compatibility.mjs`：前端实际协议 bundle 验证 29/29，临时 bundle 已自动清理；未启动端口/浏览器。共享样例来自插件 test/resources/print。
4. XML/POM 静态解析、迁移版本唯一、Flyway placeholder、Git whitespace 检查通过；提交前再次核对 staged allowlist。

已有 auth 两处 Lombok @Builder 默认值警告保留，未扩大改动。H2 用 MySQL 模式执行同一 DDL，去掉 ENGINE/CHARSET/COLLATE；证明 SQL 边界行为，不代表 MySQL、Flyway 或真实租户拦截器已验收。没有生成或提交包含环境属性的原始测试报告，只提交计数与检查结果。

### 两阶段自审与未执行项

- Spec 合规：T14–T21 与已补拆分匹配；低代码/流程权限、不可变应用引用、prepare/运行权限和前端 API 持久化仍保持未完成，不能通过本阶段绕开这些前置条件。
- 代码质量：复核 4 表索引、删除墓碑、版本永不复用、DTO 固定字段、SQL 无动态拼接、协议未知字段拒绝、异常隐私和资源边界；审查改动后的 72 项全部通过。
- 未启动 Admin/Flow/MySQL/Redis、未执行真实数据库迁移或接口 E2E，未重做 M1/M2 浏览器验证、PDF/打印机。没有修改 LawHub，也没有停止其它用户进程。
- 阶段提交名 `[forge-native-print] 完成 M3a 打印持久化与协议校验`；后续从 T22–T28 开始，不跳到真实业务接入。


## 2026-09-19：M3b 模板事务、授权编排与前端持久化

### 范围与规范

- 持续在 `/Users/mini32g/Desktop/project/forge-admin`、`codex/forge-native-print` 实施；未修改 LawHub，既有 `.DS_Store` 不纳入提交。用户仍授权分阶段 commit、禁止 push。
- 复用既有 Spec/tasks/test-spec/log、根及 code-copilot AGENTS、测试标准、UI DESIGN 与 Forge CRUD Skill；编码前补 T25/T22/T24/T26/T28 子任务和 SPI 契约。当前原生模块不使用生成器 POST-safe CRUD，遵循已审查 REST Spec。
- 模板草稿 CAS、复制、发布不可变版本、同内容重复发布复用版本、停用、逻辑删除及引用保护均落实。写入统一应用锁→模板锁；绑定默认项在空集合并发下也由应用行锁串行化。
- 设计依赖打印设计权限与应用/来源授权；运行仅依赖 print:execute 与 Provider 的应用/记录/场景授权。Provider 返回可信发布版本清单，不回退最新草稿或实时设计绑定。默认没有真实 Provider，503 拒绝。
- prepare 只返回模板使用且目录允许的字段。图片元素必须绑定 IMAGE 目录字段；资源别名归一 fileId 后再授权。数据单集合 500 行、单文本 100000 字符、JSON 流式限长 4MiB。流程授权结果必须带已解析 processRunId。打印审计仅记录 PREPARED/DIALOG_OPENED/FAILED 元数据；所有端点关闭请求/响应正文日志。
- 前端两个 Pinia store 管理异步版本和请求代次，服务端保存不落 localStorage；保存时的新编辑保留，409 不丢数据，关闭预览清理单据。模板/场景/状态展示使用字典，新增页面与面板均低于 800 行。
- V1.0.170 仅注册 3 个隐藏页面，NOT EXISTS 防重复、tenant_id=1，不自动给角色赋权。未执行迁移。正式业务入口和应用发布集成仍在 M4。

### 测试与修复记录

- RED：PrintProviderRegistryTest 在 Registry 未实现时 testCompile 明确失败；实现后通过。后续逐项补事务、HTTP、前端竞态及本轮审查边界用例。
- 前端首次回归发现旧设计器测试仍点击服务端模式已移除的“新建”；按新行为断言按钮不存在并直接验证 canLeave，另外补只改名称的离开保护。首轮格式检查发现同一行多语句，按 AST 拆分后定向 lint 通过。
- 浏览器验证 mock 起初缺少 getDictData 导出，补齐字典缓存契约后刷新通过。首次合成样例因未安装宋体而正确禁用打印，正向 HTTP 样例改用 Arial 后渲染 1 页正常。
- 自审补图片字段类型检查，避免普通文本字段绕过运行资源授权；补资源别名、总输出大小流式限制、并发首个默认绑定与流程 run 身份测试。按 SDD 将分页入口统一为 `/print/templates/page`。
- 一次单独 Registry 命令误在仓库根执行，reactor 项目定位失败；更正工作目录 `forge-server` 后，包含真实 Spring 空 Provider 列表构造注入的完整 104 项测试通过。这次命令失败不计为通过。

### 最终验证命令与结果

后端先加载 `/private/tmp/forge-print-toolchain/env.sh`，Java 17 / Maven 3.9.9 及隔离 Maven 仓库沿用 M3a：

1. `mvn -s /private/tmp/forge-print-maven-settings.xml -B -ntp -pl forge-framework/forge-plugin-parent/forge-plugin-print -am test -Penable-tests -Dtest='Print*Test' -Dsurefire.failIfNoSpecifiedTests=false`：104 tests、0 failures/errors/skipped，BUILD SUCCESS。日志 `/private/tmp/forge-print-m3b-java.log`。
2. `mvn -s /private/tmp/forge-print-maven-settings.xml -B -ntp -pl forge-admin-server -am package -DskipTests`：46 模块成功；日志 `/private/tmp/forge-print-m3b-admin-build.log`。此命令不替代实际单测。
3. UI Node v24.21.0：`node node_modules/vitest/vitest.mjs run src/components/print src/stores/print/__tests__ src/api/__tests__/print.spec.js`：12 files、71 tests 全通过；分页路径最终修正后，`src/api/__tests__/print.spec.js` 2/2 再次通过。
4. 对全部本轮前端改动运行项目 ESLint，0 errors/warnings；日志 `/private/tmp/forge-print-m3b-eslint.log` 和最终 API 定向日志 `/private/tmp/forge-print-m3b-api-eslint.log`。
5. `node --max-old-space-size=8192 node_modules/vite/bin/vite.js build`：最终生产构建成功，保留既有 Rollup/Rolldown 性能/包体积提示，未关闭规则。日志 `/private/tmp/forge-print-m3b-ui-build.log`。
6. 浏览器实际执行合成 HTTP 场景，见 browser-results-m3b.json；真实 Service/Mapper/事务验证由 H2/MockMvc 完成，不混称真实业务 E2E。
7. Mapper XML、迁移版本唯一/V170 防重复与无自动角色授权、SFC 行数和 git diff --check 通过；结构化计数见 verification/m3b-results.json，不提交包含环境变量的原始 Surefire 报告。

### 两阶段自审、清理和交接

- Spec 合规：T22–T28 已完成源码及阶段出口；字段/版本/权限/审计和 UI 保存边界与设计一致。M4–M6 不勾选，整体 Spec 保持 implementing，不提前归档。
- 代码质量：无业务 Service 查询构造器、无 Controller Map 请求体、无服务循环依赖；租户/actor 来自服务端，版本和资源取自授权来源，写入 CAS 与事务回滚测试通过。新菜单不公开、不自动授予角色。
- 合成浏览器标签已关闭；本轮唯一验证服务器 127.0.0.1:4318 已 Ctrl+C（130）退出并确认无监听，没有修改 viewport。未停止其他用户进程。
- 未启动真实 Admin/Flow/MySQL/Redis，未执行 Flyway/真实鉴权加密/低代码或流程 E2E，未输出 PDF 或操作物理打印机。H2 并发结果不能替代 MySQL 锁与实际租户拦截器验收。
- 本阶段只做本地 commit，不 push；下一阶段从 M4 的真实应用授权、低代码 Provider 与发布快照集成开始。

## 2026-09-19 · M4a-1 页面身份兼容

用户要求新分支继续打印，随后指定去掉 codex 和 M4；当前分支 `forge-native-print`，从 882ff8e1 延续，未改动主分支、未 push。已有 .DS_Store 改动不纳入提交。

实际修改 PrintSourceRequest / PrintTemplateCreateDTO / PrintBindingQueryDTO、PrintTemplate / PrintBinding；V1.0.171 扩展 page_id 为 VARCHAR(128)，数字 ID 的旧 source_key 保持一致。路由解析接受工作台 page_* 标识，拒绝路径和超限值。H2 夹具先建 V168 再运行 V171 对应 ALTER；MySQL information_schema/PREPARE 防重分支未在真实库执行。

验证：Java 17/Maven 3.9.9，既有 /private/tmp/forge-print-toolchain/env.sh 和 Maven settings，`-pl forge-framework/forge-plugin-parent/forge-plugin-print -am test -Penable-tests -Dtest='Print*Test' -Dsurefire.failIfNoSpecifiedTests=false`：106 项全部通过（包括 DTO→MockMvc→事务 Service→Mapper 的字符串页面身份）。前端 Node 24.21，`vitest run src/components/print src/stores/print`：12 文件 77 项通过；目标 ESLint 首轮提示正则风格，--fix 后通过；`node --max-old-space-size=8192 node_modules/vite/bin/vite.js build` 成功，46.44 秒，保留既有构建警告。未启动任何服务/未执行真实迁移。

M4a-2/3 正在实现，不将数据 Provider、应用发布快照生成或工作台入口标为完成。
