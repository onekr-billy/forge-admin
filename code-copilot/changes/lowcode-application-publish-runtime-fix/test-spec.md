# 测试规格

## 前端定向测试

- 正式运行态：`runtimeInteractive=true` 时，即使未配置 `previewLiveData`，也不是静态预览。
- 设计画布：`runtimeInteractive=false` 且未开启真实数据预览时，是静态预览。
- 设计画布真实预览：配置接口并开启 `previewLiveData` 后，不是静态预览。
- 已发布运行配置保留 `childrenConfig`。

## 后端定向测试

- 根 `components` 中的子表可被发现。
- 嵌套布局 `children` 中的子表可被发现。
- `forms[*].schema.components` 中的子表可被发现。
- 对象发布调用子表关系同步后再应用关系和保存草稿。
- 应用发布对象步骤对选中对象执行发布状态后置校验。

## 静态验证

- 前端相关 Vitest。
- 前端相关 ESLint。
- generator 模块定向 Maven 测试。
- generator 模块主代码编译。
- `git diff --check`。

## 本轮增量验证（2026-09-22）

- `BusinessObjectDesignerService.prepareRuntimeDraft`：关系同步和草稿保存必须使用 `REQUIRES_NEW` 短事务，schema 编译不得处于事务内。
- 并发契约：同一业务对象的预览准备使用带引用计数的 JVM 锁，避免并发 `designPreview` 重复更新关系记录或清理锁时产生第二把锁。
- 关系配置契约：JSON 键序/空白差异不应触发无意义的 `updateById`。

## 手工验收建议

1. 新建应用表单并绑定一个已配置字段的子表。
2. 保存草稿后发布应用。
3. 刷新应用工作台，确认主对象与子对象发布状态。
4. 从正式应用地址打开列表，确认无静态预览提示。
5. 新增一条包含两行子表的数据，再进入编辑和详情确认回显。
