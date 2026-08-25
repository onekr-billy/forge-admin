# Business Event Flow and Publish Status Fix Plan

**Goal:** 修复动态业务数据新增/保存后未触发增强规则或事件型业务流程，以及应用发布成功后仍显示“有变更未发布”。

**Architecture:** 动态 CRUD 成功后继续以 `BusinessEvent` 作为统一事件入口；所有事件条件读取同时兼容单表记录和主子表聚合结果的 `main` 记录。事件型业务流程按已发布流程版本和对象编码匹配，增强中的 `START_FLOW` 继续复用对象主流程绑定。应用发布提交以 `ai_business_application.design_status/last_publish_version` 为最终事实，幂等重入也必须将状态收敛为已发布。

**Constraints:** 保留工作区已有改动；复杂查询继续放 Mapper XML；本轮不执行完整 Maven/Vite 构建，只运行定向测试和静态检查。

### Task 1: 保存事件与流程触发

**Files:**
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessTriggerExecutor.java`
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessprocess/BusinessProcessOrchestrator.java`
- Inspect/modify if required: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/resources/mapper/BusinessProcessVersionMapper.xml`
- Test: focused generator module unit tests

- [x] Add regression coverage for `recordData.main` condition matching.
- [x] Make enhanced-trigger and business-process condition readers share the same flat/main-field semantics.
- [x] Verify `RECORD_CREATED` and `FORM_SUBMITTED` select and start current published processes.
- [x] Verify `START_FLOW` receives the event tenant, record ID and main-record variables.

### Task 2: 发布状态收敛

**Files:**
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationVersionService.java`
- Test: focused application version service test

- [x] Add a regression test for an existing immutable version while the application is `CHANGED`.
- [x] Ensure both new-version and idempotent existing-version branches call `markPublished`.
- [x] Keep snapshot hash collision protection unchanged.

### Task 3: 定向验证与记录

**Files:**
- Modify: `code-copilot/changes/flow-notify-matrix/test-spec.md`
- Modify: `code-copilot/changes/flow-notify-matrix/execution-log.md`

- [x] Run only the focused unit tests needed for the changed services.
- [x] Run `git diff --check` on the edited files.
- [x] Record commands, results and the skipped full build.
