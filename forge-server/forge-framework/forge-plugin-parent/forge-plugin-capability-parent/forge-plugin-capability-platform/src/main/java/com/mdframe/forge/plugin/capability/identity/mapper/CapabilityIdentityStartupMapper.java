package com.mdframe.forge.plugin.capability.identity.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CapabilityIdentityStartupMapper {

    @InterceptorIgnore(tenantLine = "true")
    int countUnsafeEnabledClients(@Param("reservedClientCodes") List<String> reservedClientCodes);

    @InterceptorIgnore(tenantLine = "true")
    int countUnsafeEnabledGrants(@Param("reservedClientCodes") List<String> reservedClientCodes);
}
