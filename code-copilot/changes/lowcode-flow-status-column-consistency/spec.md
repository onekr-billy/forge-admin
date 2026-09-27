# 低代码业务流程状态字段与列表运行配置一致性修复 Spec

> 变更名：`lowcode-flow-status-column-consistency`  
> 状态：`proposed`  
> 创建日期：2026-09-24

## 1. 问题描述

低代码应用绑定业务流程，并在审批节点使用审批状态字段后，正式列表中的“审批状态”列偶尔消失。重新进入业务流程，点击审批节点，再重新发布应用后，列又恢复。

该现象不是单纯的前端列渲染问题，而是同一个托管字段在多个配置快照之间没有保持一致：

1. 业务对象草稿 `modelSchema.fields` 中可能已有 `flowStatus`。
2. 页面草稿 `pageSchema`、`listGridLayout.items[*].fieldRefs` 或 `AiCrudConfig.columnsSchema` 可能仍是旧快照。
3. 正式运行态 `getRenderConfig(..., false)` 优先读取 `AiCrudConfigVersion` 的已发布快照，而不是直接读取最新草稿。
4. 应用发布遇到对象仍为 `PUBLISHED` 时可能复用旧对象版本，导致新增的 `flowStatus` 没有重新进入正式版本。
5. 应用页面区块保存过旧的 `fieldRefs` 时，前端会按旧引用过滤列表列。

重新打开审批节点并保存/发布会触发 `ensureFlowStatusField`、流程保存时的 `autoEnsureFlowStatusFields`、列表引用补齐和对象状态变更，因此看起来像“点击审批节点后字段恢复”，本质是一次隐式的派生配置重建。

本次补充确认：用户反馈的是“整列消失”，不是列仍在但单元格值为空。因此首要排查范围是列定义快照和 `fieldRefs` 过滤链路，不把流程回调的状态值回写问题当作本问题的主因。

## 2. 已确认的代码证据

### 2.1 字段补齐只保证对象草稿和数据库列

`BusinessFlowStatusFieldService.ensure()` 会创建或校验：

- `field = flowStatus`
- `columnName = flow_status`
- `dictType = business_flow_status`
- `advancedProps.managedBy = BUSINESS_FLOW`
- 数据库列 `flow_status`
- 对象列表字段可见性

但该方法不会自动创建或重建 `AiCrudConfigVersion`，也不会统一更新已发布应用页面区块快照。

### 2.2 正式运行态优先读取旧发布快照

`AiCrudConfigService.resolvePublishedRuntimeConfig()` 根据 `publishedVersion` 读取 `AiCrudConfigVersion` 的 `modelSchema/pageSchema/columnsSchema/editSchema`。若 `flowStatus` 只进入草稿，正式列表仍然使用缺少该字段的旧 `columnsSchema`。

当前 `healManagedFlowStatusField()` 只修改本次响应中的副本，不写回发布版本，也不重建页面区块，因此只能作为兼容旧数据的临时兜底。

### 2.3 应用发布可能复用旧对象版本

`BusinessApplicationPublishService.publishObjects()` 在对象设计状态仍为 `PUBLISHED` 且存在旧发布版本时直接复用版本 ID。若流程字段变更没有把对象标记为 `CHANGED`，应用发布成功但仍会钉住不含 `flowStatus` 的旧对象快照。

### 2.4 页面区块旧 `fieldRefs` 会过滤列

运行时通过 `filterCrudItemsByFieldRefs()` 按应用页面区块保存的 `fieldRefs` 过滤列表列。当前代码已经尝试把托管流程字段补回引用，但旧字段缺少 `advancedProps.managedBy`、字段目录未返回或显式配置状态不完整时，补偿仍可能失效。

整列消失时必须区分两个观测点：

1. CRUD 正式运行接口返回的 `columns` 已经没有 `flowStatus`：属于发布快照/后端版本同步问题。
2. CRUD 正式运行接口返回的 `columns` 有 `flowStatus`，但页面最终没有列：属于应用页面区块 `fieldRefs`、`fieldCatalog` 或前端托管字段识别/过滤问题。

### 2.5 自动补齐失败可能被静默吞掉

前端审批节点自动调用 `ensureFlowStatusField({ silent: true })`；后端 `autoEnsureFlowStatusFields()` 捕获异常后只记录日志并返回。缺少 `ai:lowcode:deploy-ddl` 权限时，界面可能仍显示流程绑定成功，但字段、数据库列或运行配置没有完成同步。

### 2.6 流程绑定保存入口未统一触发一致性校验

