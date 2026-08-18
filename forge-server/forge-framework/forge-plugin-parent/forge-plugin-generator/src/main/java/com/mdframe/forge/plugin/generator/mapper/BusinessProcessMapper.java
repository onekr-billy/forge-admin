package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessObjectProcessVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface BusinessProcessMapper extends BaseMapper<AiBusinessProcess> {

    Page<AiBusinessProcess> selectProcessPage(Page<AiBusinessProcess> page,
                                               @Param("tenantId") Long tenantId,
                                               @Param("applicationId") Long applicationId,
                                               @Param("keyword") String keyword,
                                               @Param("status") Integer status,
                                               @Param("designStatus") String designStatus);

    AiBusinessProcess selectActiveById(@Param("tenantId") Long tenantId,
                                        @Param("id") Long id);

    AiBusinessProcess selectActiveByCode(@Param("tenantId") Long tenantId,
                                          @Param("applicationId") Long applicationId,
                                          @Param("processCode") String processCode);

    List<AiBusinessProcess> selectByApplicationId(@Param("tenantId") Long tenantId,
                                                   @Param("applicationId") Long applicationId);

    List<BusinessObjectProcessVO> selectBySubjectObjectCode(@Param("tenantId") Long tenantId,
                                                            @Param("objectCode") String objectCode);

    AiBusinessProcess selectForPublish(@Param("tenantId") Long tenantId,
                                       @Param("applicationId") Long applicationId,
                                       @Param("id") Long id);

    AiBusinessProcess selectForProjection(@Param("tenantId") Long tenantId,
                                          @Param("applicationId") Long applicationId,
                                          @Param("id") Long id);

    int updateDraftSchema(@Param("tenantId") Long tenantId,
                          @Param("id") Long id,
                          @Param("schemaJson") String schemaJson,
                          @Param("schemaHash") String schemaHash,
                          @Param("expectedSchemaHash") String expectedSchemaHash,
                          @Param("subjectObjectId") Long subjectObjectId,
                          @Param("subjectObjectCode") String subjectObjectCode,
                          @Param("designStatus") String designStatus,
                          @Param("updateBy") Long updateBy);

    int updateBasicInfo(@Param("tenantId") Long tenantId,
                        @Param("id") Long id,
                        @Param("processName") String processName,
                        @Param("processDescription") String processDescription,
                        @Param("updateBy") Long updateBy);

    int updateStatus(@Param("tenantId") Long tenantId,
                     @Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("updateBy") Long updateBy);

    int updateDesignStatus(@Param("tenantId") Long tenantId,
                           @Param("id") Long id,
                           @Param("expectedSchemaHash") String expectedSchemaHash,
                           @Param("designStatus") String designStatus,
                           @Param("updateBy") Long updateBy);

    int updatePublishedProjection(@Param("tenantId") Long tenantId,
                                  @Param("applicationId") Long applicationId,
                                  @Param("id") Long id,
                                  @Param("versionNo") Integer versionNo,
                                  @Param("schemaHash") String schemaHash,
                                  @Param("updateBy") Long updateBy);

    int clearPublishedProjectionExcept(@Param("tenantId") Long tenantId,
                                       @Param("applicationId") Long applicationId,
                                       @Param("processIds") Collection<Long> processIds,
                                       @Param("updateBy") Long updateBy);

    int logicalDelete(@Param("tenantId") Long tenantId,
                      @Param("id") Long id,
                      @Param("updateBy") Long updateBy);
}
