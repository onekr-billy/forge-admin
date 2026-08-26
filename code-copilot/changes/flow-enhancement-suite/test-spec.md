# 验证基线

## 后端

- `cd forge-server && mvn -pl forge-flow/forge-flow-server -am -DskipTests compile`
- `cd forge-server && mvn -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am -DskipTests compile`

## 前端

- `source ~/.nvm/nvm.sh && nvm use v20.19.0`
- `cd forge-admin-ui && ./node_modules/.bin/vitest run src/components/flow-designer/converter/__tests__/json-to-bpmn.spec.js src/components/flow-designer/converter/__tests__/user-task-parser-assignee.spec.js`
- `cd forge-admin-ui && ./node_modules/.bin/eslint <本轮修改的前端文件>`
- `cd forge-admin-ui && NODE_OPTIONS=--max-old-space-size=8192 npm run build`

## 关键断言

- 非空 BPMN XML 不被模型初始化覆盖。
- `initiatorSelect` 节点输出 `PROCESS_START_USER` 多实例集合表达式，且 XML 往返保留配置。
- 不开启多级退回时指定非上一节点被拒绝，开启后同流程历史用户任务可退回。
- 改派先写 owner 再写 assignee，本地任务状态仍为待处理。
- 自定义模型 Key 保存并拒绝非法/重复 Key。

仓库当前 `pnpm-workspace.yaml` 未声明 `packages`，本机 pnpm 8 执行 `pnpm exec` 会报 `packages field missing or empty`；本轮使用同一 `node_modules` 下的 Vitest/ESLint 可执行文件和 `npm run build` 完成等价验证。
