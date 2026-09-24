package com.mdframe.forge.plugin.generator.service.printing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.print.enums.PrintScene;
import com.mdframe.forge.plugin.print.mapper.PrintBindingMapper;
import com.mdframe.forge.plugin.print.mapper.PrintTemplateMapper;
import com.mdframe.forge.plugin.print.mapper.PrintTemplateVersionMapper;
import com.mdframe.forge.plugin.print.protocol.PrintProtocolValidator;
import com.mdframe.forge.plugin.print.service.PrintFailure;
import com.mdframe.forge.plugin.print.service.PrintIdentity;
import com.mdframe.forge.plugin.print.spi.PrintSourceRequest;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 只在候选创建时读取设计态绑定；最终提交与回滚使用已固定的清单。
 * 应用发布只固定引用完整性（模板启用、来源一致、版本哈希），不做页面字段目录校验。
 */
@Component
@RequiredArgsConstructor
public class PrintApplicationSnapshotContributor {
    private static final Set<PrintScene> PUBLISHABLE_SCENES = EnumSet.of(
            PrintScene.LIST, PrintScene.DETAIL, PrintScene.FLOW_TODO, PrintScene.FLOW_DONE, PrintScene.FLOW_STARTED);

    private final PrintIdentity identity;
    private final PrintApplicationLock applicationLock;
    private final PrintBindingMapper bindings;
    private final PrintTemplateMapper templates;
    private final PrintTemplateVersionMapper versions;
    private final PrintProtocolValidator protocol;
    private final PrintApplicationSnapshotCodec codec;
    private final ObjectMapper json;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> capture(Long applicationId) {
        var actor = identity.current();
        applicationLock.lock(actor.tenantId(), applicationId);
        var rows = bindings.selectApplication(actor.tenantId(), applicationId);
        if (rows.size() > 1000) {
            throw PrintFailure.of(409, "PRINT_BINDING_LIMIT", "应用打印绑定超过 1000 项");
        }
        var pinned = new ArrayList<PrintApplicationSnapshotCodec.Binding>();
        for (var row : rows) {
            var source = new PrintSourceRequest(row.getApplicationId(),
                    com.mdframe.forge.plugin.print.enums.PrintSourceType.valueOf(row.getSourceType()),
                    row.getPageId(), row.getFormKey(), row.getObjectCode());
            var template = templates.lockScoped(actor.tenantId(), row.getTemplateId());
            if (template == null || !EnableStatus.ENABLED.matches(template.getStatus())
                    || !source.equals(PrintSourceRequest.from(template))
                    || !source.key().equals(row.getSourceKey())
                    || !source.key().equals(template.getSourceKey()) || template.getPublishedVersionId() == null) {
                throw PrintFailure.of(409, "PRINT_BINDING_UNPUBLISHED", "打印绑定引用的模板已停用、未发布或来源不一致");
            }
            var version = versions.selectScoped(actor.tenantId(), template.getId(), template.getPublishedVersionId());
            if (version == null || !protocol.validate(version.getSchemaJson()).schemaHash().equals(version.getSchemaHash())) {
                throw PrintFailure.of(409, "PRINT_APPLICATION_VERSION_INVALID", "打印模板版本内容校验失败");
            }
            PrintScene scene = PrintScene.valueOf(row.getScene());
            if (!PUBLISHABLE_SCENES.contains(scene)) {
                throw PrintFailure.of(409, "PRINT_SCENE_UNSUPPORTED", "不支持的打印场景，不能发布此绑定");
            }
            pinned.add(new PrintApplicationSnapshotCodec.Binding(source, scene,
                    template.getId(), version.getId(), version.getSchemaHash(),
                    Boolean.TRUE.equals(row.getIsDefault()), row.getSortOrder()));
        }
        Map<String, Object> manifest = Map.of("schemaVersion", 1, "bindings", pinned);
        try {
            codec.read(json.writeValueAsString(Map.of("printing", manifest)), applicationId);
        } catch (JsonProcessingException ex) {
            throw PrintFailure.of(409, "PRINT_APPLICATION_SNAPSHOT_INVALID", "打印发布清单序列化失败");
        }
        return manifest;
    }
}
