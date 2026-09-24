package com.mdframe.forge.plugin.generator.service.printing;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.*;
import com.mdframe.forge.plugin.print.enums.PrintScene;
import com.mdframe.forge.plugin.print.protocol.PrintProtocolValidator;
import com.mdframe.forge.plugin.print.service.PrintDocumentAccess;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.mdframe.forge.plugin.generator.service.printing.PrintApplicationTestData.*;
import static com.mdframe.forge.plugin.generator.service.printing.PrintLowcodeTestData.*;

class PrintBindingValidationServiceTest {
    @Test void rejectsDeletedHiddenAndChangedFieldTypesWithFieldPath() throws Exception {
        var metadata = mock(PrintMetadataResolver.class);
        var model = JSON.readValue(model("synthetic"), LowcodeModelSchema.class);
        var resolved = new PrintMetadataResolver.Metadata(new PrintMetadataResolver.Model(new AiCrudConfig(), model,
                new LowcodePageSchema()), List.of());
        when(metadata.published(eq(ACTOR), eq(SOURCE), any())).thenReturn(resolved);
        var access = new PrintDocumentAccess(JSON);
        var resources = mock(LowcodePrintResourceAccess.class);
        var flowHistory = new FlowPrintHistoryAdapter(mock(org.springframework.beans.factory.ObjectProvider.class));
        var validator = new PrintBindingValidationService(metadata, new LowcodePrintCatalogBuilder(access),
                new PrintProtocolValidator(), access, resources, JSON, flowHistory);
        String schema = SCHEMA.replace("\"source\":\"CONSTANT\",\"value\":\"合成单据\"",
                "\"source\":\"FIELD\",\"path\":\"main.amount\"")
                .replace("\"kind\":\"TEXT\"", "\"kind\":\"TEXT\",\"format\":{\"type\":\"MONEY\"}");
        validator.validate(ACTOR, binding(10, true), schema, JSON.createObjectNode(), true);
        model.getFields().get(1).setBusinessFieldType("TEXT");
        model.getFields().get(1).setDataType("varchar");
        assertThatThrownBy(() -> validator.validate(ACTOR, binding(10, true), schema, JSON.createObjectNode(), true))
                .hasMessageContaining("main.amount");
        model.getFields().get(1).setFieldStatus("HIDDEN");
        assertThatThrownBy(() -> validator.validate(ACTOR, binding(10, true), schema, JSON.createObjectNode(), true))
                .hasMessageContaining("main.amount");
        model.getFields().remove(1);
        assertThatThrownBy(() -> validator.validate(ACTOR, binding(10, true), schema, JSON.createObjectNode(), true))
                .hasMessageContaining("main.amount");
        var flow = new PrintApplicationSnapshotCodec.Binding(SOURCE, PrintScene.FLOW_DONE, 10L, 20L, HASH, true, 0);
        String flowSchema = SCHEMA.replace("\"source\":\"CONSTANT\",\"value\":\"合成单据\"",
                "\"source\":\"FIELD\",\"path\":\"flow.processInstanceId\"");
        assertThatCode(() -> validator.validate(ACTOR, flow, flowSchema, JSON.createObjectNode(), true))
                .doesNotThrowAnyException();

        // 平台托管 flowStatus 即使不在页面 zone fieldRefs 里，发布校验也应放行
        LowcodeFieldSchema flowStatus = new LowcodeFieldSchema();
        flowStatus.setField("flowStatus");
        flowStatus.setColumnName("flow_status");
        flowStatus.setLabel("流程状态");
        flowStatus.setDataType("varchar");
        flowStatus.setListVisible(true);
        flowStatus.setFormVisible(false);
        flowStatus.setAdvancedProps(Map.of("managedBy", "BUSINESS_FLOW"));
        model.setFields(new ArrayList<>(model.getFields()));
        model.getFields().add(flowStatus);
        LowcodePageSchema page = new LowcodePageSchema();
        LowcodePageZone zone = new LowcodePageZone();
        zone.setZoneKey("edit");
        zone.setEnabled(true);
        zone.setFieldRefs(List.of("amount"));
        page.setZones(List.of(zone));
        var withPage = new PrintMetadataResolver.Metadata(
                new PrintMetadataResolver.Model(new AiCrudConfig(), model, page), List.of());
        when(metadata.candidate(eq(ACTOR), eq(SOURCE), any())).thenReturn(withPage);
        String flowStatusSchema = SCHEMA.replace("\"source\":\"CONSTANT\",\"value\":\"合成单据\"",
                "\"source\":\"FIELD\",\"path\":\"main.flowStatus\"");
        assertThatCode(() -> validator.validate(ACTOR, binding(10, true), flowStatusSchema, JSON.createObjectNode(), false))
                .doesNotThrowAnyException();
    }
}