`BusinessFlowService.saveFlowBinding()` 当前主要保存 `ai_business_binding` 和默认流程配置，不直接保证 `flowStatus`、列表布局、CRUD 发布快照与应用页面区块同步。未经过流程设计器 `saveSchema()` 的绑定入口可能只保存了绑定关系。

## 3. 目标

1. 所有低代码业务流程绑定、流程保存、流程发布和应用发布入口都使用同一套幂等的一致性服务。
2. 审批状态字段、列表列、页面引用、CRUD 草稿、CRUD 发布版本和应用页面快照在发布完成后保持可验证的一致。
3. 已发布对象存在新的托管流程字段变更时，不得仅因旧状态为 `PUBLISHED` 而复用过时版本。
4. 旧数据即使缺少 `advancedProps.managedBy`，也能通过 `flowStatus/flow_status/business_flow_status` 识别并补齐托管元数据。
5. 自动补齐失败必须向用户返回明确、可操作的错误；不能以“绑定成功”掩盖权限或 DDL 失败。
6. 运行时自愈仅用于兼容历史数据，正式发布流程必须持久化修复后的配置并清理相关缓存。
7. 正式门户只读取已发布配置；设计预览可以读取草稿，但不能污染正式运行态。
8. 平台默认保证托管字段在列表可用，同时尊重用户明确保存的 `flowStatus.visible = false` 设置。
9. 拆分现有巨型业务类中的一致性编排，避免继续把字段、流程、发布和页面快照逻辑堆入单一 Service。

## 4. 一致性契约

### 4.1 托管字段标准

除兼容历史数据外，所有新写入的审批状态字段必须满足：

| 属性 | 要求 |
| --- | --- |
| 字段编码 | `flowStatus` |
| 数据库列 | `flow_status` |
| 字典 | `business_flow_status` |
| 管理归属 | `advancedProps.managedBy = BUSINESS_FLOW` |
| 字段状态 | `ENABLED` |
| 列表可见 | 默认 `true` |
| 允许值 | `DRAFT/IN_PROCESS/NEED_MODIFY/APPROVED/REJECTED/CANCELED` |
| 数据库类型 | 文本类型，长度至少 16，推荐 `varchar(32)` |

审批节点的 `statusField` 必须规范化为 `flowStatus`；历史值 `flow_status` 只作为输入兼容格式，保存时统一规范化。

### 4.2 发布一致性

一次成功的应用发布必须同时满足：

- 对象草稿字段目录存在有效托管字段。
- 对象列表布局包含 `flowStatus`，除非用户明确隐藏。
- `AiCrudConfig` 草稿的 `modelSchema`、`pageSchema`、`columnsSchema` 已同步。
- `AiCrudConfigVersion` 的已发布快照包含字段和列表列。
- 应用页面 CRUD 区块的 `fieldRefs` 不会错误过滤托管字段。
- 流程节点绑定的 `statusField` 与对象字段编码一致。
- 对象 `design_status`、CRUD `publish_status`、应用发布版本之间不存在“应用已发布但对象仍使用旧快照”的组合。

## 5. 设计要求

### 5.1 后端统一一致性服务

新增独立的 `BusinessFlowRuntimeConsistencyService`（名称可调整），职责至少包括：

1. 幂等确保字段元数据和数据库列。
2. 修复旧字段的 `managedBy`、字典、状态和列表可见性元数据。
3. 同步对象页面 Schema、列表 `fieldRefs` 和 CRUD 草稿运行配置。
4. 检查当前发布版本是否缺少托管字段；有漂移时生成新的正式版本或阻断发布。
5. 更新应用页面区块的托管字段引用，避免旧 `fieldRefs` 过滤掉列。
6. 返回结构化结果：是否创建字段、是否更新草稿、是否需要重发、失败原因和所需权限。

该服务由以下入口调用，且调用必须幂等：

- 保存/更新流程绑定。
- 保存流程设计 Schema。
- 发布业务流程。
- 发布低代码业务对象。
- 协调发布低代码应用。

`BusinessProcessService`、`BusinessFlowService`、`AiCrudConfigService`、`BusinessApplicationPublishService` 只保留入口编排和事务边界，不重复实现字段识别、快照修复和列表引用合并逻辑。

### 5.2 应用发布版本选择

