---
name: forge-business-flow-development
description: Develop Forge business approval workflows backed by Flowable, using the current sample purchase order approval as the reference. Use when creating or reviewing a code-first or low-code business process in Forge, binding business records to flow models, designing business process status transitions, integrating BusinessCodeFormProvider or low-code forms with task forms, writing Flyway SQL for business tables/dictionaries/app-center/flow bindings, configuring BPMN node form permissions, callbacks, resubmit/reject behavior, and validating end-to-end workflow behavior.
---

# Forge Business Flow Development

## Overview

Use this skill to implement a complete Forge business workflow: business table, status machine, Flowable model binding, task form integration, SQL seed scripts, app-center entry, and validation. The primary reference is the current `sample_purchase_order` approval flow.

## Workflow

1. Identify the implementation mode.
   - Code-first complex business: read `references/code-first-workflow.md`.
   - Low-code runtime business object: read `references/lowcode-workflow.md`.
   - Unsure: default to code-first only when the business needs custom Service logic, custom tables, or non-trivial status repair.

2. Always read the shared references before editing workflow code.
   - `references/purchase-order-reference.md` for the known-good sample and file map.
   - `references/status-and-callbacks.md` for status transitions, callbacks, and repair rules.
   - `references/sql-templates.md` before writing Flyway scripts.
   - `references/bpmn-configuration.md` before creating or changing BPMN node config.
   - `references/validation-checklist.md` before testing or final review.

3. Keep configuration ownership clear.
   - Business app config owns object binding, document/status field, flow model key, title template, and variable mapping.
   - BPMN node config owns node form asset, field permissions, approver policy, reject/return/countersign behavior, and advanced expressions.
   - `ai_business_binding.binding_config.nodeForms` is compatibility fallback, not the primary source for new node configuration.

4. Use these invariants.
   - `businessKey` format: `<objectCode>:<recordId>`.
   - Store `business_key` and `process_instance_id` on the business table.
   - Use string IDs in frontend flow variables and API payloads; never convert Snowflake `Long` IDs to JavaScript `Number`.
   - Business status must be idempotent and repairable from active task nodes.
   - Complex SQL belongs in Mapper XML.
   - Flyway scripts must be repeatable or guarded with `NOT EXISTS`; tenant data uses `tenant_id = 1`.

## Deliverables

When building a new workflow, produce or update:

- Business table migration with workflow fields and status dictionary.
- Entity, DTO, VO, Mapper, Mapper XML, Service, Controller.
- `FlowDefinition` constants and default form asset metadata.
- `BusinessCodeFormProvider` for code-first forms, or low-code form asset config for low-code objects.
- BPMN model or model initialization path with node form permissions and variable expressions.
- App Center suite/object/app seed and `ai_business_binding` flow binding seed.
- Frontend page/API only when code-first business needs a custom business page.
- Validation notes covering compile, SQL checks, task form permissions, approve/reject/resubmit, and readonly done/history view.

## Non-Negotiables

- Do not build a second node configuration UI outside the real flow designer.
- Do not hardcode dictionary options or status labels in Vue pages.
- Controller write APIs must take typed DTOs, not `@RequestBody Map`. Flow `variables` may stay a Map field inside the DTO.
- Java status transitions must use a business status enum (`getCode()` / `matches()`), not string/number literals like `"IN_PROCESS"` or `setStatus(2)`. Enable/disable flags use `EnableStatus`.
- Do not seed business data with `tenant_id = 0`.
- Do not overwrite an existing BPMN XML during sample/init logic just because it differs from code defaults; preserve designer-edited node config.
- Do not rely only on `PROCESS_COMPLETED` variables for final status; task-completed and task-created callbacks must also maintain the business state.
- Do not let Provider save fields that the current node did not mark writable; platform filtering is a guard, Provider logic must still validate business state.

## Reference Routing

Read only the references needed for the task, but read each selected file fully.

- `references/code-first-workflow.md`: custom business module workflow modeled after purchase order.
- `references/lowcode-workflow.md`: dynamic CRUD business object workflow.
- `references/purchase-order-reference.md`: exact files and behavior in the current sample.
- `references/status-and-callbacks.md`: status machine and callback safety.
- `references/bpmn-configuration.md`: BPMN node form/permission/variable configuration.
- `references/sql-templates.md`: complete Flyway SQL template.
- `references/validation-checklist.md`: testing and review checklist.
