# 用户列表排序与角色授权全选 Execution Log

> 变更名：`system-user-sort-and-role-checkall`
> 执行时间：2026-09-18

## 1. 改动明细

| 文件 | 改动 |
|------|------|
| `forge-server/.../resources/mapper/SysUserMapper.xml` | `selectUserPage`/`selectExportList` 的 `ORDER BY u.create_time DESC` 替换为 `<choose>` 分支：租户视图按 `CASE ... sut_current.member_type ...`（与 user_type 显示列同口径），非租户视图按 `u.user_type` 升序；两者均再按 `create_time DESC` |
| `forge-server/.../test/.../SysUserMapperSqlContractTest.java` | 新增 `userListMustSortAdminUserTypesFirst` 契约断言（两查询均须含两种排序口径） |
| `forge-admin-ui/src/views/system/components/RolePermissionSettings.vue` | 工具栏新增 `batch-pill-control` 全选/清空按钮组；新增 `globalBatchResourceIds`/`globalSelectAllDisabled`/`globalClearDisabled` computed；样式选择器与 `collapse-pill-control` 共享（基础/暗色/响应式） |

## 2. 验证记录

```bash
# 后端：SQL 契约测试（含新增用例）
cd forge-server && mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-system test -Penable-tests -Dtest='SysUserMapperSqlContractTest'
# Tests run: 2, Failures: 0, Errors: 0 — BUILD SUCCESS

# 前端：eslint
pnpm exec eslint src/views/system/components/RolePermissionSettings.vue
# 无输出（通过）

# 前端：用户管理组件测试（确认无回归）
pnpm exec vitest run src/views/system/__tests__/user-management-components.spec.js
# Tests 5 passed (5)
```

## 3. 备注

- 排序改动同时覆盖分页与导出（两者共用 `queryCondition` 与新排序），列表显示与导出文件顺序一致。
- 全选按钮复用既有 `selectAllPermissions()`/`clearAllPermissions()`，与"全局授权"下拉同源，不改变保存接口与数据结构。
- 提示：插件模块改动需 `mvn install` 后再从 admin-server 启动，避免跑旧代码。
