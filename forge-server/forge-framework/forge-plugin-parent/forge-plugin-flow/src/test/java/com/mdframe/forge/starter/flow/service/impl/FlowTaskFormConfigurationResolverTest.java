package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.dto.TaskFormInfo;
import com.mdframe.forge.starter.flow.entity.FlowFormInstance;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.helper.FlowNodePolicyParser;
import com.mdframe.forge.starter.flow.mapper.FlowFormInstanceMapper;
import com.mdframe.forge.starter.flow.service.FlowFormService;
import com.mdframe.forge.starter.flow.service.FlowModelService;
import com.mdframe.forge.starter.flow.service.FlowNodeConfigService;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowTaskFormConfigurationResolverTest {

    private FlowFormInstanceMapper formInstanceMapper;
    private FlowTaskFormConfigurationResolver resolver;

    @BeforeEach
    void setUp() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        TaskService taskService = mock(TaskService.class);
        FlowModelService flowModelService = mock(FlowModelService.class);
        FlowTaskNodePolicy nodePolicy = new FlowTaskNodePolicy(
                repositoryService,
                mock(HistoryService.class),
                taskService,
                flowModelService,
                mock(FlowNodeConfigService.class),
                ignored -> "purchase-order");
        formInstanceMapper = mock(FlowFormInstanceMapper.class);
        resolver = new FlowTaskFormConfigurationResolver(
                repositoryService,
                taskService,
                flowModelService,
                mock(FlowFormService.class),
                formInstanceMapper,
                nodePolicy,
                (definitionId, fallback) -> "purchase-order");
    }

    @Test
    void nodeDynamicFormMustOverrideModelForm() {
        FlowModel model = new FlowModel();
        model.setFormType("dynamic");
        model.setFormId("model-form");
        model.setFormJson("[{\"field\":\"modelField\"}]");
        UserTask node = new UserTask();
        node.setFormKey("node-form");
        addFlowableAttribute(node, "formJson", "[{\"field\":\"nodeField\"}]");

        TaskFormInfo formInfo = new TaskFormInfo();
        resolver.applyFormConfiguration(formInfo, model, node);

        assertEquals("dynamic", formInfo.getFormType());
        assertEquals("node-form", formInfo.getFormKey());
        assertEquals("[{\"field\":\"nodeField\"}]", formInfo.getFormJson());
    }

    @Test
    void businessModelMustKeepBusinessFormReference() {
        FlowModel model = new FlowModel();
        model.setFormType("business");
        model.setFormId("purchase-form");
        model.setFormJson("{\"objectCode\":\"purchase_order\",\"viewKey\":\"approval\"}");

        TaskFormInfo formInfo = new TaskFormInfo();
        resolver.applyFormConfiguration(formInfo, model, null);

        assertEquals("business", formInfo.getFormType());
        assertEquals("purchase-form", formInfo.getFormKey());
        assertEquals("purchase_order", formInfo.getObjectCode());
        assertEquals("approval", formInfo.getViewKey());
    }

    @Test
    void snapshotLookupMustUseTrustedTenant() {
        FlowFormInstance instance = new FlowFormInstance();
        instance.setId(1L);
        instance.setFormKey("snapshot-form");
        instance.setSchemaSnapshot("[{\"field\":\"snapshotField\"}]");
        when(formInstanceMapper.selectByProcessInstanceIdAndTenantId("process-1", 9L))
                .thenReturn(instance);
        TaskFormInfo formInfo = new TaskFormInfo();
        formInfo.setFormType("dynamic");

        resolver.hydrateFormInstanceSnapshotIfNecessary(formInfo, "process-1", 9L);

        assertEquals(1L, formInfo.getFormInstanceId());
        assertEquals("snapshot-form", formInfo.getFormKey());
        assertEquals("[{\"field\":\"snapshotField\"}]", formInfo.getFormJson());
    }

    private void addFlowableAttribute(UserTask task, String name, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute(name, value);
        attribute.setNamespace(FlowNodePolicyParser.FLOWABLE_NS);
        attribute.setNamespacePrefix("flowable");
        task.addAttribute(attribute);
    }
}
