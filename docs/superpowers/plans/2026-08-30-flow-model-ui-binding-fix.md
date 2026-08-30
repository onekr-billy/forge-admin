# 流程模型配置与业务绑定修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Inline execution in the current session.

**Goal:** 优化新增流程模型配置表单的布局与默认待办地址，并修复流程模型卡片业务绑定状态误显示为“未绑定”。

**Architecture:** 保持现有 `/flow/model` 与 `/flow/design` 页面职责不变。新增模型弹窗仅调整表单布局和默认值；绑定状态继续通过业务流程绑定接口补充，但兼容接口返回的对象编码、模型 Key 和应用关联字段。

**Tech Stack:** Vue 3、Naive UI、Vite、Vitest、Forge Flow API。

---

### Task 1: 优化新增流程模型配置布局

**Files:**
- Modify: `forge-admin-ui/src/views/flow/model.vue`

- [x] 将模型 Key 输入区域改为独占整行并保留说明文字，避免两列布局挤压。
- [x] 将审批退回、待办跳转等低频配置移动到流程设计页的“更多配置”区域。
- [x] 新增模型时为待办跳转填充系统默认地址；编辑已有模型时保留已保存值。

### Task 2: 修复流程列表业务绑定展示

**Files:**
- Modify: `forge-admin-ui/src/views/flow/model.vue`
- Inspect: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/controller/BusinessFlowController.java`

- [x] 核对绑定接口返回结构与前端读取字段。
- [x] 按模型 Key 查询绑定，并从实际绑定字段生成卡片展示文案。
- [x] 对接口异常、空绑定和旧数据字段提供稳定降级。

### Task 3: 验证

- [x] 执行 `pnpm exec eslint src/views/flow/model.vue src/views/flow/design.vue`。
- [x] 执行相关 Vitest 测试。
- [x] 执行 `pnpm build`、后端模块编译、XML 语法检查与 `git diff --check`。
