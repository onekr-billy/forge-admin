# 本轮增量测试计划：角色授权菜单目录树

## P0

- `role-permission-navigation.spec.js`：验证目录层级、页面叶子、隐藏页面保留资源 ID、搜索结果保留父目录。
- `role-permission-settings.spec.js`：验证应用中心传入权限模块的入口/功能独立授权和默认数据范围只读状态不回归。
- `SysRoleServiceImplBindResourcesTest`：验证明确页面入口保留父级目录、API-only 授权不写入页面和目录父级。
- `RoleResourceSelectionNormalizerTest`：不依赖 Mock 框架，直接验证 API-only、页面入口父链和孤儿目录归一化规则。
- `SysRoleServiceImplScopedPermissionTest`：验证应用范围化权限继续使用相同的父级归一化口径。
- ESLint：检查新增导航组件、权限模型和权限配置组件。
- `pnpm --ignore-workspace build`：验证 Vue 模板、动态导入和生产打包。

## P1

- 浏览器真实登录后打开 `/system/role`，检查目录展开、页面叶子选择、隐藏页面标签和目录批量授权。

## 跳过项

- 后端 Mockito inline mock maker 在当前 macOS/JDK 环境无法自附加（Byte Buddy agent attachment），服务集成单测已尝试但被测试运行环境阻断；核心归一化逻辑已由不依赖 Mockito 的 3 个单测覆盖，并使用 `mvn ... package -DskipTests` 完成插件模块编译验证。
- P1 浏览器验证未自动执行，当前环境未启动 Admin 服务和前端开发服务器；生产构建已覆盖模板编译。
