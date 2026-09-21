package com.mdframe.forge.plugin.capability.secureaction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

/** 表单建单与幂等回执在同一个主库事务内提交。回执仅保存摘要和记录 ID。 */
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
