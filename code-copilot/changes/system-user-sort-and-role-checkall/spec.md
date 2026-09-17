# 用户列表管理员优先排序与角色授权全选 Spec

> 变更名：`system-user-sort-and-role-checkall`
> 状态：`apply`
> 创建日期：2026-09-18

## 1. 需求

1. **用户列表按用户类型排序**：系统管理员（0）、租户管理员（1）排在普通用户（2）前面。
2. **角色授权支持全选**：授权弹窗提供显眼的"全选/清空"一级操作。

## 2. 方案

### F1 用户类型优先排序（后端）

`SysUserMapper.xml` 的 `selectUserPage` / `selectExportList` 排序改为：

- 非租户视图：`ORDER BY u.user_type, u.create_time DESC`
- 租户视图：`ORDER BY CASE WHEN u.user_type = 0 THEN 0 WHEN sut_current.member_type = 1 THEN 1 ELSE 2 END, u.create_time DESC`

关键点：租户视图下 `user_type` 是计算列（SELECT 中"租户管理员"判定来自 `sut_current.member_type`，与 `Base_Column_List` 口径一致），直接按 `u.user_type` 排会出现**显示与排序不一致**，因此必须用 `<choose>` 分支隔离（`sut_current` 仅在租户视图 JOIN）。

### F2 角色授权全选/清空（前端）

`RolePermissionSettings.vue` 已有 `selectAllPermissions()` / `clearAllPermissions()`，但藏在侧栏"全局授权"下拉第二项，入口隐蔽。改动：主工具栏（展开/折叠旁）新增"全选/清空"pill 按钮组，复用 `collapse-pill-control` 样式体系（含 dark 模式与响应式）。

- 全选禁用：loading / 无权限项 / 已全部选中
- 清空禁用：loading / 当前无任何选中

## 3. 影响范围

| 范围 | 说明 |
|------|------|
| 改动文件 | `SysUserMapper.xml`、`SysUserMapperSqlContractTest.java`、`RolePermissionSettings.vue` |
| 行为变化 | 用户分页/导出列表排序变为用户类型升序优先；授权弹窗多一组一级按钮 |
| 不变 | 查询条件、返回结构、既有"全局授权"下拉、模块级/页面级批量操作、保存接口 |
| 无 DB 变更 | 无表结构、无 Flyway 脚本 |

## 4. 验收

- [x] 用户列表第一页前部为系统管理员、其次租户管理员，普通用户靠后；同类型内仍按创建时间倒序
- [x] 租户管理员视角查看本租户用户，列表中的"租户管理员"排在普通用户前（与列显示口径一致）
- [x] 授权弹窗工具栏出现"全选/清空"；点击全选后所有模块菜单+功能勾选；清空后全部取消；按钮禁用态正确
