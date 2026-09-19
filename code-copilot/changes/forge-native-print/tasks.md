# 实施任务

> 状态：implemented-pending-e2e，M1–M5 代码开发、T46/T47 设计器视觉与交互修正和自动化构建验证已完成；M6 的真实环境、PDF 和打印机验收由用户执行。禁止 push。
>
> 依据：[spec.md](spec.md)、[design.md](design.md)
>
> 执行日志：[execution-log.md](execution-log.md)；验证基线：[test-spec.md](test-spec.md)

## 执行规则

- 先完成 D02 审查，再依赖顺序执行；不以“有页面”代替数据/权限/发布闭环。
- 每个编码任务控制在 3–5 个主要文件，超出则先拆成子任务并回填；依赖配置、测试和必要文档也记录实际改动。
- 每项任务回填实际文件、命令、结果、阻塞项；勾选必须有证据。
- 已有文件与拟新增文件明确区分；下表使用规划文件名，落位前核实，变更名称或协议先反向同步文档。
- 未开始的任务不要标为失败；未执行的测试不要标为通过。
- 不自动启动真实数据库/Admin/Flow，不执行迁移。已使用现有 forge-admin Git 仓库；按用户要求分阶段 commit，禁止 push。

## 路径缩写

- U：`forge-admin-ui/src/`
- P：拟新增 `forge-server/forge-framework/forge-plugin-parent/forge-plugin-print/`
- PJ：`P/src/main/java/com/mdframe/forge/plugin/print/`
- PT：`P/src/test/java/com/mdframe/forge/plugin/print/`
- GJ：`forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/`
- GT：对应 generator 的 `src/test/java/com/mdframe/forge/plugin/generator/`
- B：现有 `forge-server/forge-business/forge-business-core/`

## D：提案、规划与审查

- [x] D00：现状调查并创建 proposal/spec/design；确认自主实现与平台衔接方向。
- [x] D01：创建 tasks/test-spec/execution-log，完成任务依赖与文档一致性校验（结果见日志）。
- [x] D02：用户于 2026-09-18 确认“开始编码吧 严格执行”，并要求分阶段 commit、不 push。

## M1：自有协议与分页闭环

阶段出口：合成单据能稳定得到分页结果并打开浏览器打印；分页失败必须可定位。

| 状态/任务 | 依赖 | 拟涉及文件（U 下） | 验收与证据 |
|---|---|---|---|
| [x] T01 协议与单位 | D02 | `components/print/protocol/{types,validate,units}.js`、`protocol/__tests__/protocol.spec.js` | 版本/元素/物理尺寸校验；缩放不改坐标；未知协议拒绝 |
| [x] T02 绑定与格式化 | T01 | `protocol/{binding,formatters,fieldCatalog}.js`、`protocol/__tests__/binding.spec.js` | 0/false/null、分→元、大整数、非法路径和数组绑定 |
| [x] T03 基础渲染器 | T02 | `renderers/{PrintText,PrintImage,PrintShape}.vue`、`renderers/{registry,style}.js` | 合成数据呈现，文本转义，不执行脚本 |
| [x] T04 表格与编码渲染器 | T03 | `renderers/{PrintTable,PrintBarcode,PrintQrcode}.vue`、`renderers/{registry,codes}.js` | 明细/合计、编码错误可见；仅复用基础编码库 |
| [x] T05 资源与测量 | T04 | `engine/{resources,fonts,measure,measurementCache}.js`、`engine/__tests__/resources.spec.js` | 字体/图片等待、超时/失败、缓存按内容/宽度/字体失效 |
| [x] T06 文本和区块分页 | T05 | `engine/{layout,textPagination,pageGeometry,prepare}.js`、`engine/__tests__/layout.spec.js` | 长文本、精确页边界、固定区块换页、超高错误 |
| [x] T07 表格分页 | T06 | `engine/{tablePagination,pageNumbers,layout}.js`、`engine/__tests__/tablePagination.spec.js` | 重复表头、合计另页、空明细、500 行/50 页上限 |
| [x] T08 预览与打印会话 | T07 | `runtime/{PrintPage,PrintPreview}.vue`、`runtime/browserPrint.js`、`runtime/__tests__/browserPrint.spec.js` | 同一分页树预览/打印、iframe/Blob 清理、不污染全局样式 |

T03/T04 共用 `renderers/__tests__/renderers.spec.js` 行为验证。T08 浏览器入口及脚本放 `verification/`，属于阶段出口验证文件。

T08 另含 `runtime/__tests__/PrintPreview.spec.js`，覆盖预览卸载时仍在准备的打印会话；8 个任务的阶段证据见 execution-log 与 verification/browser-results.json。勾选表示实现和对应阶段验证完成，不代表 Git 已提交或后续业务集成完成。

M1 必须补真实浏览器分页验证，不能只凭纯函数测试进入 M2。验证脚本/合成模板放当前变更 `verification/`，按 test-spec 记录。

## M2：设计器与编辑闭环

阶段出口：模板在自有设计器中可编辑/预览/序列化往返；此阶段合成数据演示不计为业务接入完成。

