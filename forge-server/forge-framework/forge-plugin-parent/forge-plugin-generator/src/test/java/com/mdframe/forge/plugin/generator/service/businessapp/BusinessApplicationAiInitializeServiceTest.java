package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateResult;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeDataModelDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeObjectSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeProcessSuggestionDTO;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationAiInitializeResultVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessApplicationAiInitializeService")
class BusinessApplicationAiInitializeServiceTest {

    @Test
    @DisplayName("confirmed process suggestions create editable application process drafts")
    void createsSuggestedProcessDrafts() {
        BusinessApplicationService applicationService = mock(BusinessApplicationService.class);
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        BusinessObjectCreateService objectCreateService = mock(BusinessObjectCreateService.class);
        BusinessObjectDesignerService designerService = mock(BusinessObjectDesignerService.class);
        BusinessProcessService processService = mock(BusinessProcessService.class);
        AiBusinessApplication application = new AiBusinessApplication();
        application.setId(10L);
        application.setApplicationCode("expense_app");
        application.setSuiteCode("finance");
        when(applicationService.requireEntity(10L)).thenReturn(application);
        when(applicationObjectService.list(10L)).thenReturn(List.of());
        when(objectCreateService.create(any())).thenReturn(101L);
        when(designerService.loadContext(101L))
                .thenReturn(new BusinessObjectDesignerService.DesignerContext());
        BusinessProcessVO process = new BusinessProcessVO();
        process.setId("201");
        process.setProcessName("费用审批流程");
        when(processService.create(any())).thenReturn(process);
        BusinessApplicationAiInitializeService service = new BusinessApplicationAiInitializeService(
                new ObjectMapper(), applicationService, applicationObjectService,
                objectCreateService, designerService, new BusinessNamingService(), processService);

        LowcodeAiAppGenerateResult plan = new LowcodeAiAppGenerateResult();
        plan.setModels(List.of(model("expense", "费用单")));
        LowcodeProcessSuggestionDTO suggestion = new LowcodeProcessSuggestionDTO();
        suggestion.setProcessCode("expense_approval");
        suggestion.setProcessName("费用审批流程");
        suggestion.setProcessDescription("用户确认后创建的最小流程草稿");
        suggestion.setSubjectObjectCode("expense");
        plan.setProcessSuggestions(List.of(suggestion));

        BusinessApplicationAiInitializeResultVO result = service.initialize(10L, plan);

        assertEquals(1, result.getProcesses().size());
        ArgumentCaptor<BusinessProcessDTO> captor = ArgumentCaptor.forClass(BusinessProcessDTO.class);
        verify(processService).create(captor.capture());
        assertEquals("10", captor.getValue().getApplicationId());
        assertEquals("101", captor.getValue().getSubjectObjectId());
        assertEquals("expense_approval", captor.getValue().getProcessCode());
    }

    private LowcodeDataModelDTO model(String code, String name) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField("name");
        field.setLabel("名称");
        field.setDataType("varchar");
        LowcodeObjectSchema object = new LowcodeObjectSchema();
        object.setCode(code);
        object.setName(name);
        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setObject(object);
        schema.setFields(List.of(field));
        LowcodeDataModelDTO model = new LowcodeDataModelDTO();
        model.setModelCode(code);
        model.setModelName(name);
        model.setModelSchema(schema);
        return model;
    }
}
