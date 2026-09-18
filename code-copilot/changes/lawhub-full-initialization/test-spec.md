# Test Spec

## 静态完整性

- 全量预设选择当前主干全部模块。
- 生成结果不含旧 Maven artifact/module 前缀和旧 Java 包。
- 数据库 SQL 保留日志表 DDL，但日志/历史类表无初始化 INSERT。
- Docker Compose 配置可解析，敏感值只由服务器环境变量注入。

## 构建验证

- 后端：`mvn -DskipTests package`。
- 管理端：`pnpm --ignore-workspace build`。
- 报表端：`pnpm --ignore-workspace build`。

## 部署验证

- `docker compose ps` 中 LawHub 服务健康，重启策略为 `unless-stopped`。
- 通过 Nginx 80 端口访问管理端入口返回成功。
- 使用真实浏览器和默认管理员凭证完成登录、工作区选择并进入首页。
- 首页能够取得用户信息和权限菜单，显示“超级管理员”。
- 应用首次启动前抽查日志/运行历史类表均为空；登录后产生的当前在线会话不属于初始化数据。