| 状态/任务 | 依赖 | 拟涉及文件（U 下） | 验收与证据 |
|---|---|---|---|
| [x] T09 Pinia 设计状态 | T08 | `stores/print/printDesignerStore.js`、`designer/{commands,history}.js`、`designer/__tests__/history.spec.js` | 选择/跨面板状态、撤销重做、拖动一次提交一次命令 |
| [x] T10 画布交互 | T09 | `designer/{PrintCanvas,PrintSelectionOverlay}.vue`、`designer/{usePrintDrag,usePrintResize}.js` | 缩放下坐标准确、多选、键盘移动、移出边界处理 |
| [x] T11 物料与字段区 | T10 | `designer/{PrintElementPalette,PrintFieldTree,PrintSectionList}.vue`、`designer/elementCatalog.js` | 主字段/明细/审批字段可拖入；区块顺序可调 |
| [x] T12 属性面板 | T11 | `designer/panels/{PaperPanel,TextPanel,TablePanel,BindingPanel}.vue` | 属性即时作用、物理单位清楚、失效字段定位；不堆单文件 |
| [x] T13 编辑工作台 | T12 | `designer/{PrintDesigner,PrintDesignerToolbar}.vue`、`views/print/designer.vue`、`designer/__tests__/PrintDesigner.spec.js` | 新建/复制/保存草稿/预览入口、未保存提示、明暗主题 |

### M2 实施拆分与保存边界（编码前补充）

- T09 增量测试覆盖历史上限、无效编辑原子拒绝、拖动取消、组合移动边界、保存并发；主要文件保持原计划。
- T10a：画布、选择层、拖动和缩放 composable；T10b：`designer/{PrintCanvasElement.vue,usePrintKeyboard.js}` 与交互测试，负责框选/键盘和元素展示。
- T11：物料与字段区、区块列表和 elementCatalog；仅字段目录允许的字段可拖入，明细字段加入对应表格。
- T12a：PaperPanel、TextPanel、BindingPanel；T12b：TablePanel、`TableBandsPanel.vue` 与 `ElementGeometryPanel.vue`，负责明细列、多行合并表头/合计绑定和元素几何尺寸。
- T13a：工作台、工具栏、页面入口、组件测试；T13b：`designer/{draftStorage,usePrintDesignerLifecycle}.js` 与验证入口，负责草稿往返与离开保护。
- M2 的“保存草稿”明确为当前浏览器本地模板草稿，只存协议，不存预览业务数据；界面明确显示本地保存。服务端模板/版本持久化仍由 T28 接入，不把本地草稿视为业务接入完成。
- 预览继续复用 M1 的真实分页；画布为区块编辑视图，实际页数以预览为准。主项目入口先以现有视图解析方式注册源码，资源菜单与权限由 M3 迁移统一落地。

M2 结果：新增 26 个源码/测试文件（设计器、Pinia 和页面），T09 的 9 项状态测试、T10–T13 的 10 项编辑行为测试通过；打印模块累计 10 文件 58 项通过。浏览器证据见 `verification/browser-results-m2.json`。本地草稿不包含运行上下文，服务端持久化未提前勾选。主项目与独立验证入口均构建通过；最大新增 SFC 为 277 行。具体命令、边界修复和服务清理见 execution-log。

## M3：模板后端、版本和数据提供方

阶段出口：合成 Provider 通过完整模板/版本/授权链路输出上下文；真实业务 Provider 在 M4/M5 接入。

