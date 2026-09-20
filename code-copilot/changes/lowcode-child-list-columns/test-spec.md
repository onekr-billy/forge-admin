# 低代码子表列表字段发布修复测试计划

## P0

- `LowcodeRuntimeConfigBuilderTest`：显式选中且 `listVisible=false` 的子表字段仍生成列，标题不带子表名。
- `LowcodeRuntimeConfigBuilderTest`：table zone 过期时优先使用 list grid 的子表选列。
- `BusinessObjectDesignerPageSchemaTest`：viewSchema 清洗和 zone 回写保留页面模型中的子表字段。
- `page-schema.spec.js`：子表字段在列表设计中不被移除，展示模式默认为聚合。
- `page-schema.spec.js`：列表 grid 可以修复过期的 table zone 字段引用。

## P1

- Generator 相关模块定向测试与编译。
- 本轮 JS/Vue 定向 ESLint。
- 前端生产构建。
- `git diff --check`。

## 真实环境验收

- 保存列表设计并重新发布，重启 Admin 后刷新正式列表。
- 验证子表列已出现，标题只显示字段名，聚合/拆行数据与选项一致。

## 本轮增量：草稿表头与数据查询一致性

- `DynamicCrudServiceChildListTest`：存储列仍只有主表时，设计预览按新 grid 选列生成 JOIN SQL，子表返回键与 render 列一致；聚合模式包含 `GROUP_CONCAT`，拆行模式保留单值及唯一行键，无子行时保留主记录。
- 同一测试覆盖普通请求及无设计权限的预览参数请求仍使用发布快照，原始配置不能被草稿编译修改。
- `AiCrudConfigServiceDesignPreviewTest`、`LowcodeRuntimeConfigBuilderTest`、`BusinessObjectDesignerPageSchemaTest` 增量回归。
- 使用 JDK 17：`mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=DynamicCrudServiceChildListTest,AiCrudConfigServiceDesignPreviewTest,LowcodeRuntimeConfigBuilderTest,BusinessObjectDesignerPageSchemaTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- 本轮仅后端配置解析变更，不重复前端 build。真实只读接口可确认数据未丢；修复后若运行服务尚未加载新代码，使用独立只读回放验证，明确不宣称浏览器 E2E 已通过。
