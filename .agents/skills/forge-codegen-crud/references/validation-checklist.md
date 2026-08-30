# Validation Checklist

## Generated SQL

- [ ] Flyway filename uses the next unused version and lower snake case description.
- [ ] Existing-table migrations guard every `DROP INDEX` with `information_schema.STATISTICS` and tolerate renamed, missing, or partially migrated legacy indexes.
- [ ] Business table has `id`, `tenant_id`, `create_by`, `create_time`, `create_dept`, `update_by`, `update_time`.
- [ ] Logical-delete tables contain `del_flag`; no visible `logic_delete_active` generated column is created.
- [ ] A business key that is active-only unique uses `(business_key..., del_flag)` with `BIGINT/Long` and `@TableLogic(delval = "id")`; tables without this requirement do not add the index mechanically.
- [ ] Custom logical-delete SQL for an active-only unique key writes the current primary key, never the fixed value `1`.
- [ ] Active-only uniqueness does not rely on `(business_key, deleted_at)` with `deleted_at = NULL`; MySQL allows multiple `NULL` values in a unique index.
- [ ] Built-in data uses `tenant_id = 1`, never `0`.
- [ ] Every insert has explicit column names.
- [ ] Every dictionary/resource/Excel seed has `NOT EXISTS` protection.
- [ ] Dictionary fields either reuse an existing `dict_type` or add `sys_dict_type` and `sys_dict_data`.
- [ ] Import/export pages include `sys_excel_export_config` and `sys_excel_column_config` rows.
- [ ] `sys_resource` inserts create the menu and required button permissions.

## Backend

- [ ] Controller `@RequestBody` parameters are typed DTO/VO classes, never `Map`.
- [ ] Status and enable/disable writes/comparisons use `EnableStatus` or a module enum (`getCode()` / `matches()`), not `0`/`1`/`"DRAFT"` literals.
- [ ] Controller uses `RespInfo.success(data)` / `RespInfo.success()` and Forge codegen-safe routes.
- [ ] Generated detail/update/delete endpoints use POST (`/getById`, `/edit`, `/remove/{id}`); no `@PutMapping` or `@DeleteMapping` is generated.
- [ ] Pagination uses `PageQuery` or `pageNum` + `pageSize`, not `page`.
- [ ] Query SQL lives in Mapper XML.
- [ ] Mapper XML lists explicit columns and includes standard audit fields.
- [ ] Sensitive or encrypted endpoints use `@ApiDecrypt` and `@ApiEncrypt`.
- [ ] Service methods validate required IDs and uniqueness before writes.
- [ ] No Service-to-Service circular dependency is introduced.
- [ ] Batch delete accepts IDs and rejects empty input.

## Frontend

- [ ] Page uses `AiCrudPage`.
- [ ] `api-config` uses `:id` placeholders, not `{id}`.
- [ ] Generated `api-config` uses POST for detail/update/delete (`post@.../getById`, `post@.../edit`, `post@.../remove/:id`); no `put@` or `delete@` is generated.
- [ ] Dictionary fields use `useDict()`, `DictSelect`, and `DictTag`.
- [ ] Schemas are `computed` when they depend on dictionaries.
- [ ] Import/export props and API config match generated backend or common Excel endpoints.
- [ ] Image/file fields use file IDs and `AuthImage` or `getFileUrl(fileId)`, not raw avatar URLs.
- [ ] Operation links use semantic UnoCSS classes such as `text-primary`, `text-error`, `text-warning`, and `text-success`.

## Verification Commands

Run the narrowest useful checks for the generated scope:

```bash
cd forge && mvn -pl forge-admin-server -am compile -DskipTests
```

```bash
cd forge-admin-ui && source ~/.nvm/nvm.sh && nvm use v20.19.0 && pnpm build
```

If the full commands are too expensive, run module-specific compilation/lint and state what was not run.
