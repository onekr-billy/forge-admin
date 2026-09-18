package com.mdframe.forge.plugin.system.service.impl;

import com.mdframe.forge.plugin.system.entity.SysResource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleResourceSelectionNormalizerTest {

    private static final Map<Long, SysResource> RESOURCES = Map.of(
            1L, resource(1L, 1, 0L),
            100L, resource(100L, 1, 1L),
            101L, resource(101L, 2, 100L),
            201L, resource(201L, 3, 101L),
            202L, resource(202L, 4, 101L),
            9001L, resource(9001L, 1, 0L));

    @Test
    void apiOnlyShouldNotRetainItsPageOrDirectoryAncestors() {
        Set<Long> normalized = RoleResourceSelectionNormalizer.normalize(
                Set.of(1L, 100L, 101L, 202L),
                Set.of(202L),
                RESOURCES);

        assertThat(normalized).containsExactly(202L);
    }

    @Test
    void explicitPageShouldRetainItsDirectoryAncestors() {
        Set<Long> normalized = RoleResourceSelectionNormalizer.normalize(
                Set.of(1L, 100L, 101L, 201L),
                Set.of(101L, 201L),
                RESOURCES);

        assertThat(normalized).containsExactlyInAnyOrder(1L, 100L, 101L, 201L);
    }

    @Test
    void orphanDirectoryShouldBeDropped() {
        Set<Long> normalized = RoleResourceSelectionNormalizer.normalize(
                Set.of(9001L),
                Set.of(9001L),
                RESOURCES);

        assertThat(normalized).isEmpty();
    }

    private static SysResource resource(Long id, Integer resourceType, Long parentId) {
        SysResource resource = new SysResource();
        resource.setId(id);
        resource.setResourceType(resourceType);
        resource.setParentId(parentId);
        return resource;
    }
}
