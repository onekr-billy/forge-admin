package com.mdframe.forge.plugin.generator.service.printing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.print.enums.PrintScene;
import com.mdframe.forge.plugin.print.protocol.PrintProtocolValidator;
import com.mdframe.forge.plugin.print.service.PrintDocumentAccess;
import com.mdframe.forge.plugin.print.service.PrintFailure;
import com.mdframe.forge.plugin.print.spi.PrintActor;
import com.mdframe.forge.plugin.print.vo.PrintFieldCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PrintBindingValidationService {
    private static final Set<PrintScene> PUBLISHABLE_SCENES = EnumSet.of(
            PrintScene.LIST, PrintScene.DETAIL, PrintScene.FLOW_TODO, PrintScene.FLOW_DONE, PrintScene.FLOW_STARTED);

    private final PrintMetadataResolver metadata;
    private final LowcodePrintCatalogBuilder catalogs;
    private final PrintProtocolValidator protocol;
    private final PrintDocumentAccess access;
    private final LowcodePrintResourceAccess resources;
    private final ObjectMapper json;
    private final FlowPrintHistoryAdapter flowHistory;

    public void validate(PrintActor actor, PrintApplicationSnapshotCodec.Binding binding,
                         String schemaJson, JsonNode snapshot, boolean published) {
        validate(actor, binding, schemaJson, snapshot, published, null);
    }

    public void validate(PrintActor actor, PrintApplicationSnapshotCodec.Binding binding,
                         String schemaJson, JsonNode snapshot, boolean published,
                         Map<String, PrintMetadataResolver.Metadata> metadataCache) {
        if (binding.scene() == null || !PUBLISHABLE_SCENES.contains(binding.scene())) {
            throw PrintFailure.of(409, "PRINT_SCENE_UNSUPPORTED", "不支持的打印场景，不能发布此绑定");
        }
        String cacheKey = binding.source().key() + ":" + published;
        PrintMetadataResolver.Metadata model;
        if (metadataCache != null) {
            model = metadataCache.computeIfAbsent(cacheKey, ignored -> published
                    ? metadata.published(actor, binding.source(), snapshot)
                    : metadata.candidate(actor, binding.source(), snapshot));
        } else {
            model = published
                    ? metadata.published(actor, binding.source(), snapshot)
                    : metadata.candidate(actor, binding.source(), snapshot);
        }
        var catalog = withSceneFields(catalogs.build(model), binding.scene());
        var document = protocol.validate(schemaJson);
        var requirements = access.requirements(document, catalog);
        validateFormats(json.valueToTree(document.document()), access.catalog(catalog), null);
        resources.validate(actor, requirements.staticFileIds());
    }

    private PrintFieldCatalogVO withSceneFields(PrintFieldCatalogVO catalog, PrintScene scene) {
        if (scene != PrintScene.FLOW_TODO && scene != PrintScene.FLOW_DONE && scene != PrintScene.FLOW_STARTED) {
            return catalog;
        }
        var fields = new ArrayList<>(catalog.fields());
        fields.addAll(flowHistory.catalog());
        return new PrintFieldCatalogVO(fields);
    }

    private void validateFormats(JsonNode node, Map<String, String> types, String collection) {
        if (node.isObject()) {
            if ("TABLE".equals(node.path("kind").asText())) {
                collection = node.path("collectionPath").asText();
            }
            String path = "FIELD".equals(node.path("binding").path("source").asText())
                    ? node.path("binding").path("path").asText()
                    : collection != null && node.has("field") ? collection + "." + node.path("field").asText() : null;
            String format = node.path("format").path("type").asText("TEXT");
            if (path != null && !"TEXT".equals(format) && !format.equals(types.get(path))) {
                throw PrintFailure.field(path, "打印字段类型与格式不兼容：" + path);
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                validateFormats(child, types, collection);
            }
        }
    }
}
