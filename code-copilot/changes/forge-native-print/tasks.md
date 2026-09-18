# 实施任务

> 状态：implementing，M1 已迁入 forge-admin，目标仓库复验与阶段 commit 进行中；M2–M6 未开始。禁止 push。
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
| [ ] T09 Pinia 设计状态 | T08 | `stores/print/printDesignerStore.js`、`designer/{commands,history}.js`、`designer/__tests__/history.spec.js` | 选择/跨面板状态、撤销重做、拖动一次提交一次命令 |
| [ ] T10 画布交互 | T09 | `designer/{PrintCanvas,PrintSelectionOverlay}.vue`、`designer/{usePrintDrag,usePrintResize}.js` | 缩放下坐标准确、多选、键盘移动、移出边界处理 |
| [ ] T11 物料与字段区 | T10 | `designer/{PrintElementPalette,PrintFieldTree,PrintSectionList}.vue`、`designer/elementCatalog.js` | 主字段/明细/审批字段可拖入；区块顺序可调 |
| [ ] T12 属性面板 | T11 | `designer/panels/{PaperPanel,TextPanel,TablePanel,BindingPanel}.vue` | 属性即时作用、物理单位清楚、失效字段定位；不堆单文件 |
| [ ] T13 编辑工作台 | T12 | `designer/{PrintDesigner,PrintDesignerToolbar}.vue`、`views/print/designer.vue`、`designer/__tests__/PrintDesigner.spec.js` | 新建/复制/保存草稿/预览入口、未保存提示、明暗主题 |

## M3：模板后端、版本和数据提供方

阶段出口：合成 Provider 通过完整模板/版本/授权链路输出上下文；真实业务 Provider 在 M4/M5 接入。

| 状态/任务 | 依赖 | 拟涉及文件 | 验收与证据 |
|---|---|---|---|
| [ ] T14 插件装配 | D02 | `P/pom.xml`、plugin-parent/pom.xml、generator/pom.xml、admin-server/pom.xml | 单向依赖、Admin 聚合编译、无 Service 环 |
| [ ] T15 数据结构迁移 | T14 | 新 Flyway 迁移、PJ `entity/{PrintTemplate,PrintTemplateVersion,PrintBinding}.java` | 表/索引可重复；显式 TableLogic；当前版本号唯一 |
| [ ] T16 审计实体和状态 | T15 | PJ `entity/PrintExecution.java`、`enums/{PrintDesignStatus,PrintExecutionResult,PrintScene}.java` | 状态 getCode/matches；无物理打印成功误报 |
| [ ] T17 业务枚举与资源种子 | T16 | 新 Flyway 迁移、PJ `enums/PrintSourceType.java`、PT `PrintResourceContractTest.java` | sys_print_* 字典/权限 NOT EXISTS、tenant=1、无全员授权 |
| [ ] T18 模板读写 Mapper | T15 | PJ `mapper/{PrintTemplateMapper,PrintTemplateVersionMapper}.java`、对应两个 Mapper XML | 查询租户/逻辑删除；CAS；唯一编码和版本约束 |
| [ ] T19 绑定/执行 Mapper | T16 | PJ `mapper/{PrintBindingMapper,PrintExecutionMapper}.java`、对应两个 Mapper XML | 默认绑定事务范围、关联索引、日志不存业务正文 |
| [ ] T20 模板 DTO | T18 | PJ `dto/{PrintTemplateCreateDTO,PrintTemplateUpdateDTO,PrintTemplatePublishDTO,PrintTemplateStatusDTO,PrintTemplateCopyDTO}.java` | 固定字段类型、长度/体积/修订号约束 |
| [ ] T21 协议后端验证 | T20 | PJ `protocol/{PrintTemplateDocument,PrintSection,PrintElement,PrintProtocolValidator}.java`、PT `PrintProtocolValidatorTest.java` | schema 白名单、禁脚本、版本拒绝、前后端协议一致 |
| [ ] T22 模板服务/API | T21 | PJ `service/{PrintTemplateService,PrintTemplateVersionService}.java`、`controller/PrintTemplateController.java`、`vo/PrintTemplateVO.java` | 草稿/复制/状态/发布，失败不污染已发布版本 |
| [ ] T23 模板服务行为验证 | T22 | PT `{PrintTemplateServiceTest,PrintTemplateVersionServiceTest,PrintTemplateControllerTest}.java` | 同时编辑冲突、重复发布、被引用删除、停用旧版本 |
| [ ] T24 绑定服务/API | T19,T22 | PJ `dto/{PrintBindingQueryDTO,PrintBindingSaveDTO}.java`、`service/PrintBindingService.java`、`controller/PrintBindingController.java` | 来源核验、默认唯一、应用归属不能伪造 |
| [ ] T25 上下文协议/SPI | T21 | PJ `spi/{PrintDataProvider,AuthorizedPrintContext}.java`、`dto/PrintPrepareDTO.java`、`vo/{PrintContextVO,PrintFieldCatalogVO}.java` | actor/tenant 服务端获取，低代码/代码业务共用入口 |
| [ ] T26 prepare 编排 | T24,T25,T27a | PJ `service/{PrintPrepareService,PrintProviderRegistry,PrintExecutionService}.java`、`controller/PrintRuntimeController.java` | 发布版本解析、使用权限与记录权限、执行事件真实性 |
| [ ] T27a 运行查询/事件 DTO | T25 | PJ `dto/{PrintCatalogQueryDTO,PrintAvailableTemplatesDTO,PrintExecutionEventDTO}.java` | 固定字段类型与场景参数校验，不接受任意 Map |
| [ ] T27b 运行授权验证 | T26 | PT `{PrintPrepareAuthorizationTest,PrintProviderRegistryTest,PrintExecutionServiceTest}.java` | 运行接口无设计权依赖；跨租户/伪造版本/执行事件拒绝 |
| [ ] T28 模板前端持久化 | T22,T24,T27b,T13 | U `api/print.js`、`views/print/index.vue`、`runtime/PrintTemplatePicker.vue`、`stores/print/printRuntimeStore.js` | 真实 API 草稿保存/版本选择；业务枚举使用字典 |

