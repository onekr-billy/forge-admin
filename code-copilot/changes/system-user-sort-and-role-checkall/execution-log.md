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

## 4. Fix 轮次：授权树隐藏资源 + 模块级全选（2026-09-18）

### 4.1 验收反馈与根因

- 反馈 1：授权界面里隐藏的模块/菜单仍然显示。根因：`selectAssignableResourceTree` 完全未过滤 `visible`/`menu_status`。
- 反馈 2："某个模块下面要全选"。根因：初版按钮为全局口径，与诉求不符。

### 4.2 数据勘察

- 隐藏资源分两类：`visible=0 且 menu_status=0` 共 19 条（彻底停用：旧低代码配置、CRM demo、测试菜单）；`visible=0 但 menu_status=1` 共 29 条（功能性隐藏页：应用工作台/运行应用/草稿预览/设计器，靠页面跳转访问，必须可授权）。
- 递归验证双隐藏目录子树：仅含废弃 demo 菜单与停用对象按钮，剔除无误伤。
- 附带发现：`sys_resource` 存在父子循环引用（9371「出入管理」↔ 9372「InOutHistory」互为 parent），递归 CTE 1001 层爆栈；两者不可达根、不在授权树中，属脏数据待单独修复。

### 4.3 改动

| 文件 | 改动 |
|------|------|
| `SysResourceServiceImpl.java` | `selectAssignableResourceTree` 增加 `wrapper.and(w -> w.ne(visible,0).or().ne(menuStatus,0))`：仅剔除双隐藏，保留功能性隐藏页（防授权后 403 老坑复发） |
| `UserLoadServiceImpl.java` | `loadApiPermissions` 移除 `.eq(SysResource::getVisible, 1)`（历史坑残留：角色绑定的隐藏 API 鉴权 403），与 `loadUserPermissions` 口径对齐 |
| `SysResourceAssignableTreeContractTest.java`（新增） | 源码契约断言三处口径：授权树剔除双隐藏但保留单隐藏；菜单管理树不过滤 visible；权限加载无 visible 过滤 |
| `RolePermissionSettings.vue` | 工具栏按钮组从全局"全选/清空"改为"模块全选/模块清空"（作用于当前激活模块）；新增 `selectAllInActiveModule()`/`deselectAllInActiveModule()` 与 `moduleSelectAllDisabled`/`moduleClearDisabled`；跨模块全局批量仍保留在"全局授权"下拉 |

### 4.4 验证

```bash
# 后端：授权树契约 + UserLoad 回归
mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-system test -Penable-tests \
  -Dtest='SysResourceAssignableTreeContractTest,UserLoadServiceImplPasswordLoginTest,UserLoadServiceImplCaptchaTest,UserLoadServiceImplExternalIdentityTest'
# Tests run: 6, Failures: 0, Errors: 0 — BUILD SUCCESS

# 前端
pnpm exec eslint src/views/system/components/RolePermissionSettings.vue
pnpm exec vitest run src/views/system/__tests__/user-management-components.spec.js
# eslint 通过；Tests 5 passed (5)
```

## 5. Fix 轮次：角色授权改为菜单目录树（2026-09-18）

### 5.1 变更范围

- `RolePermissionSettings.vue` 不再平铺左侧业务模块，改为消费菜单目录树；目录节点保留嵌套关系，页面入口作为叶子节点。
- 新增 `RolePermissionNavigation.vue`，负责树导航、搜索、目录状态和隐藏页面提示。
- 新增 `role-permission-model.js`，负责资源树转换、目录页面收集、搜索过滤和授权状态计算。
- 隐藏功能页不从权限数据中删除，树节点和页面卡片标记“导航隐藏”；保存的 `resourceIds` 协议不变。

