# Business Process Action, Title and Status Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make low-code workflow actions resolve a published target object, show business names in the designer, preserve the configured approval title, and recognize an existing flow status field.

**Architecture:** Keep the workflow runtime generic. Resolve action targets through the published business-object/CRUD metadata chain, retain object codes only as internal values, and normalize status-field aliases at the designer boundary. Approval titles remain owned by the approval-node configuration and are passed through the existing business-flow start API.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus/XML mappers, Vue 3, Vitest, ESLint.

---

### Task 1: Trace and fix action target resolution

**Files:**
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessprocess/BusinessProcessActionExecutor.java`
- Inspect/modify as needed: `AiCrudConfigMapper.java`, `AiCrudConfigMapper.xml`
- Test: `BusinessProcessActionExecutorTest.java`

- [x] Add regression tests for generic/legacy action targets, including application-primary fallback, and use their published runtime configuration.
- [x] Extend resolution only through existing published metadata (`objectCode`, `configKey`, object id/snapshot where available); keep the final lookup restricted to published low-code CRUD configs.
- [x] Preserve a clear failure message containing the resolved business object code when no published runtime exists.
- [x] Run focused executor tests and a Java 17 module compile.

### Task 2: Show business names for action target options

**Files:**
- Modify: `forge-admin-ui/src/components/business-process-designer/ActionAndApprovalNodeConfig.vue`
- Inspect: application-object API/VO used by `businessApplicationObjects`
- Test: `forge-admin-ui/src/components/business-process-designer/__tests__/business-process-designer-workbench.spec.js`

- [x] Keep `objectCode` as the option value but prioritize `objectName`, `pageName`, and other business-name fields for labels.
- [x] Render the current subject option with its business name while retaining the “current subject” meaning.
- [x] Add focused assertions that labels do not fall back to a technical code when a business name is present and that target metadata is persisted.

### Task 3: Preserve configured approval title

**Files:**
- Modify: `BusinessProcessOrchestrator.java` only if tracing proves the title is dropped there.
- Inspect/modify: `BusinessFlowService.java` title handling if the start DTO/title is overwritten.
- Test: `BusinessProcessOrchestratorTest.java` or existing business-flow service tests.

- [x] Trace the title from published node `config.titleTemplate` into `startFromBusinessProcess` and the persisted business-flow/task title.
- [x] Ensure a configured title template wins over the BPMN user-task name and is not replaced by a node fallback such as “打卡”; an absent node title now delegates to the binding title template.
- [x] Add a regression assertion for the configured approval title.

### Task 4: Recognize existing flow status fields

**Files:**
- Modify: `forge-admin-ui/src/components/business-process-designer/ActionAndApprovalNodeConfig.vue`
- Inspect/modify: `BusinessFlowStatusFieldService.java` only if backend field aliases are missing.
- Test: business-process designer tests and/or status-field service tests.

- [x] Normalize camelCase and snake_case aliases (`flowStatus`/`flow_status`) and all actual field metadata keys before deciding whether the field exists.
- [x] Preserve the existing refresh event path and update the field list used by the node drawer.
- [x] Keep the “add flow status” prompt hidden when either alias is already present, and keep ensure operation idempotent.

### Validation

- [x] Run `git diff --check`.
- [x] Run targeted frontend lint/test commands with Node 20.19.0.
- [x] Run targeted backend test/compile commands only; no full Maven or Vite build was run.
