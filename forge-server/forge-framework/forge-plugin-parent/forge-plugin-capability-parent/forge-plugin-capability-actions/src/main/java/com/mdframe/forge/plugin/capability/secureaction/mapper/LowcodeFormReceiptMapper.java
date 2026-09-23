package com.mdframe.forge.plugin.capability.secureaction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

/** 平台表单调用回执，仅保存摘要和记录 ID；跨库请求的空回执表示已占位但结果未确认。 */
@Mapper
public interface LowcodeFormReceiptMapper {
    int reserve(@Param("id") Long id, @Param("tenantId") Long tenantId,
                @Param("clientId") Long clientId, @Param("capabilityId") Long capabilityId,
                @Param("keyHash") String keyHash, @Param("digest") String digest,
                @Param("userId") Long userId, @Param("orgId") Long orgId);

    Map<String, Object> lock(@Param("tenantId") Long tenantId, @Param("clientId") Long clientId,
                             @Param("capabilityId") Long capabilityId, @Param("keyHash") String keyHash);

    int complete(@Param("tenantId") Long tenantId, @Param("clientId") Long clientId,
                 @Param("capabilityId") Long capabilityId, @Param("keyHash") String keyHash,
                 @Param("recordId") String recordId, @Param("userId") Long userId);
}
