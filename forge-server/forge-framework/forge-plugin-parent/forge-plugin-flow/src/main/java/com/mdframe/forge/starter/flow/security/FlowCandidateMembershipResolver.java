package com.mdframe.forge.starter.flow.security;

import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.flow.service.FlowUserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves the current session's candidate group identifiers once per caller.
 *
 * <p>The returned values cover role IDs, role keys, organization IDs and custom
 * workflow group codes. List queries can bind this bounded collection directly
 * instead of rejoining the role and organization graph for every task row.</p>
 */
@Component
@RequiredArgsConstructor
public class FlowCandidateMembershipResolver {

    private final FlowUserGroupService flowUserGroupService;

    public Set<String> resolveCurrentSessionGroups() {
        Set<String> memberships = new LinkedHashSet<>();
        if (SessionHelper.getRoleIds() != null) {
            SessionHelper.getRoleIds().forEach(id -> memberships.add(String.valueOf(id)));
        }
        if (SessionHelper.getRoleKeys() != null) {
            SessionHelper.getRoleKeys().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(memberships::add);
        }
        if (SessionHelper.getOrgIds() != null) {
            SessionHelper.getOrgIds().forEach(id -> memberships.add(String.valueOf(id)));
        }
        Long userId = SessionHelper.getUserId();
        if (userId != null) {
            flowUserGroupService.resolveGroupCodesByUserId(userId).stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(memberships::add);
        }
        return memberships;
    }
}