### 5.2 验证

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh && nvm use v20.19.0
pnpm --ignore-workspace exec eslint \
  src/views/system/components/RolePermissionSettings.vue \
  src/views/system/components/RolePermissionNavigation.vue \
  src/views/system/components/role-permission-model.js \
  src/views/system/__tests__/role-permission-navigation.spec.js \
  src/views/app-center/__tests__/role-permission-settings.spec.js
# 无输出（通过）

pnpm --ignore-workspace exec vitest run \
  src/views/system/__tests__/role-permission-navigation.spec.js \
  src/views/app-center/__tests__/role-permission-settings.spec.js
# Test Files 2 passed；Tests 7 passed (7)

git diff --check
# 通过

NODE_OPTIONS=--max-old-space-size=8192 pnpm --ignore-workspace build
# ✓ built in 1m 19s；存在既有 Vite/CSS/动态导入警告，无构建错误
```

### 5.3 未执行项

- 未启动 Admin/Vite 服务做真实浏览器点击验证，避免在用户未授权的情况下启动并连接业务数据库；生产构建已验证模板和打包。
- 目录树轮次未修改上述既有文件；后续解耦轮次已在 `RolePermissionModal.vue`、`role.vue`、`SysRoleServiceImpl.java` 和回归测试中补充接口/页面独立授权处理。

## 6. Fix 轮次：页面入口与接口权限解耦（2026-09-18）

### 6.1 改动

| 文件 | 改动 |
|------|------|
| `RolePermissionSettings.vue` 的调用方（`role.vue`、`RolePermissionModal.vue`） | 系统角色授权默认关闭页面入口与功能/API 联动，允许只授权接口而不授权页面入口 |
| `SysRoleServiceImpl.java` | 角色资源归一化改为：API/按钮只保留自身；明确页面入口时才补齐父级页面/目录；范围化应用授权复用同一规则 |
| `RoleResourceSelectionNormalizer.java` | 抽离页面父链保留规则，避免授权保存与范围化保存出现两套口径 |
| `SysRoleServiceImplBindResourcesTest.java` | 增加 API-only 资源保存回归用例 |
| `RoleResourceSelectionNormalizerTest.java` | 增加 API-only、明确页面入口、孤儿目录 3 个无 Mock 单测 |

### 6.2 验证

```bash
# 前端静态检查
cd forge-admin-ui
source ~/.nvm/nvm.sh && nvm use v20.19.0
pnpm --ignore-workspace exec eslint \
  src/views/system/role.vue \
  src/views/system/components/RolePermissionModal.vue \
  src/views/system/components/RolePermissionSettings.vue \
  src/views/system/components/RolePermissionNavigation.vue \
  src/views/system/components/role-permission-model.js
# 无输出（通过）

pnpm --ignore-workspace exec vitest run \
  src/views/system/__tests__/role-permission-navigation.spec.js \
  src/views/app-center/__tests__/role-permission-settings.spec.js
# Test Files 2 passed；Tests 7 passed

NODE_OPTIONS=--max-old-space-size=8192 pnpm --ignore-workspace build
# ✓ built in 1m；存在既有 Vite/CSS/动态导入警告，无构建错误

# 后端插件模块编译
cd forge-server
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-system -am package -DskipTests
# BUILD SUCCESS

# 核心归一化规则单测（不依赖 Mockito）
mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-system test -Penable-tests \
  -Dtest='RoleResourceSelectionNormalizerTest'
# Tests run: 3, Failures: 0, Errors: 0 — BUILD SUCCESS
```

### 6.3 阻断项

```bash
mvn -o -pl forge-framework/forge-plugin-parent/forge-plugin-system test -Penable-tests \
  -Dtest='SysRoleServiceImplBindResourcesTest,SysRoleServiceImplScopedPermissionTest'
# Tests run: 8, Errors: 8；Mockito inline MockMaker 无法加载，根因是当前 JDK/macOS 无法完成 Byte Buddy agent self-attach。
```

该失败发生在测试初始化阶段，未进入断言；不影响上面的 Java 编译结果。