局部顺序明确为 `T25 → T27a → T26 → T27b → T28`，按依赖执行，不按编号机械执行。

## M4：低代码与应用发布

阶段出口：已发布低代码应用可以打印真实授权主子表单据；草稿与运行版本隔离。

| 状态/任务 | 依赖 | 拟涉及文件 | 验收与证据 |
|---|---|---|---|
| [ ] T29 低代码 Provider | T27b,T28 | GJ `service/printing/{LowcodePrintDataProvider,LowcodePrintCatalogBuilder,LowcodePrintValueAdapter}.java`、GT `service/printing/LowcodePrintDataProviderTest.java` | main/children 归一、子表列授权、公式/字典/关联/脱敏 |
| [ ] T30 发布态字段校验 | T29 | GJ `service/printing/{PrintBindingValidationService,PrintMetadataResolver}.java`、GT `service/printing/PrintBindingValidationServiceTest.java` | 字段删除/改型/表单身份变更可定位，不查最新草稿代替发布态 |
| [ ] T31 应用资源入口 | T28,T30 | U `views/app-center/application-workspace/{ApplicationPrintPanel,ApplicationWorkspaceNav}.vue`、现有 `views/app-center/application.[applicationCode].vue`、`components/print/designer/PrintSourceSelector.vue` | 从表单进入自动绑定，独立全屏，不新增对象选择负担 |
| [ ] T32 打印动作投影 | T29,T31 | GJ `service/printing/PrintRuntimeActionProjectionService.java`、现有动作投影接入文件、U `views/print/preview.vue`、对应投影测试 | 列表行/详情均进入统一 route；不复制打印脚本 |
| [ ] T33 应用快照扩展 | T30 | GJ `service/printing/PrintApplicationSnapshotContributor.java`、现有 `BusinessApplicationSnapshotService.java`/`BusinessApplicationPublishService.java`、对应测试 | 固定模板版本/引用/hash，失败无半发布，回滚恢复 |
| [ ] T34 下载协议扩展 | T33 | 现有 `LowcodeProtocolSnapshotBuilder.java`、拟新增打印导出贡献器、生成依赖模板、对应导出测试 | 下载代码包含协议/模板/绑定且复用运行时，无漏字段 |

