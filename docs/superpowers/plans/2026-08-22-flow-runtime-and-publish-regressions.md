# Flow Runtime and Publish Regressions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Execute the tasks below with test-first checkpoints.

**Goal:** Make low-code application publish targets persist per client, make event-triggered business processes start after record saves, make approval action nodes resolve the published runtime object configuration, and make H5 dynamic forms preserve range fields and writable-field metadata.

**Architecture:** Keep the application page publish schema as the source of truth for management/H5 mount targets and compile each client target into its corresponding `sys_resource` menu during publish. Keep event starts in the low-code business-process orchestrator, invoked from the successful CRUD write path after the record ID is known. Resolve action-node targets through the immutable published business-object snapshot rather than the generic fallback code. Reuse the shared form schema compiler for H5 and normalize range components into the two-value runtime shape before save.

**Tech Stack:** Vue 3, Vitest, Playwright, Spring Boot 3, MyBatis-Plus, Flowable, Flyway, low-code runtime JSON schemas.

---

### Task 1: Trace and persist client-specific publish mounts

**Files:**
- Inspect/modify `forge-admin-ui/src/views/app-center/components/designer/PageDesignPublishPanel.vue` and `forge-admin-ui/src/views/app-center/application-runtime.[applicationCode].vue`.
- Inspect/modify `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationPageMenuPublishService.java` and related DTO/VO/Mapper XML files.
- Inspect/modify `forge-admin-server/src/main/java/com/mdframe/forge/admin/bridge/MenuRegisterAdapterImpl.java` only if menu registration drops client targets.
- Test with `forge-admin-ui/src/views/app-center/__tests__/page-design-tabs.spec.js` and a focused publish-target contract test.

- [ ] Reproduce by setting management and H5 mounts, reloading the publish page, and inspecting the saved page node plus generated `sys_resource` rows.
- [ ] Add a failing contract test proving `ADMIN` and `H5` mount target/config survive save and publish independently.
- [ ] Preserve the publish DTO fields for `clientCode`, `mountTarget`, `menuParentId`, `menuName`, and `menuSort` through the API and snapshot.
- [ ] Compile management resources using the management client code and H5 resources using client code `h5`; do not fall back to one global menu target.
- [ ] Ensure empty client targets do not delete an existing other-client menu and ensure republishing is idempotent.
- [ ] Run the focused frontend/backend tests and `git diff --check`.

### Task 2: Start event-triggered processes after successful CRUD writes

**Files:**
- Inspect/modify `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/DynamicCrudService.java`.
- Inspect/modify `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessprocess/BusinessProcessOrchestrator.java` and its event/trigger collaborators.
- Inspect/modify relevant Mapper XML only when the event lookup requires a missing published-state predicate.
- Test with `BusinessProcessOrchestratorTest`, `DynamicCrudService` tests, and an event-start integration contract.

- [ ] Add a failing test showing a successful insert/update invokes the event-start path with the persisted string record ID and tenant/application context.
- [ ] Invoke event starts only after the transaction has a durable record ID; do not invoke on validation or database failure.
- [ ] Match `RECORD_CREATED`, `RECORD_UPDATED`, `STATUS_CHANGED`, `FIELD_CHANGED`, and `FORM_SUBMITTED` using the normalized low-code event configuration.
- [ ] Keep duplicate event delivery idempotent by using the existing business key/process binding checks.
- [ ] Verify that the event path does not require a manual start button position or a browser-only context.

### Task 3: Resolve approval action-node targets from the published object snapshot

**Files:**
- Inspect/modify `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessprocess/BusinessProcessActionExecutor.java` and `BusinessProcessApprovalResultListener.java`.
- Inspect/modify `BusinessActionExecutionService`/runtime config resolver and their Mapper XML contracts.
- Test with `BusinessProcessActionExecutorTest` and a regression test for target code `business_object` resolving to the process subject object’s published snapshot.

- [ ] Add a failing test for an approval result action configured with a target object whose code is the generic fallback `business_object`.
- [ ] Resolve the action target in this order: explicit action object code, process subject object code, then reject with a diagnostic containing process/node/action IDs.
- [ ] Load only an immutable published runtime/design snapshot; never execute from draft designer options.
- [ ] Preserve tenant context and string IDs through the executor.
- [ ] Run focused generator tests and verify the existing missing-published-config error remains for truly unavailable objects.

### Task 4: Preserve range controls and writable fields in H5 dynamic forms

**Files:**
- Inspect/modify `forge-h5-ui/src/utils/business-task-form-adapter.js` and the H5 dynamic form renderer/components.
- Inspect/modify shared low-code form schema normalization/compiler used by admin and H5.
- Inspect/modify `DynamicCrudService`/business document configuration lookup only if H5 submits an object code without a published document config.
- Test with H5 form adapter Vitest tests and a runtime save contract for a range field.

- [ ] Add a failing test for a range component whose schema contains two endpoints, asserting H5 renders two controls and serializes both values.
- [ ] Normalize legacy single-value range payloads without dropping the second endpoint, and preserve component visibility/readonly metadata.
- [ ] Ensure the H5 submit payload uses the published form/object field registry so `DynamicCrudService.insert` receives writable fields instead of an empty map.
- [ ] Return a user-facing validation message when no writable fields exist, while keeping the server diagnostic useful.
- [ ] Run focused H5/generator tests and `git diff --check`.

### Task 5: Incremental verification and handoff

- [ ] Read and update `code-copilot/changes/flow-notify-matrix/execution-log.md` with commands, results, skipped environment checks, and service cleanup.
- [ ] Run only targeted Vitest/Maven tests and lint required by the changed files; do not run a production build unless explicitly requested.
- [ ] Review the final diff for unrelated worktree changes and report the exact configuration paths plus any database/service E2E still requiring the user’s environment.
