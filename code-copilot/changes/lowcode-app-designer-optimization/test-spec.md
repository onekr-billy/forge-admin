# 低代码应用设计体验与页面形态优化 Test Spec

## 本轮增量验证（阶段一至四）

### P0

1. 业务对象编码唯一性：同租户新增重复 `objectCode` 被后端拒绝；编辑当前对象保持通过；不同租户可使用相同编码（若现有租户隔离契约允许）。
2. 页面创建 loading：创建请求完成但设计器资产尚未加载时显示设计器占位，不渲染空白容器。
3. 左树右表运行态：正式 `PortalPageRenderer` 和编辑/草稿运行画布都接收树节点事件；节点筛选追加到右表 `publicParams`，清空节点恢复基础查询。

### P1

1. 前端异步唯一校验：输入合法编码后调用校验接口；编码未变化不重复调用；后端冲突错误能映射到表单字段。
2. 页面路由 query 中的雪花 ID 始终按字符串传递。
3. 树接口全量/懒加载：懒加载请求携带字符串 `parentValue`，树节点主键和节点取值不发生 JavaScript 数值精度转换。
4. 子表展示字段、筛选条件的顺序在设计器 schema 与运行时配置中保持一致。

## 已执行命令与结果（2026-09-25）

- 前端定向 Vitest：5 个文件、40 个测试通过；补充运行时流程状态/流程设计器回归 2 个文件、59 个测试通过。
- 前端生产构建：`vite build` 成功；仅有既有 CSS 注释、动态 import 与 chunk 体积警告。
- 后端生成器定向 Maven：`DynamicCrudServiceAutoGenerationTest`、`BusinessObjectControllerTest` 共 7 个测试通过。
- `git diff --check`：通过。
- 定向 ESLint 仍受存量巨型 SFC 的 import 排序、模板缩进、`no-use-before-define` 等问题阻断；本轮新增树过滤代码未产生解析错误，详见 `giant-class-audit.md`。

## 尚未执行

- 未启动 Admin/Flow、MySQL、Redis；未进行真实数据库写入和端到端流程联调。
- 审批状态字段整列消失属于独立专项，需在运行配置发布/流程绑定刷新链路上补充回归用例。

## 命令基线

- 文档/变更：`git diff --check`
- 前端：`source ~/.nvm/nvm.sh && nvm use v20.19.0 && NODE_OPTIONS=--max-old-space-size=8192 pnpm --ignore-workspace build`
- 前端定向测试：`source ~/.nvm/nvm.sh && nvm use v20.19.0 && pnpm --ignore-workspace exec vitest run <spec>`
- 后端插件：`mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests`

## 暂不执行

- 不启动 Admin/Flow、MySQL、Redis，不进行真实数据库写入和端到端流程联调；待用户提供运行环境后按上述 P0 接口场景补验。