| 状态/任务 | 依赖 | 拟涉及文件 | 验收与证据 |
|---|---|---|---|
| [x] T14 插件装配 | D02 | `P/pom.xml`、plugin-parent/pom.xml、BOM/pom.xml、generator/pom.xml、admin-server/pom.xml | 单向依赖、Admin 聚合编译、无 Service 环 |
| [x] T15 数据结构迁移 | T14 | 新 Flyway 迁移、PJ `entity/{PrintTemplate,PrintTemplateVersion,PrintBinding}.java` | 表/索引可重复；显式 TableLogic；当前版本号唯一 |
| [x] T16 审计实体和状态 | T15 | PJ `entity/PrintExecution.java`、`enums/{PrintDesignStatus,PrintExecutionResult,PrintScene}.java` | 状态 getCode/matches；无物理打印成功误报 |
| [x] T17 业务枚举与资源种子 | T16 | 新 Flyway 迁移、PJ `enums/PrintSourceType.java`、PT `PrintResourceContractTest.java` | sys_print_* 字典/权限 NOT EXISTS、tenant=1、无全员授权 |
| [x] T18 模板读写 Mapper | T15 | PJ `mapper/{PrintTemplateMapper,PrintTemplateVersionMapper}.java`、对应两个 Mapper XML | 查询租户/逻辑删除；CAS；唯一编码和版本约束 |
| [x] T19 绑定/执行 Mapper | T16 | PJ `mapper/{PrintBindingMapper,PrintExecutionMapper}.java`、对应两个 Mapper XML | 默认绑定事务范围、关联索引、日志不存业务正文 |
| [x] T20 模板 DTO | T18 | PJ `dto/{PrintTemplateCreateDTO,PrintTemplateUpdateDTO,PrintTemplatePublishDTO,PrintTemplateStatusDTO,PrintTemplateCopyDTO}.java` | 固定字段类型、长度/体积/修订号约束 |
| [x] T21 协议后端验证 | T20 | PJ `protocol/{PrintTemplateDocument,PrintSection,PrintElement,PrintProtocolValidator}.java`、PT `PrintProtocolValidatorTest.java` | schema 白名单、禁脚本、版本拒绝、前后端协议一致 |
| [x] T22 模板服务/API | T21 | PJ `service/{PrintTemplateService,PrintTemplateVersionService}.java`、`controller/PrintTemplateController.java`、`vo/PrintTemplateVO.java` | 草稿/复制/状态/发布，失败不污染已发布版本 |
| [x] T23 模板服务行为验证 | T22 | PT `{PrintTemplateServiceTest,PrintTemplateVersionServiceTest,PrintTemplateControllerTest}.java` | 同时编辑冲突、重复发布、被引用删除、停用旧版本 |
| [x] T24 绑定服务/API | T19,T22 | PJ `dto/{PrintBindingQueryDTO,PrintBindingSaveDTO}.java`、`service/PrintBindingService.java`、`controller/PrintBindingController.java` | 来源核验、默认唯一、应用归属不能伪造 |
| [x] T25 上下文协议/SPI | T21 | PJ `spi/{PrintDataProvider,AuthorizedPrintContext}.java`、`dto/PrintPrepareDTO.java`、`vo/{PrintContextVO,PrintFieldCatalogVO}.java` | actor/tenant 服务端获取，低代码/代码业务共用入口 |
| [x] T26 prepare 编排 | T24,T25,T27a | PJ `service/{PrintPrepareService,PrintProviderRegistry,PrintExecutionService}.java`、`controller/PrintRuntimeController.java` | 发布版本解析、使用权限与记录权限、执行事件真实性 |
| [x] T27a 运行查询/事件 DTO | T25 | PJ `dto/{PrintCatalogQueryDTO,PrintAvailableTemplatesDTO,PrintExecutionEventDTO}.java` | 固定字段类型与场景参数校验，不接受任意 Map |
| [x] T27b 运行授权验证 | T26 | PT `{PrintPrepareAuthorizationTest,PrintProviderRegistryTest,PrintExecutionServiceTest}.java` | 运行接口无设计权依赖；跨租户/伪造版本/执行事件拒绝 |
| [x] T28 模板前端持久化 | T22,T24,T27b,T13 | U `api/print.js`、`views/print/index.vue`、`runtime/PrintTemplatePicker.vue`、`stores/print/printRuntimeStore.js` | 真实 API 草稿保存/版本选择；业务枚举使用字典 |

### M3 实施拆分（编码前补充）

- M3a：T14–T21，插件依赖、迁移/实体/字典权限、Mapper、输入 DTO 和后端协议验证。单独 commit；不新增可被调用但缺少来源授权的 Controller。
- M3b：T22–T28，模板发布/授权/引用保护、Provider SPI、运行编排与前端持久化。完成后才验收 M3 完整链路。
- T14 实际包含 BOM dependencyManagement，共 5 个 POM；依赖单向 generator → print → 技术 starter。
- T17 的权限仅加入现有应用中心菜单下的权限资源，不提前加入尚未完成的模板列表菜单，不自动授予角色。
- T18/T19 另加 `PrintMapperContractTest`，加载真实 MyBatis XML 并校验租户、逻辑删除、CAS 和锁条件；MySQL 并发/索引仍待真实库验收。
- T20 另加 `PrintTemplateDtoTest` 验证 Bean Validation 的固定字段边界；schemaJson 是大小受限的协议字符串，由 T21 原样验证后转为明确模型，不能依赖 Jackson 默默忽略未知属性。
- T21a：`protocol/{PrintTemplateDocument,PrintSection,PrintElement}.java` 明确模型；`PrintProtocolValidatorTest` 和 JSON 合成样例先写后实现。
- T21b：`protocol/{PrintProtocolValidator,PrintProtocolRules,PrintTableRules,PrintValueRules}.java`，分离入口/几何/表格/绑定样式校验；异常与验证结果作为入口内的静态类型，不新增泛化框架。
- 默认绑定互斥将在 T24 对同一应用行加锁后执行（锁能力由应用侧 SPI 提供）；M3a Mapper 不声称自身已解决空集合并发插入。
- 版本 Mapper 不提供 update/delete；最大版本号查询包含已删除历史，避免版本号复用。所有业务查询显式 tenant_id 和 del_flag，审计状态更新绑定创建 actor 并使用期望状态 CAS。

局部顺序明确为 `T25 → T27a → T26 → T27b → T28`，按依赖执行，不按编号机械执行。

## M4：低代码与应用发布

阶段出口：已发布低代码应用可以打印真实授权主子表单据；草稿与运行版本隔离。

