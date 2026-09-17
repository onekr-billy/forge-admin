# 测试计划

## P0

- `dynamic-import-recovery.spec.js`：识别目标异常、60 秒内只刷新一次、恢复时阻止 Vite 重抛。
- `pnpm build:prod`：验证入口、路由和新增工具可被生产模式打包。
- Nginx 配置校验：使用容器内 `nginx -t`（若本机 Docker 可用）。
- `git diff --check`：检查补丁格式。

## P1

- 构建镜像后请求不存在的 `/forge/assets/*.js`，预期 404。
- 请求 `/forge/`，预期包含 `Cache-Control: no-cache, no-store, must-revalidate`。
- 请求现有哈希资源，预期包含 `Cache-Control: public, max-age=31536000, immutable`。

## 跳过边界

- 本轮不部署线上环境；P1 线上复验由部署后执行。
