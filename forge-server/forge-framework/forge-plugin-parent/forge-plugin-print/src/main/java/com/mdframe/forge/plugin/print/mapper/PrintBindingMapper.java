package com.mdframe.forge.plugin.print.mapper;

import com.mdframe.forge.plugin.print.entity.PrintBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 所有调用须先做来源授权；多语句写入由服务事务及应用/模板行锁串行化。
 */
@Mapper
public interface PrintBindingMapper {

    int insert(PrintBinding row);

    List<PrintBinding> selectSource(@Param("tenantId") Long tenantId, @Param("applicationId") Long applicationId, @Param("sourceKey") String sourceKey, @Param("scene") String scene);

    long countTemplateReferences(@Param("tenantId") Long tenantId, @Param("templateId") Long templateId);

    int clearDefault(@Param("tenantId") Long tenantId, @Param("applicationId") Long applicationId, @Param("sourceKey") String sourceKey, @Param("scene") String scene, @Param("actor") Long actor);

    int updateBinding(@Param("row") PrintBinding row, @Param("expectedRevision") Long expectedRevision);

    int softDelete(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("expectedRevision") Long expectedRevision, @Param("actor") Long actor);
}
