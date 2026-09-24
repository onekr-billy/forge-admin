# Task Form Document Protocol — Tasks

> 变更：`task-form-document-protocol`

## Phase 1 — 后端协议加量

- [x] Spec + VO + `TaskFormUiDocumentCompiler` + 单测 + BusinessFlowService 写入

## Phase PC-A / PC-B — 审批 PC

- [x] sections 序 / components 树 resolve；todo + FlowReadonlyFormPanel
- [x] 不动 H5

## Phase Unify — 设计/运行协议统一（本轮）

- [x] `@/protocols/ui-document`：constants / compile / resolve / index
- [x] 审批 util 改为 re-export
- [x] 后端编译器对齐轻量 layout 扩展（span/gridStyle/align）
- [x] `crud-page` buildRuntimeFormProfile + transformEditFields 优先 uiDocument
- [x] `buildRuntimeCrudProps` 挂载 uiDocument（editSchema 仍平铺，供 fieldRefs）
- [x] `hydrateRuntimeFormLayout` / `runtime-crud-block-props` 消费 uiDocument
- [x] `LowcodePreviewPane` 预览同路径
- [x] 协议编译 / resolve 单测
- [ ] 手工：设计器 card/row 布局 → 预览 → 运行页一致

## Perf — task-form-context 加载加速

- [x] 同请求内 `getRuntimeConfig` 只读一次并复用
- [x] 审批 `formAssets` 裁掉整份 schema（保留 formKey/formName/usage）
- [x] 应用页 formKey 解析 300s 短缓存
- [x] 分段耗时日志 `[task-form-context] ... stages={}`（含 access/runtimeContext）
- [x] 性能契约单测
- [x] DB：`selectApplicationInAppBuilderJson` 只抽 `inAppBuilder`，跳过 detail 统计 JOIN
- [x] DB：`isCompleteFlowNodeFormInfo` 有 formKey/formRef 时跳过第二次 Flow RPC
- [x] DB：`selectById(AiCrudConfig)` 复用已加载配置
- [x] DB：`TaskFormRuntimeContext` 携带 `publishedConfig`/`businessObject`，避免 object/config 二次查库
- [x] `app_` 页面资产 objectCode 不一致时不再回退 `collectTaskFormAssets`
- [x] 节点表单只拷身份元数据；页面资产缓存保留已解析对象避免反复 parse
- [x] `isCompleteFlowNodeFormInfo` 识别 `variables.businessFormRef`
- [x] 启动预热已启用应用数据源 Hikari 池

## Phase H5（延后）

- [ ] 审批 H5 消费 uiDocument
- [ ] 业务页 H5 消费同一协议
- [ ] 控件映射缺口清单

## Phase 4

- [ ] 文档沉淀 + 废弃双 path 评估
- [ ] Provider 输出 uiDocument
