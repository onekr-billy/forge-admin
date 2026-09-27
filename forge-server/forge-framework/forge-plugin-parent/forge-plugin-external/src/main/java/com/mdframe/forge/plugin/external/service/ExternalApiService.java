package com.mdframe.forge.plugin.external.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mdframe.forge.plugin.external.dto.ExternalApiQuery;
import com.mdframe.forge.plugin.external.entity.ExternalApi;

import java.util.List;

public interface ExternalApiService extends IService<ExternalApi> {

    IPage<ExternalApi> page(ExternalApiQuery query);

    ExternalApi getManagementById(Long id);

    ExternalApi getRuntimeById(Long id);

    List<ExternalApi> listBySystemId(Long systemId);

    ExternalApi getByCode(String apiCode, Long systemId);

    List<ExternalApi> listWithSystem();

    boolean saveApi(ExternalApi entity);

    boolean updateApi(ExternalApi entity);

    boolean removeApi(Long id);
}
