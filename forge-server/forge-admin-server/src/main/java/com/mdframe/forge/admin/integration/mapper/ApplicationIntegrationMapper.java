package com.mdframe.forge.admin.integration.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.admin.integration.dto.ApplicationIntegrationConfig;
import com.mdframe.forge.admin.integration.dto.ApplicationCapabilityCandidate;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApplicationIntegrationMapper {
    ApplicationIntegrationConfig config(@Param("tenantId") Long tenantId, @Param("appId") Long appId);
    int initialize(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("appId") Long appId,
                   @Param("userId") Long userId, @Param("deptId") Long deptId);
    int save(@Param("tenantId") Long tenantId, @Param("appId") Long appId,
             @Param("config") ApplicationIntegrationConfig config, @Param("userId") Long userId);
    int attach(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("appId") Long appId,
               @Param("capabilityId") Long capabilityId, @Param("userId") Long userId, @Param("deptId") Long deptId);
    Long member(@Param("tenantId") Long tenantId, @Param("appId") Long appId, @Param("capabilityId") Long capabilityId);
    Page<AiCapability> capabilities(Page<AiCapability> page, @Param("tenantId") Long tenantId,
                                    @Param("appId") Long appId, @Param("keyword") String keyword);
    List<ApplicationCapabilityCandidate> unattachedPublishedCandidates(
            @Param("tenantId") Long tenantId,
            @Param("appId") Long appId);
    List<ApplicationCapabilityCandidate> applicationPublishedCandidates(
            @Param("tenantId") Long tenantId,
            @Param("appId") Long appId);
    List<ApplicationCapabilityCandidate> applicationSourceCapabilities(
            @Param("tenantId") Long tenantId,
            @Param("appId") Long appId,
            @Param("sourceType") String sourceType,
            @Param("sourceKey") String sourceKey);
}