| 状态/任务 | 依赖 | 拟涉及文件 | 验收与证据 |
|---|---|---|---|
| [x] T29 低代码 Provider | T27b,T28 | GJ `service/printing/{LowcodePrintDataProvider,LowcodePrintCatalogBuilder,LowcodePrintValueAdapter}.java`、GT `service/printing/LowcodePrintDataProviderTest.java` | main/children 归一、子表列授权、公式/字典/关联/脱敏 |
| [x] T30 发布态字段校验 | T29 | GJ `service/printing/{PrintBindingValidationService,PrintMetadataResolver}.java`、GT `service/printing/PrintBindingValidationServiceTest.java` | 字段删除/改型/表单身份变更可定位，不查最新草稿代替发布态 |
| [x] T31 应用资源入口 | T28,T30 | U `views/app-center/application-workspace/{ApplicationPrintPanel,ApplicationWorkspaceNav}.vue`、现有 `views/app-center/application.[applicationCode].vue`、`components/print/designer/PrintSourceSelector.vue` | 从表单进入自动绑定，独立全屏，不新增对象选择负担 |
| [x] T32 打印动作投影 | T29,T31 | GJ `service/printing/PrintRuntimeActionProjectionService.java`、现有动作投影接入文件、U `views/print/preview.vue`、对应投影测试 | 列表行/详情均进入统一 route；不复制打印脚本 |
| [x] T33 应用快照扩展 | T30 | GJ `service/printing/PrintApplicationSnapshotContributor.java`、现有 `BusinessApplicationSnapshotService.java`/`BusinessApplicationPublishService.java`、对应测试 | 固定模板版本/引用/hash，失败无半发布，回滚恢复 |
| [x] T34 下载协议扩展 | T33 | 现有 `LowcodeProtocolSnapshotBuilder.java`、拟新增打印导出贡献器、生成依赖模板、对应导出测试 | 下载代码包含协议/模板/绑定且复用运行时，无漏字段 |

### M4 实施拆分（2026-09-19，编码前）

当前分支为 `forge-native-print`。仓库核对发现：工作台页面 ID 是字符串（如 `page_purchase`），M3 的 Long pageId 不能接入；应用版本提交与打印删除尚未共享应用行锁。先完成 M4a，再接 M4b/M4c；不将基础适配计作真实单据打印完成。

- [x] M4a-1（T29 前置）：`PrintSourceRequest`、两个来源 DTO、两个实体改用受控字符串 pageId（拆为 DTO 组与持久化组）；新增 V1.0.171 扩展两表 page_id，保持旧数字身份摘要不变。路由解析同步，补 DTO/HTTP/Mapper/路由验证。
- [x] M4a-2（T33 前置）：应用 Mapper 行锁、版本 Mapper 历史快照当前读（4 文件）；新增 `service/printing/{PrintApplicationAccessAdapter,PrintApplicationSnapshotCodec,PrintApplicationLock}.java`。应用设计权限与应用可见范围同时核验，删除检查全部保留的历史应用版本。
- [x] M4a-3（T33 前置）：新增 `PrintApplicationVersionGuard`，接入 `BusinessApplicationVersionService`；共享应用行锁下核验固定模板版本/归属/hash，失败不提交应用版本或发布指针。补服务与事务/Mapper 测试。
- [x] M4b：T29/T30/T33 余项，已发布元数据、主子表读取与字段权限、候选快照生成/发布校验。特别验证 DynamicCrudService 的子表读取后处理，不能沿用未翻译/未脱敏子表结果。
- [x] M4c：T31/T32/T34，工作台入口、运行动作、下载协议及浏览器验收；进入前落实 R01。

M4a 的 `printing` 快照协议先定义并在最终应用版本提交时守卫；候选快照生成和业务字段验证属于 M4b，M4a 不提前安装不完整 DataProvider。现存不含 printing 的应用版本兼容为空绑定。

### 存量超大组件接入条件任务

- [x] R01（M4 前检查）：确认 T32 是否能完全使用既有 route/配置路径，不改 AiCrudPage。能则记录“不适用”，不能则先拆成 R01a/R01b…，完成被修改 SFC 的合规规模与回归后再接入，不豁免根 AGENTS.md 5.14。
- [x] R02（M5 前检查）：核对 FlowTaskDetailShell/todo/started/done 实际行数与公共上下文。样式迁出后 `todo.vue` 1944 行、`started.vue` 568 行、`FlowTaskDetailShell.vue` 225 行、`done.vue` 606 行；流程打印上下文进入独立 Pinia Store，未继续通过详情组件层层透传。

R01/R02 为条件化实施检查，不得勾选后绕过拆分；如果需要的重构显著扩大范围，先更新 Spec 与任务并说明原因。

## M5：流程与代码业务

阶段出口：待办/已办/我发起均可在授权范围打印同一实例单据与审批记录；现有审批动作不变。

### M5 实施拆分（2026-09-19）

