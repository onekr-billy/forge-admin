# 用户列表管理员优先排序与角色授权全选 Spec

> 变更名：`system-user-sort-and-role-checkall`
> 状态：`review`
> 创建日期：2026-09-18

## 1. 需求

1. **用户列表按用户类型排序**：系统管理员（0）、租户管理员（1）排在普通用户（2）前面。
2. **角色授权支持全选**：授权弹窗提供显眼的"全选/清空"一级操作。
3. **验收反馈修正**：授权树不应显示彻底停用的隐藏模块/菜单；全选操作需支持"某个模块下全选"。

## 2. 方案

### F1 用户类型优先排序（后端）

`SysUserMapper.xml` 的 `selectUserPage` / `selectExportList` 排序改为：

- 非租户视图：`ORDER BY u.user_type, u.create_time DESC`
- 租户视图：`ORDER BY CASE WHEN u.user_type = 0 THEN 0 WHEN sut_current.member_type = 1 THEN 1 ELSE 2 END, u.create_time DESC`

关键点：租户视图下 `user_type` 是计算列（SELECT 中"租户管理员"判定来自 `sut_current.member_type`，与 `Base_Column_List` 口径一致），直接按 `u.user_type` 排会出现**显示与排序不一致**，因此必须用 `<choose>` 分支隔离（`sut_current` 仅在租户视图 JOIN）。

### F2 角色授权模块级全选/清空（前端，按验收反馈修订）

初版为全局"全选/清空"，用户验收后指出真正诉求是"某个模块下面要全选"。修订：

- 工具栏按钮组改为"模块全选/模块清空"，作用于**当前激活模块**的全部菜单入口与功能权限
- 新增 `selectAllInActiveModule()` / `deselectAllInActiveModule()`，从 `workspaceModules` 取当前模块全部 resourceIds 做增删
- 模块全选禁用：loading / 当前模块无可勾选项 / 当前模块已全选
- 模块清空禁用：loading / 当前模块无任何选中
- 跨模块全局批量仍保留在侧栏"全局授权"下拉，不动

### F3 授权树剔除「双隐藏」资源（后端）

`SysResourceServiceImpl.selectAssignableResourceTree` 增加：

```java
wrapper.and(w -> w.ne(SysResource::getVisible, 0).or().ne(SysResource::getMenuStatus, 0));
```

设计权衡（基于库内实测分布，**不能一刀切按 visible 过滤**）：

- 隐藏资源分两类：`visible=0 且 menu_status=0`（19 条，彻底停用/废弃：旧低代码配置、CRM demo、测试菜单）与 `visible=0 但 menu_status=1`（29 条，**功能性隐藏页**：应用工作台、运行应用、草稿预览、设计器等，靠页面跳转访问）
- 只剔除"双隐藏"；功能性隐藏页必须保留在授权树中，否则角色无法勾选其入口权限，授权后访问被拦 403（与既有教训"后台权限加载按 visible 过滤导致隐藏菜单 API 权限丢失"同一原则）
- `buildEntityTree` 从根递归构建，父节点被剔除后子节点自然消失；已验证双隐藏目录子树内仅有废弃 demo 菜单，无误伤

### F4 loadApiPermissions 移除 visible 过滤（后端，附带修复）

排查中发现 `UserLoadServiceImpl.loadApiPermissions` 仍按 `visible=1` 过滤 API 资源（`loadUserPermissions` 已按同一原则修复，此处为漏网残留）：角色绑定了隐藏 API（库内现有 3 个 `visible=0` 的 API）后鉴权层会 403。移除该条件，与 `loadUserPermissions` 口径对齐。

## 3. 影响范围

| 范围 | 说明 |
|------|------|
| 改动文件 | `SysUserMapper.xml`、`SysUserMapperSqlContractTest.java`、`RolePermissionSettings.vue`、`SysResourceServiceImpl.java`、`UserLoadServiceImpl.java`、`SysResourceAssignableTreeContractTest.java` |
| 行为变化 | 用户分页/导出排序用户类型优先；授权弹窗提供模块级全选/清空；授权树不再显示彻底停用资源；隐藏 API 可正常鉴权 |
| 不变 | 查询条件与返回结构、"全局授权"下拉、保存接口、菜单管理树（仍显示全部资源便于维护隐藏项） |
| 无 DB 变更 | 无表结构、无 Flyway 脚本 |

## 4. 验收

- [x] 用户列表第一页前部为系统管理员、其次租户管理员，普通用户靠后；同类型内仍按创建时间倒序
- [x] 租户管理员视角查看本租户用户，列表中的"租户管理员"排在普通用户前（与列显示口径一致）
- [x] 授权弹窗工具栏出现"模块全选/模块清空"；仅作用于当前激活模块，切换模块互不影响；禁用态正确
- [x] 授权树不再出现彻底停用（双隐藏）的废弃目录与 demo 菜单；功能性隐藏页（应用工作台、运行应用等）仍可见可授权
- [x] 角色绑定 `visible=0` 的 API 后接口不再 403
- [x] 菜单管理页树不受影响（仍能维护隐藏菜单）

## 5. 已知数据问题（未在本变更处理）

- `sys_resource` 存在父子循环引用：id 9371「出入管理」与 9372「InOutHistory」互为 `parent_id`（递归 CTE 1001 层爆栈定位）。两者均不可达根、不出现在任何树中，不影响现有功能，但属脏数据，建议后续出修复脚本纠正 `parent_id`；在此之前任何基于 `sys_resource` 的递归 SQL 都会失败。
