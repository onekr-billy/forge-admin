# Status And Callbacks

Forge business workflow status must be maintained by the business module, not inferred only from Flowable screens.

Java production code must write and compare status through a module enum (`getCode()` / `matches()`), not `"DRAFT"` / `"IN_PROCESS"` string literals or magic numbers. Enable/disable flags use `EnableStatus`. Mapper XML may keep SQL literals. Controller write APIs take typed DTOs; only the flow `variables` field may stay a Map.

## Recommended Status Set

Use these values unless the domain has a strong reason to extend them:

| Status | Meaning | Typical allowed actions |
| --- | --- | --- |
| `DRAFT` | Created but not submitted | edit, delete, submit |
| `IN_PROCESS` | Flow instance is running in approval nodes | view, save allowed node fields |
| `NEED_MODIFY` | Rejected back to applicant modify node | edit permitted fields, resubmit, terminate |
| `APPROVED` | Flow completed successfully | readonly, domain side effects |
| `REJECTED` | Applicant terminated or flow rejected | readonly/delete depending on domain |
| `CANCELED` | Flow canceled/withdrawn/terminated | readonly/delete depending on domain |

## Business Table Fields

Add at least:

```sql
`status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '业务状态',
`business_key` varchar(128) DEFAULT NULL COMMENT '流程业务Key',
`process_instance_id` varchar(128) DEFAULT NULL COMMENT '流程实例ID'
```

Add per-node approver and remark fields only when the business table must retain them for search/reporting. Otherwise task comments can stay in flow task history.

## Callback Contract

Code-first Services should use:

```java
@FlowBind(modelKey = MODEL_KEY, businessType = BUSINESS_TYPE)
public class XxxServiceImpl {

    @FlowCallback(on = {
            FlowCallback.ON_TASK_CREATED,
            FlowCallback.ON_TASK_COMPLETED,
            FlowCallback.ON_COMPLETED,
            FlowCallback.ON_REJECTED,
            FlowCallback.ON_CANCELED
    })
    @Transactional(rollbackFor = Exception.class)
    public void handleFlowEvent(FlowEventContext context) {
        // load by tenant + businessKey; update idempotently
    }
}
```

Required handling:

- `ON_TASK_CREATED`: repair business status from current active node. This is critical for reject-to-modify flows.
- `ON_TASK_COMPLETED`: copy task variables needed by the business and move status for reject/resubmit decisions.
- `ON_COMPLETED`: set approved and copy final variables, but do not assume this event is the only reliable source.
- `ON_REJECTED`: set rejected with reason.
- `ON_CANCELED`: set canceled with reason.

## Repair Rules

Do not trust event ordering as the only source of truth. Add repair at three points:

1. `TASK_CREATED` callback:
   - Applicant modify node: `IN_PROCESS -> NEED_MODIFY`.
   - Approval node: `NEED_MODIFY -> IN_PROCESS`.

2. Task form save:
   - If applicant modify save sees `IN_PROCESS`, repair to `NEED_MODIFY` before validation.
   - If approval save sees `NEED_MODIFY`, repair to `IN_PROCESS` before validation.

3. Page/detail query:
   - For running statuses, query active `sys_flow_task` by `business_key`.
   - Map active `task_def_key` back to expected business status.
   - Persist and return repaired status when drift is found.

## Variable Safety

- Write approval variables before completing the task so gateways and callbacks can read them.
- Merge runtime variables and historic variables for terminal events when implementing flow side logic.
- Business callback must be idempotent and tolerate missing variables.
- Keep `recordId`, `businessKey`, and user IDs as strings in frontend. Do not convert Snowflake IDs to JS `Number`.

## Idempotency

- Flow start must be guarded by `tenantId + businessKey`.
- Low-code platform start already uses locks and `ai_business_flow_instance_link`; do not add duplicate frontend start paths.
- Code-first business Services should prevent double submit by checking status and existing `business_key/process_instance_id`.
- Domain side effects after approval must have their own idempotency key.