- M5a（服务端）：先完成流程 task/instance/run/record 一致性解析、待办/已办/我发起分场景授权、实例审批轨迹适配，以及采购代码业务 Provider。流程上下文继续通过 Flow 服务现有可见性接口校验；运行打印只读应用已发布快照，不依赖模板设计权限。
- M5b（前端与 BPMN，已完成）：新增独立 `FlowPrintAction` 和 `flowPrintContextStore`，从详情页当前选中记录同步稳定字符串身份；接入前拆出超限详情样式/上下文，避免继续扩大 `todo.vue`、`started.vue` 和 `FlowTaskDetailShell.vue`。节点模板策略作为审批节点现有配置的小分区保存到 BPMN 扩展属性。
- M5c（资源与审计收口）：流程签名/图片统一走可取消、失败即阻断的鉴权资源加载器；执行事件仍只接受 DIALOG_OPENED/FAILED，打开预览或模板选择不记为出纸。
- 本阶段不启动 Admin/Flow/MySQL/Redis，不执行真实流程或迁移；自动化覆盖模块单测、前端组件/协议测试与聚合构建，真实待办/已办/我发起 E2E 由用户环境回填。

M5b 按 R02 再拆成以下可独立核验的小任务：

- [x] M5b-1a（2 文件，纯结构调整）：将 `todo.vue` 的作用域样式迁到同目录 CSS，SFC 降至 2000 行以内，不改变流程行为。
- [x] M5b-1b（4 文件，纯结构调整）：将 `started.vue`、`FlowTaskDetailShell.vue` 的作用域样式迁到同目录 CSS，SFC 降至 800 行以内，不改变流程行为。
- [x] M5b-2a（4 文件）：新增 `flowPrintContextStore`、`FlowPrintAction` 与对应测试；服务端重取稳定身份，切换任务用 generation 丢弃旧响应，待办未保存字段先提示。
- [x] M5b-2b（5 文件）：业务表单上下文补服务端规范 `applicationId/processRunId`；优先使用不可变 run，旧 CODE 流程只接受业务对象唯一归属的已发布应用，并补 Mapper XML 与单测。
- [x] M5b-3（3 个入口文件）：待办、已办、我发起的详情工具栏接入同一打印动作，只传当前选中行及已经加载的授权上下文。
- [x] M5b-4a（4 文件）：新增节点打印策略小分区及组件测试，接入审批节点并补默认配置。
- [x] M5b-4b（3 文件）：BPMN 解析/写回和往返测试；仅保存 `INHERIT/RESTRICT + templateIds`，不修改审批动作。

M5c 实际拆分：

- [x] M5c-1：新增运行时资源加载边界，将取消映射为 `PRINT_CANCELLED`，将非法/缺失/未就绪资源统一映射为 `RESOURCE_FAILED`；继续保留 10 秒超时、AbortSignal 和 Blob URL 释放。
- [x] M5c-2：按字段目录识别明细表 IMAGE 列；流程 `flow.history.signature` 经鉴权下载、解码后作为图片单元格参与测量/分页，不向纸面暴露 fileId。
- [x] M5c-3：FAILED 审计禁止携带页数；执行事件响应固定 `physicalOutputConfirmed=false`，明确 `DIALOG_OPENED` 不是出纸确认。

| 状态/任务 | 依赖 | 拟涉及文件 | 验收与证据 |
|---|---|---|---|
| [x] T35 流程身份/数据适配 | T29,T33 | GJ `service/printing/{FlowPrintContextResolver,FlowPrintAccessPolicy,FlowPrintHistoryAdapter}.java`、GT `service/printing/FlowPrintAccessPolicyTest.java` | task/instance/run/record 一致；三类入口分别授权；重提/会签不混轮次 |
| [x] T36 代码业务 Provider | T35 | B 新增采购打印 Provider、采购打印字段目录、对应测试 | 复用现有业务读取，运行接口不依赖设计权，不以 formUrl 截图代替 |
| [x] T37 流程打印入口 | T35,R02 | U `components/flow/FlowPrintAction.vue`、U `stores/print/flowPrintContextStore.js`、拆分后的详情上下文组件、对应组件测试 | 真实选中实例上下文；切换任务不串数据；未保存修改有提示 |
| [x] T38 节点打印策略 | T35 | 既有流程节点面板新增小分区组件、节点配置序列化/解析文件、策略测试 | 继承默认/限制子集；随节点模型版本保存，不改审批动作 |
| [x] T39 鉴权资源和审计 | T37,T38 | U `runtime/printResourceLoader.js`、PJ 执行事件 DTO/Service 小改、资源授权测试 | 签名/图片鉴权，资源失败阻止输出；对话框打开不等同出纸 |

## M6：验证、审查和交付

- [x] T40：按 test-spec 的 P0 自动化矩阵执行协议/分页/权限/版本/流程回归；修复差异后增量复跑。真实环境项仍归 T42/用户验收。
- [x] T41：前端构建、相关 Maven 编译及目标单测；SQL/XML 静态检查；保留命令和结果。
- [ ] T42：浏览器验证拖拽、编辑、长表分页、字体/图片失败、亮暗主题、切换资源清理；截图/产物落 verification。
- [x] T43：Spec 合规审查，再代码质量审查；整改后记录审查结论。审查阶段可按仓库规范使用独立审查上下文。
- [x] T44：提供真实 Flyway/API/流程/打印机验收步骤，由用户回填；未完成保持 `implemented-pending-e2e`。
- [x] T45：交付文档、使用说明、回滚指引与最终任务状态；仅在实际验收完成后归档。
- [x] T46：按用户反馈补强打印设计器视觉基线：横纵毫米标尺、1/5mm 网格、页边距与页眉页脚辅助线、纸张预设/旋转/缩放工具、紧凑图标物料区；浏览器以桌面视口核对三栏和正式入口说明。
- [x] T47：收敛设计器命令与面板布局，增加拖动/缩放时参与吸附的动态定位线及六向对齐/等距分布，升级预览为支持适合宽度、页码和紧凑缩放控制的文档查看工作台；组件/状态测试、浏览器视觉复核与前端构建通过，单独提交且未 push。

