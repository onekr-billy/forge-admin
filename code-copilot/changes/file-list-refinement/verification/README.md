# 文件管理页面验证

此目录仅用于开发验收，不进入生产路由。截图内全部为合成测试文件，不能作为真实文件/存储服务验收证据。

## 运行

先在仓库根目录建立本地依赖链接（链接不会被 Git 提交）：

```sh
ln -s "$PWD/forge-admin-ui/node_modules" code-copilot/changes/file-list-refinement/verification/node_modules
cd forge-admin-ui
node node_modules/vite/bin/vite.js --config ../code-copilot/changes/file-list-refinement/verification/vite.config.js
```

另一个终端从仓库根目录运行：

```sh
PLAYWRIGHT_MODULE=/Users/mini32g/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright \
node code-copilot/changes/file-list-refinement/verification/check-ui.cjs
```

也可以设置 PLAYWRIGHT_MODULE 为本机已安装的 Playwright 包绝对路径；默认尝试 `require('playwright')`。浏览器使用已安装的 Chrome，端口固定为 5187。

脚本拦截业务接口，模拟上传、移动和重命名，不访问真实后端。结束后关闭本轮 Vite 服务，删除本地依赖符号链接即可。

## 截图

- list-light / list-dark：文件列表明暗主题。
- grid-light / grid-dark / grid-images：网格与图片分类。
- list-mobile / grid-mobile / groups-mobile：390px 窄屏。
- groups / preview-image / empty-search：分组、图片预览及无匹配结果。
