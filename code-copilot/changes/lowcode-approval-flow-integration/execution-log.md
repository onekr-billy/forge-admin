# 低代码审批流程衔接优化 Execution Log

## 2026-09-18 实施前基线

- 工作树基线：`main`，仅存在用户原有 `.DS_Store` 修改，本变更不触碰。
- 已确认根因：审批节点组件与工作台同时挂载 `FlowDesignPage`；Flow 模型列表接口忽略 `status`，可设计目录拿不到草稿。
- 本轮不启动真实服务、不修改数据库。

## 2026-09-18 增量验证

### 变更范围

- 前端：审批节点模型选择、新建即绑定、单一内嵌流程设计器、保存/部署目录刷新与过时响应保护。
- 后端：流程模型设计目录支持显式包含草稿，默认下拉仍只返回已发布模型；Generator 设计目录与发布校验目录分离。
- 数据库：无表结构或 Flyway 变更，仅新增 Mapper 查询。

### 已通过

1. 前端定向测试：

   ```bash
   source ~/.nvm/nvm.sh && nvm use v24.21.0 && \
   ./node_modules/.bin/vitest run \
     src/components/business-process-designer/__tests__/business-process-designer-workbench.spec.js \
     src/views/app-center/__tests__/business-process-workspace.spec.js
   ```

   结果：2 个测试文件、48 个用例全部通过。仅有既有 `n-icon` 测试 stub 警告和 Sass legacy API 警告。

2. 前端生产代码 ESLint：

   ```bash
   ./node_modules/.bin/eslint \
     src/components/business-process-designer/ActionAndApprovalNodeConfig.vue \
     'src/views/app-center/business-process.[processId].vue'
   ```

   结果：通过。

3. 前端生产构建：

   ```bash
   NODE_OPTIONS=--max-old-space-size=8192 ./node_modules/.bin/vite build
   ```

   结果：通过，`9269 modules transformed`，`built in 24.55s`。存在项目既有 Vite native config、CSS `//` 注释和 ineffective dynamic import 警告，不阻断构建。

4. Mapper XML 与差异检查：

   ```bash
   xmllint --noout forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/resources/mapper/FlowModelMapper.xml
   git diff --check
   ```

   结果：通过。

### 未执行与原因

- 后端 Flow/Generator 定向测试及 Maven 编译未执行：当前主机 `java -version` 提示无 Java Runtime，`mvn` 命令不存在，也没有 Maven Wrapper。已补充对应 Java 单测，需在具备 JDK 17/Maven 的环境中复跑。
- 真实浏览器与 Admin/Flow 联调未执行：本轮不启动真实服务、不写数据库；组件测试已覆盖单层打开、保存/部署不关闭、关闭后销毁和目录刷新。
- 用户原有 `.DS_Store` 修改保持不动。

### 环境备注

- 项目偏好记录的 Node `v20.19.0` 当前未安装；本机只有 `v24.21.0`。直接使用现有 `node_modules/.bin` 完成测试、ESLint 和构建，未触发依赖重装。
