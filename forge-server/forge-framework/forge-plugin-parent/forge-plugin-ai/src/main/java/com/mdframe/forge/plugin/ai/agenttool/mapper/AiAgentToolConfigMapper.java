package com.mdframe.forge.plugin.ai.agenttool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.ai.agenttool.domain.AiAgentToolConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiAgentToolConfigMapper extends BaseMapper<AiAgentToolConfig> {

    Page<AiAgentToolConfig> selectToolConfigPage(Page<AiAgentToolConfig> page,
                                                   @Param("agentId") Long agentId,
                                                   @Param("toolSource") String toolSource,
                                                   @Param("keyword") String keyword);

    int countByAgentAndKey(@Param("agentId") Long agentId,
                           @Param("toolSource") String toolSource,
                           @Param("toolKey") String toolKey,
                           @Param("excludeId") Long excludeId);

    /**
     * 按 Agent 查询已启用(enabled='1')的工具绑定，供引擎运行时声明工具。
     */
    List<AiAgentToolConfig> selectEnabledByAgentId(@Param("agentId") Long agentId);
}
