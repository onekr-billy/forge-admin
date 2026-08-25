package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface BusinessProcessVersionMapper extends BaseMapper<AiBusinessProcessVersion> {

    AiBusinessProcessVersion selectPublishedVersion(@Param("tenantId") Long tenantId,
                                                     @Param("processId") Long processId,
                                                     @Param("versionNo") Integer versionNo);

    AiBusinessProcessVersion selectPublishedVersionById(@Param("tenantId") Long tenantId,
                                                         @Param("versionId") Long versionId);

    AiBusinessProcessVersion selectPublishedForApplicationVersion(
            @Param("tenantId") Long tenantId,
            @Param("processId") Long processId,
            @Param("applicationVersion") Integer applicationVersion);

    List<AiBusinessProcessVersion> selectVersions(@Param("tenantId") Long tenantId,
                                                   @Param("processId") Long processId);

    List<AiBusinessProcessVersion> selectPublishedByApplication(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("processIds") Collection<Long> processIds);

    Integer selectMaxVersionNo(@Param("tenantId") Long tenantId,
                               @Param("processId") Long processId);

    Long countActiveReferences(@Param("tenantId") Long tenantId,
                               @Param("processId") Long processId);

    List<AiBusinessProcessVersion> selectCurrentPublishedByApplication(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId);

    List<AiBusinessProcessVersion> selectCurrentPublishedBySubjectObjectCode(
            @Param("tenantId") Long tenantId,
            @Param("objectCode") String objectCode);

    int insertImmutable(AiBusinessProcessVersion version);
}
