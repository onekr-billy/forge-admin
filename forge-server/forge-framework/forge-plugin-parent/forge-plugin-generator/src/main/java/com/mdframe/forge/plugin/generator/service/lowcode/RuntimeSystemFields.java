package com.mdframe.forge.plugin.generator.service.lowcode;

import java.util.Set;

/** Canonical audit and identity fields excluded from editable child rows. */
final class RuntimeSystemFields {

    static final Set<String> FIELD_NAMES = Set.of(
            "id", "tenantId", "createBy", "createTime", "createDept", "updateBy", "updateTime", "delFlag"
    );
    static final Set<String> COLUMN_NAMES = Set.of(
            "id", "tenant_id", "create_by", "create_time", "create_dept", "update_by", "update_time", "del_flag"
    );

    private RuntimeSystemFields() {
    }
}
