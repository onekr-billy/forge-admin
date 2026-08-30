# Flow Enhancement Suite Fix Implementation Plan

> **For agentic workers:** Execute this plan inline in the current workspace, preserving unrelated user changes.

**Goal:** Fix the reviewed Flowable workflow enhancement defects without changing the requested workflow semantics.

**Architecture:** Keep BPMN ownership in the designer and Flowable state changes in the Flow service. Add the missing assignee binding, constrain return/direct-send operations to the intended task branch, validate identities and tenant-scoped model lookups at service boundaries, and protect server-owned business variables.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus/XML, Flowable 7, Vue 3, Vitest, Maven.

---

### Task 1: Bind initiator-selected multi-instance tasks to each selected user

**Files:**
- Modify: `forge-admin-ui/src/components/flow-designer/converter/user-task-writer.js:68-183`
- Test: `forge-admin-ui/src/components/flow-designer/converter/__tests__/json-to-bpmn.spec.js`

- [x] Add `flowable:assignee="${assignee}"` only for `initiatorSelect` user tasks, preserving ordinary assignee and candidate-user output.
- [x] Extend the existing converter assertion to require both the `PROCESS_START_USER` collection and the assignee expression.
- [x] Run the focused Vitest converter tests.

### Task 2: Make return and direct-send operations branch-safe

**Files:**
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowTaskServiceImpl.java:462-769,1841-2000`
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/entity/FlowTask.java`
- Test: flow service unit/integration tests under `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/test`

- [x] Record the intended return task/activity when rejecting to start and expose direct-send only when the current task is the persisted return target and the pending path is active.
- [x] Require the current task to be the workflow starter for the reject-to-start direct-send path; preserve ordinary historical-node direct-send behavior.
- [x] Replace “move every active activity to one activity” with a single-task/targeted migration strategy; reject ambiguous multi-branch active states instead of silently merging parallel branches.
- [x] Keep local task status and runtime marker cleanup transactional.
- [x] Add tests for a valid serial path and an ambiguous parallel path.

### Task 3: Validate reassignment target identity and tenant

**Files:**
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowTaskServiceImpl.java:640-681`
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowInstanceServiceImpl.java:602-640`
- Test: flow service tests under `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/test`

- [x] Resolve the target user through the organization integration service and reject missing, inactive, or tenant-inconsistent users before changing Flowable state.
- [x] Apply the same validation to the administrator reassignment entry point so the two APIs cannot diverge.
- [x] Preserve owner-before-assignee ordering and pending local status.

### Task 4: Protect server-owned business variables

**Files:**
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowService.java:159-163,3742-3754`
- Test: generator business flow tests under `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/test`

- [x] Define a local reserved-variable set for object/config/record/business identifiers and reject client variables that attempt to override them.
- [x] Merge allowed dynamic variables, then force server-owned values last; preserve `PROCESS_START_USER` and other documented dynamic variables.
- [x] Cover both ordinary start and low-code/manual start paths.

### Task 5: Enforce tenant-scoped model lookup and copy return policy

**Files:**
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/mapper/FlowModelMapper.java`
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/resources/mapper/FlowModelMapper.xml`
- Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowModelServiceImpl.java:135-220,505-656`
- Modify: `forge-server/forge-flow/forge-flow-server/src/main/java/com/mdframe/forge/flow/controller/FlowModelController.java`

- [x] Add tenant-aware XML queries for model key and key-existence checks with `del_flag = 0`.
- [x] Pass the trusted current tenant into start-config/model-key service calls even though the controller is an `@IgnoreTenant` boundary.
- [x] Handle database uniqueness races as a duplicate-key business error.
- [x] Copy `allowMultiReturn` with the rest of model policy.

### Task 6: Incremental verification and change records

**Files:**
- Modify: `code-copilot/changes/flow-enhancement-suite/test-spec.md`
- Modify: `code-copilot/changes/flow-enhancement-suite/execution-log.md`
- Modify: `code-copilot/changes/flow-enhancement-suite/tasks.md`

- [x] Run `git diff --check`, focused Vitest, targeted Maven compilation, and any new unit tests.
- [x] Run frontend ESLint/build for touched files.
- [x] Record commands, warnings, skipped real-service E2E, and final status in the existing execution log.
