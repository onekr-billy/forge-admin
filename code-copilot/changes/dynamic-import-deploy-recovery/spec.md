# 动态模块部署切换恢复

## 背景

生产环境发布新版前端后，仍打开旧页面的浏览器会继续引用旧构建哈希。用户首次进入尚未加载的懒加载页面时，请求已被新镜像删除的旧 chunk，出现 `Failed to fetch dynamically imported module`。

线上检查同时确认，不存在的 `/forge/assets/*.js` 被 Nginx SPA fallback 返回为 `200 text/html`，使浏览器把 HTML 当作 JavaScript 模块加载。

## 目标

- 哈希静态资源不存在时返回 404，不回退到 `index.html`。
- `index.html` 不被浏览器或中间代理长期缓存；带内容哈希的资源允许长期缓存。
- 版本切换导致的动态模块加载失败自动刷新一次，获取最新版入口文件，并避免刷新死循环。

## 非目标

- 不处理普通 API 网络失败。
- 不改变业务页面和存储配置功能。
- 不执行线上部署。

## 验收标准

- 动态导入错误能够被识别并触发一次刷新，60 秒内重复失败不再次刷新。
- Vite `vite:preloadError` 和 Vue Router 异步路由错误均接入恢复逻辑。
- 两套 Docker Nginx 配置对 `/forge/assets/` 使用严格文件匹配，对 SPA HTML 使用禁止缓存响应头。
- 前端单测、生产构建、Nginx 配置校验及 `git diff --check` 通过。