## 依赖主线

`D02 → T01…T13 → T28 → T29…T34 → T35…T39 → T40…T45 → T46 → T47`。

后端 `T14…T27b` 可以在同一实施阶段顺序穿插；不存在默认多 Agent 并行授权。任务的前置协议、迁移和权限审查不得跳过。

## 后续独立提案

H01 审批业务快照/归档与历史重打；H02 批量与静默客户端；H03 复杂表格/合同排版。未纳入本次完成口径。

M3a 验证拆分补充：T18b/T19b 使用 `PrintPersistenceTest.java` + test-scope H2 验证真实 Mapper 的 stale revision、跨租户、删除重建、永久版本号与执行事件 CAS。内存数据库由测试创建/关闭，不连接 Admin 配置。T16 额外包含 PrintDataMode，共 5 个主要文件。

T21b 增加 `PrintProtocolLimits.java` 汇总与前端一致的技术限制，主要文件共 5 个。T21 验证另含 PrintProtocolSmokeTest、PrintProtocolCompatibilityTest、共享 compatibility-cases.json 和 verification/protocol-compatibility.mjs。

### M3a 阶段结果

T14–T21 源码与阶段验证完成：打印插件、4 表/5 个业务字典/4 项权限、实体与 Mapper、模板 DTO、协议模型和白名单校验。72 项 Java 测试通过；29 个共享前端协议样例通过；Admin 46 模块聚合 package 成功。实际文件、命令和审查证据见 execution-log 及 verification/m3a-results.json。

本阶段只有协议校验 Component 与 Mapper，没有提前暴露未授权 Controller。T22–T28 不勾选，M3 完整模板/授权/运行链路仍未完成。H2 验证不能替代 MySQL 方言、真实租户拦截器及运行授权验收；新增迁移没有自动执行。

## M3b 实施拆分（2026-09-19，编码前）

按授权依赖先做 T25，再做模板服务与运行编排；各子任务 3–5 个主要文件，测试及文档单列。

- T25a：spi/{PrintActor,PrintSourceRequest,AuthorizedPrintSource,PrintApplicationAccess,PrintDataProvider}。应用授权/事务锁/发布引用检查通过独立 SPI，缺少实现拒绝调用。
- T25b：spi/{PrintRecordRequest,AuthorizedPrintContext,PrintBindingSelection,PrintData}、vo/PrintFieldCatalogVO；发布版本选择与字段目录由可信 Provider 返回。
- T25c：service/{PrintIdentity,PrintProviderRegistry,PrintFailure,PrintAudit}、enums/PrintDesignAction；先测试身份缺失、Provider 缺失/重复与来源不一致。
- T22a：service/{PrintTemplateService,PrintTemplateVersionService,PrintTemplateAccess}、vo/PrintTemplateVO；T22b：service/PrintDocumentAccess、vo/PrintVersionVO、controller/PrintTemplateController；沿用 Spec 的 REST API，非代码生成器 POST-safe CRUD。
- T23：模板服务、版本事务、Controller 注解/DTO 协议测试；使用 H2 真实 Mapper + Spring 事务代理与合成应用授权，覆盖 rollback、CAS、被引用删除、同内容重复发布。
- T24a：dto/{PrintBindingQueryDTO,PrintBindingSaveDTO}、service/PrintBindingService、controller/PrintBindingController；T24b：Mapper 添加 scoped binding 读取与测试，应用锁→模板锁保持一致顺序。
- T27a：dto/{PrintPrepareDTO,PrintAvailableTemplatesDTO,PrintCatalogQueryDTO,PrintExecutionEventDTO}；GET 版本详情补入 T22，删除使用 revision 查询参数。
- T26a：service/{PrintPrepareService,PrintDataProjector,PrintExecutionService}、vo/{PrintContextVO,PrintAvailableTemplateVO}；T26b：controller/PrintRuntimeController 与服务/授权/事件测试。
- T28a：U api/print.js、stores/print/{printTemplateStore,printRuntimeStore}.js 和状态/API 测试；T28b：views/print/{index,designer}.vue、components/print/management/{PrintTemplateCreate,PrintTemplateVersions,PrintBindingPanel}.vue；T28c：runtime/PrintTemplatePicker.vue、views/print/preview.vue、设计器 Toolbar/生命周期小改与组件测试。
- T28d：新 V1.0.170 权限隐藏路由种子、verification 的合成服务端验证入口/浏览器证据。模板页面需要上游应用/表单上下文，不让普通用户手输应用/业务对象 ID；正式应用内资源导航留 T31。

