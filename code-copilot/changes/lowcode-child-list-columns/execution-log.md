# 低代码子表列表字段发布修复执行记录

> 本文档按执行轮次追加验证证据。

## 2026-09-20

### 实现结果

- 列表设计器显式选中的子表字段不再因子表对象自身 `listVisible=false` 被发布编译二次过滤。
- 字段抽屉、设计预览和正式运行配置共用“仅显示字段名”的标题语义，并兼容历史的 `子表名 · 字段`、`子表名.字段` 等标题。
- 正式列配置读取 `fieldSettings.title`，保留用户自定义列标题能力。
- 未显式选择时仍沿用默认 `listVisible` 策略；聚合/拆行查询、分页和主表操作去重逻辑未改变。

### 验证记录

1. 前端定向回归：通过。

   ```bash
   pnpm --dir forge-admin-ui --ignore-workspace exec vitest run \
     src/components/lowcode-builder/page/__tests__/page-schema.spec.js \
     -t "shows only the field label|keeps an explicitly selected child column|defaults to aggregate"
   ```

   结果：3 passed，3 skipped。

2. 相关文件 ESLint：通过。

   ```bash
   pnpm --dir forge-admin-ui exec eslint \
     src/components/lowcode-builder/page/page-schema.js \
     src/components/lowcode-builder/page/__tests__/page-schema.spec.js \
     src/components/lowcode-builder/page/ListPageGridDesigner.vue \
     src/components/lowcode-builder/page/FieldConfigDrawer.vue \
     src/views/app-center/components/designer/BusinessListDesigner.vue
   ```

3. 前端生产构建：通过。

   ```bash
   cd forge-admin-ui && pnpm build
   ```

   结果：`✓ built in 1m 12s`。存在存量 CSS `//` 注释和动态导入分包警告，与本轮无关。

4. 完整 `page-schema.spec.js`：本轮新增的 3 个用例及另外 2 个存量用例通过；1 个存量目录标题唯一性用例失败。

   ```text
   expected 59 to be 63
   page-schema.spec.js:39
   ```

   失败来自当前 `listPageBlockCatalog` 的重复标题，与子表字段选择/标题解析无关。