### 存量超大组件接入条件任务

- [ ] R01（M4 前检查）：确认 T32 是否能完全使用既有 route/配置路径，不改 AiCrudPage。能则记录“不适用”，不能则先拆成 R01a/R01b…，完成被修改 SFC 的合规规模与回归后再接入，不豁免根 AGENTS.md 5.14。
- [ ] R02（M5 前检查）：核对 FlowTaskDetailShell/todo/started/done 实际行数与公共上下文。FlowTaskDetailShell 的时间轴/样式先拆出；触达超 2000 行入口时，先将表单、动作与业务读取按职责拆分，并用 Pinia 管理共享状态。分拆任务每个 3–5 文件，必须在 T37 之前补齐明确清单。

R01/R02 为条件化实施检查，不得勾选后绕过拆分；如果需要的重构显著扩大范围，先更新 Spec 与任务并说明原因。

## M5：流程与代码业务

阶段出口：待办/已办/我发起均可在授权范围打印同一实例单据与审批记录；现有审批动作不变。

| 状态/任务 | 依赖 | 拟涉及文件 | 验收与证据 |
|---|---|---|---|
| [ ] T35 流程身份/数据适配 | T29,T33 | GJ `service/printing/{FlowPrintContextResolver,FlowPrintAccessPolicy,FlowPrintHistoryAdapter}.java`、GT `service/printing/FlowPrintAccessPolicyTest.java` | task/instance/run/record 一致；三类入口分别授权；重提/会签不混轮次 |
| [ ] T36 代码业务 Provider | T35 | B 新增采购打印 Provider、采购打印字段目录、对应测试 | 复用现有业务读取，运行接口不依赖设计权，不以 formUrl 截图代替 |
| [ ] T37 流程打印入口 | T35,R02 | U `components/flow/FlowPrintAction.vue`、U `stores/print/flowPrintContextStore.js`、拆分后的详情上下文组件、对应组件测试 | 真实选中实例上下文；切换任务不串数据；未保存修改有提示 |
| [ ] T38 节点打印策略 | T35 | 既有流程节点面板新增小分区组件、节点配置序列化/解析文件、策略测试 | 继承默认/限制子集；随节点模型版本保存，不改审批动作 |
| [ ] T39 鉴权资源和审计 | T37,T38 | U `runtime/printResourceLoader.js`、PJ 执行事件 DTO/Service 小改、资源授权测试 | 签名/图片鉴权，资源失败阻止输出；对话框打开不等同出纸 |

## M6：验证、审查和交付

- [ ] T40：按 test-spec 的 P0 矩阵执行协议/分页/权限/版本/流程回归；修复差异后增量复跑。
- [ ] T41：前端构建、相关 Maven 编译及目标单测；SQL/XML 静态检查；保留命令和结果。
- [ ] T42：浏览器验证拖拽、编辑、长表分页、字体/图片失败、亮暗主题、切换资源清理；截图/产物落 verification。
- [ ] T43：Spec 合规审查，再代码质量审查；整改后记录审查结论。审查阶段可按仓库规范使用独立审查上下文。
- [ ] T44：提供真实 Flyway/API/流程/打印机验收步骤，由用户回填；未完成保持 `implemented-pending-e2e`。
- [ ] T45：交付文档、使用说明、回滚指引与最终任务状态；仅在实际验收完成后归档。

## 依赖主线

`D02 → T01…T13 → T28 → T29…T34 → T35…T39 → T40…T45`。

后端 `T14…T27b` 可以在同一实施阶段顺序穿插；不存在默认多 Agent 并行授权。任务的前置协议、迁移和权限审查不得跳过。

## 后续独立提案

H01 审批业务快照/归档与历史重打；H02 批量与静默客户端；H03 复杂表格/合同排版。未纳入本次完成口径。
