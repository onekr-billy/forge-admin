# Execution Log

## 2026-09-25

- 建立阶段化 Spec/Tasks/Test Spec；已读取项目规则、低代码/流程 Skill、自动化测试标准和相关历史踩坑索引。
- 阶段一：完成新建页面设计器占位、对象编码异步唯一校验、后端租户范围唯一性校验和冲突兜底。
- 阶段二：完成标准列表/左树右表模板、旧树配置字段迁移、业务化树配置面板及 `apiConfig.tree` 兼容生成。
- 阶段三：完成树全量/懒加载运行时；补充正式 `PortalPageRenderer` 的树配置加载、节点事件接线和页面级右表过滤，修复左树 blockId 与右表 blockId 不一致导致过滤不生效的问题。
- 阶段四：完成子表展示字段、选择器展示字段和筛选条件的竖向拖拽/上下移动排序，并将顺序传入运行时配置。
- 前端 Vitest：5 个文件、40 个测试通过；补充运行时流程状态/流程设计器回归 2 个文件、59 个测试通过。
- 前端 Vite 生产构建成功；仅存在既有 CSS 注释、动态 import 和 chunk 体积警告。
- 后端生成器定向 Maven 测试：7 个测试通过，BUILD SUCCESS。
- `git diff --check` 通过。
- 定向 ESLint 仍有存量巨型 SFC 的 import 排序、模板缩进和 `no-use-before-define` 错误；本轮新增代码无解析错误，已记录到巨型类治理专项。
- 审批状态字段整列消失尚未纳入本变更的修复范围，保留为后续专项。

## 2026-09-26

- 修复草稿运行配置编译 NPE：`appendTreeRuntimeField` 经 `buildSearchField(field)` 传入 null `modelSchema`，`applySearchTreeSelectDefaults` → `buildTreeConfig` 调用 `modelSchema.getTreeConfig()` 失败（configKey 例：`cgou_business_object_xryd`）。
- `buildTreeConfig` / `isTreeRuntime` / 树字段推断改为 null-safe；隐藏筛选项构建时传入完整 model/page schema。
- `resolveIncludeChildrenTreeConfig` 优先按查询区 treeSelect 自身 optionSource 展开，避免被左树外部源误绑；子集查询依赖草稿编译成功后的 `includeChildren` + `_includeChildren` 展开。
- 验证：`mvn -Penable-tests test -Dtest=LowcodeRuntimeConfigBuilderTest`（JDK17）通过。

## 2026-09-26 (续) 查询树对齐左树

- 根因：查询区 treeSelect 未稳定复用左树 `/tree`+排序；保存后 optionSource/includeChildren 易丢；搜索提交时 `_includeChildren` 可能未带上。
- 前端：`alignSearchSchemaWithLeftTree` 按 tree-panel.filterField 注入与左树相同的 optionSource（含 designPreview、orderBy）；`AiSearch` 提交前补齐 `field_includeChildren`；`publicParams` 合并 `options.defaultSort`；左树加载带上排序参数。
- 后端：`buildTreeOptionSource` 支持排序参数；本表树源对齐时带 defaultSort。
- 验证：vitest runtime-tree-table + runtime-crud-props 44 通过；LowcodeRuntimeConfigBuilder 相关测试通过。

## 2026-09-26 (续2) 查询子集对齐左树展开参数

- 对比请求：左树 `fieldTreeSelect=1,5`，查询区只有 `fieldTreeSelect=1` + `_includeChildren`；后端展开不可靠。
- 查询区选树后按选项树前端展开写入 `field__treeExpanded`，列表请求改写为与左树相同的 `field=1,5`。
- 左树节点序：不再用右表 `defaultSort(id desc)` 请求 `/tree`，恢复树接口默认主键序。

## 2026-09-26 (续3) 多值仍按 eq 精确匹配

- 请求已是 `fieldTreeSelect=1,5`，但 `_searchTypes.fieldTreeSelect=eq`，SQL 变成 `= '1,5'` 无结果。
- 后端：多值（逗号/List）强制按 `in`；Repository / QueryGenerator / expand 后 coerce。
- 前端：展开后改写 `_searchTypes` 为 in，并去掉 `fieldTreeSelectName` 展示字段。