5. 后端定向测试：使用 JDK 17 执行，但被工作区内并行的数据审计代码编译错误阻断，尚未进入本轮 JUnit。

   ```bash
   mvn -q -Penable-tests \
     -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am \
     -Dtest=LowcodeRuntimeConfigBuilderTest#keepsSelectedChildColumnsOutOfMainForm \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   阻断项：

   - `LowcodeRuntimeConfigBuilder.java:283`：并行数据审计改动引用了未定义变量 `context`。
   - `DataAuditValueNormalizer.java:70`：并行新增类引用了作用域外变量 `value`。

   两处均非本变更代码，未擅自修改或回退。

6. `git diff --check`：通过。

## 2026-09-20 · 真实应用配置复查

### 根因证据

- 对象 `2101126754308390913` 的 `listGridLayout` 与 `pages[list].gridLayout` 已保存 3 个子表列：
  `cgou_detail_ujpc__fieldDictSelect`、`cgou_detail_ujpc__updateTime`、
  `cgou_detail_ujpc__fieldNumber`。
- 同一草稿的 `zones[table].fieldRefs` 仍只有 `fieldInput`、`fieldNumber`、`flowStatus`。
- 正式和设计预览运行配置均只生成上述 3 个主表列，证明限制发生在 viewSchema 回写与发布编译链路，
  不是运行页表格组件漏渲染。

### 增量修复

- `BusinessObjectDesignerService` 的 viewSchema 清洗集合改为“主模型字段 + pageSchema.modelRefs 字段”，
  保存设计时不再删除 `modelCode__field`。
- `LowcodeRuntimeConfigBuilder` 优先读取列表 grid 区块的显式选列，历史上已经分叉的草稿无需重新勾字段。
- 发布检查改用页面字段集合，子表视图字段不再被当成无效引用清掉。

### 验证记录

1. JDK 17 后端定向测试：`LowcodeRuntimeConfigBuilderTest` 15/15、
   `BusinessObjectDesignerPageSchemaTest` 17/17 通过。
2. 前端新增 grid/zone 同步用例及子表运行列过滤用例通过。
3. 本轮相关前端文件 ESLint 通过。
4. 前端 `pnpm build` 通过；仅有存量 CSS 注释和动态导入分包警告。
5. `git diff --check` 通过。

## 2026-09-20 · 子表列显示但数据为空

### 根因与范围

- 用户页面的草稿 render 返回 3 个 `cgou_detail_ujpc__...` 子表列；同配置 `page?designPreview=1` 只有主表字段。
- 记录 17 的详情返回 1 条子表明细，外键与主记录一致，数据未丢失。
- 表头的 `buildDraftRenderConfig` 每次编译当前 model/page Schema；数据的 `getConfig` 却直接使用存储的旧 columnsSchema，未进入 JOIN 分支。
- 新增 `AiCrudConfigService.resolveDraftRuntimeConfig` 统一草稿编译结果，返回配置副本；表头和动态 CRUD 共用。正式请求保持发布快照，设计权限判断保持不变。
- 本轮只改上述配置解析入口及新增回归，不修改 SQL 实现、前端、审批状态或业务数据。

### 自动化证据

使用 JDK 17，在 `forge-server` 执行：

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am \
  -Dtest=DynamicCrudServiceChildListTest,AiCrudConfigServiceDesignPreviewTest,LowcodeRuntimeConfigBuilderTest,BusinessObjectDesignerPageSchemaTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- 修复前新用例 5 个中 2 个失败：实际 SQL 为 `SELECT * FROM test_order ...`，没有子表 JOIN/投影；另 3 个权限与发布隔离用例通过。
- 修复后：`DynamicCrudServiceChildListTest` 5/5、`AiCrudConfigServiceDesignPreviewTest` 1/1、`LowcodeRuntimeConfigBuilderTest` 15/15、`BusinessObjectDesignerPageSchemaTest` 17/17，共 38/38 通过。编译同时通过。
- 前期新测试夹具的重载匹配器、主键规则及 SQL 引用格式断言已校正，未把测试准备错误计为业务回归证据。
- `git diff --check` 通过。仅有 JVM CDS 和 commons-logging 存量警告。

### 真实数据只读回放

- 本地临时探针：`/tmp/forge-child-list-readonly.5wWx7s/ForgeChildListReadOnlyProbe.java`。
- 使用当前编译类和测试报告中的 `java.class.path`，读取本地 Admin dev 配置并按 Spring 环境占位符规则解析连接参数，未输出凭据。JDBC 连接显式 `setReadOnly(true)`。
- 从数据库读取实际 `ai_crud_config`；使用真实草稿编译器、`DynamicCrudService.selectPage` 和 `DynamicCrudRepository`。认证/权限上下文、字典标签与公式等外围依赖模拟，因此不宣称覆盖真实浏览器加密链路或所有字段翻译。
- 首次探针的环境占位符与独立 ObjectMapper 默认行为不匹配，修正为 Spring 环境解析及 Spring Jackson 默认设置后，回放成功。
- 聚合模式独立查询原始子表，逐条比较已选字典原值、数字、更新时间与列表投影：

```text
AGGREGATE total=16 checkedCells=48 parentsWithChildren=10 allValuesMatch=true
EXPAND total=17 returnedRows=17 uniqueRowKeys=17 childProjectionPresent=true
READ_ONLY=true noPersistedConfigOrBusinessWrites=true
```

- 6 条主记录确实无子表明细，留空符合预期；拆行模式仅修改探针内存配置，没有保存模式设置。
- 未启动/停止 Admin、Flow、前端服务，未发布应用，未写业务数据。
- 部署动作：用户重启 `ForgeAdminApplication` 加载新代码后刷新当前设计权限页面即可；正式发布入口仍需发布最新列表设计。未对尚未加载新代码的进程宣称接口修复已生效。
- 本轮没有前端变更，不重复前端 build/浏览器验证；已有其他变更交叠，未自动提交工作区。