- 发布前重新读取对象当前设计状态和最新草稿摘要，不能完全依赖早期候选快照。
- 只要草稿中存在尚未进入发布版本的托管字段/列表配置变化，就必须将对象标记为 `CHANGED` 并生成新对象版本。
- 复用旧对象版本前必须通过结构摘要或字段版本校验，确认 `flowStatus`、列表列和页面 Schema 均一致。
- 发布后校验对象版本、CRUD 发布版本、应用快照均存在且指向同一有效配置；不满足时整个发布步骤返回失败。

### 5.3 运行时自愈

- `findManagedFlowStatusField()` 必须同时支持 `managedBy=BUSINESS_FLOW` 和历史字段特征 `field=flowStatus`、`columnName=flow_status`、`dictType=business_flow_status`。
- 自愈不得覆盖用户显式禁用/隐藏配置；仅补齐缺失的托管标识、字段目录和默认列表列。
- 自愈成功后，应持久化到草稿或新建发布版本，并失效 CRUD/应用运行配置缓存。
- 自愈失败应记录可关联的诊断编号并向调用方返回明确错误，不得只写日志继续成功。

### 5.4 权限与错误处理

- 缺少 `ai:lowcode:deploy-ddl` 时，绑定/保存/发布必须返回“缺少同步数据库权限”的结构化业务错误，并指出对象和字段。
- 失败事务不得留下“流程已绑定但字段未创建”的半成功状态。
- 不允许通过扩大权限、绕过租户隔离或直接执行未经审计的 DDL 来规避问题。

### 5.5 前端运行配置刷新

流程节点自动补齐成功后，必须刷新字段目录、页面 Schema、运行时 CRUD 配置和应用页面区块引用；发布完成后必须刷新运行配置缓存/查询结果。

自动补齐失败时：

- 自动触发至少显示 warning，并在节点或发布检查清单中标记失败。
- 用户点击保存/发布时阻断继续提交，给出权限或字段冲突的具体原因。
- 手动重试成功后再清除错误标记。

## 6. 兼容与数据处理

- 不删除历史 `AiCrudConfigVersion`；新版本通过正常发布流程生成。
- 不自动修改用户明确隐藏的列表字段。
- 对历史 `flowStatus`/`flow_status` 字段只做可验证的元数据补齐；类型、长度或数据库列冲突时阻断并提示人工处理。
- 不修改 Flowable 原生表，也不改变业务流程状态机语义。
- 若存在无法自动判定归属的同名业务字段，不自动接管，返回冲突信息。

## 7. 非目标

- 本变更不新增审批状态业务含义，不改变 `APPROVED/REJECTED/CANCELED/NEED_MODIFY` 的状态流转。
- 本变更不允许终态单据重新发起流程。
- 本变更不进行无审批的数据批量修复；历史数据补偿必须由独立任务和回滚方案承载。
- 本轮不重写全部巨型 Service，只抽取与本问题直接相关的字段/快照一致性职责。

## 8. 验收标准

1. 已发布对象绑定流程后，不重新打开审批节点，直接发布应用，正式列表稳定显示流程状态列。
2. 旧发布快照缺少 `flowStatus`、旧页面区块缺少 `fieldRefs` 时，发布流程能生成新一致版本，或明确阻断并说明原因。
3. 旧字段只有 `flowStatus/flow_status` 特征但没有 `managedBy` 时，能被识别并补齐托管元数据。
4. 多次保存绑定、重复打开审批节点、重复发布均幂等，不重复创建字段、列、列表列或版本引用。
5. 缺少 DDL 权限时，流程绑定/保存/发布不会返回成功；前端能看到具体错误。
6. 用户明确设置 `flowStatus` 隐藏后，运行时不强制显示；未明确隐藏时，旧 `fieldRefs` 不得吞掉托管字段。
7. 正式门户只返回已发布快照，设计预览仍可读取草稿；两者不会相互污染。
8. 应用发布后对象状态、CRUD 发布版本、应用版本和页面区块引用均通过一致性校验。
9. 后端一致性逻辑已从巨型 Service 中抽取为可单测的独立能力，原有租户、权限、事务和逻辑删除约束不回归。
10. 定向后端测试、前端测试、编译/构建和 `git diff --check` 通过；真实数据库/流程联调结果单独记录。

## 9. 风险与回滚

- 自动创建数据库列涉及 DDL 权限和事务边界，必须先校验字段兼容性；失败时阻断发布并保留诊断信息。
- 重新生成发布版本可能改变正式列表结构，但只新增平台托管字段，不覆盖用户自定义列顺序、显式隐藏和操作列配置。
- 回滚代码可恢复旧发布逻辑；已生成的新版本不删除，通过应用版本回退恢复上一份完整快照。
