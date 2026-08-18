package com.mdframe.forge.plugin.external.service;

import com.mdframe.forge.plugin.external.entity.ExternalApi;

import java.util.List;
import java.util.Map;

public interface ExternalQuerySourceService {

    List<ExternalApi> listAvailable();

    ExternalApi requireMetadata(String sourceKey);

    Object execute(String sourceKey, Map<String, Object> params);

    String sourceKey(ExternalApi api);
}