本轮不安装生产合成 Provider，不启动真实 Admin/Flow/数据库。M3 阶段出口通过测试专用 Provider、真实 Service/Mapper/事务和模拟 HTTP 完成；真实业务适配器仍留 M4/M5。

T28 补充文件：management/printRouteContext.js 解析入口标识；router/index.js 注册 3 个页面的布局和离开保护语义。V170 仅注册隐藏菜单，perms 为 NULL，继续使用 V169 四项 API 权限，不自动给角色增权。PrintIdentityTest/PrintDocumentAccessTest 覆盖登录租户一致性及资源别名。

### M3b 阶段结果

T22–T28 的上述子任务均已实现并验证，实际新增内容包括模板/版本/绑定事务服务与 3 个 Controller、来源与运行授权 SPI、按发布清单 prepare、字段/图片资源投影、执行事件 CAS；前端 API、两个 Pinia store、模板管理/设计/预览页及版本/绑定面板。没有向生产安装合成 Provider。

- Java 104/104、前端 71/71 通过；分页路径修正后 API 契约 2/2 增量复验；定向 lint 与前后端构建通过。
- 真正 Spring 事务代理 + H2 Mapper 覆盖并发默认绑定、发布插入后 rollback、旧修订冲突、跨租户/操作者、图片字段授权与 4MiB 输出边界；MockMvc 覆盖明确 DTO 和规范化 schemaJson 响应。缺少 Provider 的 Spring 构造注入验证通过。
- 合成 HTTP 浏览器验证包括创建/保存/冲突/等待中编辑/名称离开保护/版本/绑定/仅运行权限/预览/缺少适配器/数据清理，详见 verification/m3b-results.json 和 browser-results-m3b.json。
- 本阶段可进入 M4，但真实业务闭环未验收：应用授权 SPI 实现、低代码 Provider、应用发布快照固定模板引用仍由 T29–T34 完成。M4–M6 和真实 E2E 不勾选。

### M4a 阶段结果

M4a-1 已提交 d90b720b；M4a-2/3 的应用适配和提交守卫使用同一事务锁，作为同一安全闭环提交。字符串页面 ID、当前身份、应用设计权限、历史引用检查、固定模板版本/hash 验证已实现。Print 106 项、generator 30 项目标测试通过；其中 5 项采用生产 XML/MyBatis-Plus 和真实 Spring/H2 事务验证。前端 77 项与生产构建通过，Admin 46 模块聚合 package 通过。identity 新增断言另增量复验通过。

T29/T30/T33 总任务保持未完成：候选 printing 清单生成、已发布业务字段目录及真实数据 Provider 仍属 M4b；T31/T32/T34 和 R01 保留待办。无真实库迁移或业务 E2E，不将 M4a 视为可打印业务单据。

### M4b 编码拆分与边界（2026-09-19）

- [x] M4b-1：PrintBindingMapper/XML、PrintApplicationSnapshotContributor、BusinessApplicationSnapshotService；候选生成时固定启用绑定的已发布模板版本/hash，后续发布重试和回滚保留固定引用，不重新读取最新模板指针。
- [x] M4b-2：PrintMetadataResolver、LowcodePrintCatalogBuilder、LowcodePrintSourceResolver、PrintBindingValidationService；从应用版本指定的对象设计版本→CRUD 版本读取完整元数据；来源必须是该页面实际使用的对象；字段与明细可见性取发布模型/页面交集。
- [x] M4b-3：严格读取固定配置的 LowcodePrintRecordReader、LowcodePrintValueAdapter、DynamicCrudService 小范围增量；主子表都走记录范围、解密/公式/翻译/脱敏，子表禁止猜测外键，超过 500 行拒绝。金额输出统一回到打印协议的分。
- [x] M4b-4：LowcodePrintDataProvider、LowcodePrintResourceAccess、应用提交守卫接字段验证；仅 LIST/DETAIL，当前用户应用/页面/对象/记录权限全部通过才返回固定版本；流程场景仍拒绝，留 M5。
- [x] M4b-5：以上服务单测、真实 Mapper/事务增量验证、Admin 聚合构建；回填本轮证据后分阶段本地提交。

每个子任务主要源码不超过 5 个文件；测试、构造器兼容调整和本 SDD 文档单列。运行读取绝不回退到草稿；缺少完整历史发布配置、子对象固定版本或关系元数据时给出可定位错误，不能以猜测配置继续输出。应用发布自身的多步骤恢复机制保持现状，打印验证失败不提交新的应用版本/指针。

M4b-4 补充分组：4a 为 Provider/资源授权/字段校验/最终提交守卫（4 文件）；4b 为当前对象启停守卫（AiCrudConfigMapper/XML、PrintMetadataResolver、Provider，4 文件）。只查询当前启停/删除状态，不读取最新草稿作为打印元数据。候选绑定查询使用应用锁内 FOR UPDATE 当前读，避免发布长事务的旧一致性快照。

