package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;

record BusinessRuntimeContext(String requestedObjectCode,
                              String objectCode,
                              String configKey,
                              AiBusinessDocumentConfig documentConfig,
                              AiCrudConfig runtimeConfig,
                              AiBusinessObject businessObject) {
}