M4b-3 补充：3a 为读取器/值适配器/DynamicCrudService；3b 为虚拟公式打印执行入口（AbstractFormulaRuntime、VirtualFormulaRuntime、DynamicCrudService，3 文件）。现有普通读取会记录公式输出且吞掉执行错误；打印入口复用执行引擎，禁用输入/输出 trace 和公式执行日志，遇公式错误阻止整份输出。增加相应行为测试，不改变普通 CRUD 公式行为。

### M4b 阶段结果

T29/T30/T33 后端实现完成并通过阶段验证（implemented-pending-e2e）。新增 9 个打印适配生产类，挂接候选快照/最终应用版本提交；严格使用应用指定的对象设计版本→CRUD 版本，当前启停/删除只作撤权检查。主子表列目录取模型可见性、页面区域和已编译子表显示列的交集；固定子表关系不猜外键。主子表都受记录范围、解密、公式、翻译、脱敏保护；公式和脱敏失败阻止输出。复用文件下载权限且校验租户、类型与大小。

验证：打印插件 106 项，generator 79 项目标回归通过；最后增加 1 项草稿版本伪装拒绝用例，并复验元数据/Provider 共 15 项通过，累计 186 个不同用例通过。Admin 46 模块 package 成功；SQL/XML、git diff --check 通过。实际边界和命令见 execution-log、verification/m4b-results.json。

M4c（入口、动作投影、导出协议）和 M5 流程/代码 Provider 未完成，FLOW 场景明确拒绝。未启动真实服务、未跑 MySQL/Flyway/API/打印机验收；前端无改动，复用 M4a 基线。只做本地 commit，禁止 push。

## M4c 实施拆分（2026-09-19，编码前）

R01 结论：不适用。AiCrudPage 已有 route + params(rowField/static)、runtimeActions 按 row/detail 投影、详情按钮和更多菜单，T32 完全复用该路径，不修改此超大 SFC。PortalPageRenderer 678 行、工作台入口 534 行，可在规模约束内小幅接入。

- M4c-1 / T31：新增来源解析工具、Pinia 工作台状态、PrintSourceSelector；从页面实际 objectRef 与应用对象交集生成可选表单，缺失/失效来源不能新建。
- M4c-2 / T31：提取 PrintTemplateList（既有 index 页面复用）、新增 ApplicationPrintPanel；工作台入口和导航增加打印分区，共 5 个主要文件。模板设计继续打开全屏路由。
- M4c-3 / T32：新增 PrintRuntimeActionProjectionService，Controller 调用；PortalPageRenderer 传 pageId、api/ai/lowcode.js 透传查询参数。只从已发布应用快照投影，服务端重新检查门户页面/对象和模板状态。
- M4c-4 / T34：核实统一静态代码生成链，打印导出贡献器携带协议、固定绑定、版本定义及依赖契约；应用导出与共享协议编译入口复用，禁止把业务 CRUD 改接动态接口。独立部署的数据提供方依赖明确记录，不能把 JSON 保存误报为后端适配完成。
- M4c-5：来源/动作/导出行为用例、定向前后端回归、两端构建、合成浏览器明暗/窄屏与路由交互；不启动真实后端或执行迁移。各子任务的测试与验证文件单列，不计入主文件额度。

M4c-4b 复核补充：主子表代码生成会将子对象合并进主对象输出，应用包需另生成 application-printing.json，覆盖全部已选对象（含被聚合消费的子对象）；manifest 显式指向该文件。使用同一导出贡献器，不另造版本捕获链。
应用代码预览/下载使用 REPEATABLE_READ 事务，使对象协议与应用级打印清单取同一数据库读视图；导出贡献器独立调用使用只读事务。应用草稿代码生成仍沿用原有元数据准备，不标记为只读。真实 MySQL 并发发布期间的下载一致性另列人工验收。

### M4c 阶段结果

T31/T32/T34 实现与阶段验证完成（implemented-pending-e2e）。来源选择使用 Pinia，工作台和独立列表复用 PrintTemplateList；页面上下文经 PortalPageRenderer → render API → PrintRuntimeActionProjectionService，列表/详情继续走 AiCrudPage 的原有 route 动作。新增 AiCrudPage-print.spec.js 直接调用真实组件的列表/详情按钮，R01 无需改动组件本体。

T34 实际文件拆分：4a 为 ApplicationVersion Mapper/XML + PrintCodegenContributor + LowcodeProtocolSnapshotBuilder；4b 为 VelocityCodegenStrategy、PRINTING.md.vm、BusinessApplicationCodegenService、BusinessAppCodegenService。统一导出不可变模板、绑定/hash 和运行依赖；应用级清单覆盖聚合子对象；覆盖报告明确独立部署的数据提供方和资产导入需要扩展，不声称目标环境自动可打印。

验证：37 个后端测试类共 201 项、16 个前端测试文件 89 项通过；ESLint、Vite 主构建/独立验证构建、Admin 46 模块构建通过。亮暗主题、390px 无整页横向溢出、表格更多动作可滚动到达、表单来源创建、预览和权限状态已用合成 HTTP 验证。没有启动真实 Admin/Flow/MySQL/Redis或执行迁移，SQL JSON_CONTAINS 的 MySQL 实跑、并发下载一致性、真实权限与实机打印仍待人工验收。详见 execution-log、verification/m4c-results.json。
