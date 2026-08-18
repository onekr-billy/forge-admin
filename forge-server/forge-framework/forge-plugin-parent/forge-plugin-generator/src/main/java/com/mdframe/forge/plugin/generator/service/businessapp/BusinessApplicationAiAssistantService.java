package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationAiAssistantChatDTO;
import com.mdframe.forge.plugin.generator.service.AiClientAdapter;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationAiAssistantReplyVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 在应用发布态和当前用户页面权限边界内调用已绑定智能体。 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationAiAssistantService {

    private static final Set<String> SUPPORTED_CAPABILITIES = Set.of("query", "form", "analysis");
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_PAGE_CONTEXT_LENGTH = 12000;

    private final BusinessApplicationService applicationService;
    private final BusinessApplicationRuntimeService runtimeService;
    private final AiClientAdapter aiClientAdapter;
    private final ObjectMapper objectMapper;

    public Map<String, Object> status(Long applicationId) {
        BusinessApplicationVO current = applicationService.detail(applicationId);
        Map<String, Object> currentConfig = readObject(current.getAiAssistantConfig(), "AI 助理配置");
        Map<String, Object> publishedConfig = Map.of();
        if (current.getLastPublishVersion() != null && Integer.valueOf(1).equals(current.getStatus())) {
            try {
                publishedConfig = readObject(
                        runtimeService.runtimeById(applicationId).getApplication().getAiAssistantConfig(),
                        "已发布 AI 助理配置");
            } catch (BusinessException ignored) {
                publishedConfig = Map.of();
            }
        }
        boolean bound = isBound(currentConfig);
        boolean enabled = booleanValue(currentConfig.get("enabled"));
        boolean available = isAvailable(publishedConfig);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("bound", bound);
        status.put("enabled", enabled);
        status.put("published", current.getLastPublishVersion() != null);
        status.put("available", available);
        status.put("pendingPublish", !Objects.equals(currentConfig, publishedConfig));
        status.put("config", currentConfig);
        return status;
    }

    public BusinessApplicationAiAssistantReplyVO chat(
            String applicationCodeOrSlug, BusinessApplicationAiAssistantChatDTO request) {
        if (request == null) {
            throw new BusinessException("AI 助理请求不能为空");
        }
        String pageId = StringUtils.trimToNull(request.getPageId());
        String message = StringUtils.trimToNull(request.getMessage());
        String capability = StringUtils.lowerCase(
                StringUtils.defaultIfBlank(request.getCapability(), "query"), Locale.ROOT);
        if (pageId == null) {
            throw new BusinessException("请选择当前应用页面");
        }
        if (message == null) {
            throw new BusinessException("请输入要咨询的内容");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("单次咨询内容不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }
        if (!SUPPORTED_CAPABILITIES.contains(capability)) {
            throw new BusinessException("AI 助理能力不受支持");
        }

        BusinessApplicationRuntimeVO runtime = runtimeService.runtimeByCodeOrSlug(applicationCodeOrSlug);
        BusinessApplicationVO application = runtime.getApplication();
        Map<String, Object> config = readObject(application.getAiAssistantConfig(), "已发布 AI 助理配置");
        if (!isAvailable(config)) {
            throw new BusinessException("应用 AI 助理尚未在已发布版本中启用");
        }
        Set<String> configuredCapabilities = stringSet(config.get("capabilities"), true);
        if (!configuredCapabilities.contains(capability)) {
            throw new BusinessException("当前应用未开放该 AI 助理能力");
        }
        Set<String> configuredPageIds = stringSet(config.get("pageIds"), false);
        if (!configuredPageIds.contains(pageId)) {
            throw new BusinessException("当前页面不在 AI 助理授权范围内");
        }

        Map<String, Object> options = readObject(application.getOptions(), "应用运行配置");
        Map<String, Object> builder = objectMap(options.get("inAppBuilder"));
        Map<String, Object> pageNode = findAccessiblePage(builder, pageId);
        Map<String, Object> pageSchema = objectMap(objectMap(builder.get("pages")).get(pageId));
        String agentCode = StringUtils.trimToNull(String.valueOf(config.get("agentCode")));
        if (agentCode == null || "null".equalsIgnoreCase(agentCode)) {
            throw new BusinessException("已发布 AI 助理缺少智能体编码");
        }

        String pageTitle = StringUtils.defaultIfBlank(
                string(pageNode.get("title")), StringUtils.defaultIfBlank(string(pageNode.get("name")), pageId));
        String instructions = StringUtils.abbreviate(
                StringUtils.defaultString(string(config.get("instructions"))), 2000);
        String pageContext = abbreviateJson(pageSchema, MAX_PAGE_CONTEXT_LENGTH);
        Map<String, String> contextVariables = new LinkedHashMap<>();
        contextVariables.put("applicationCode", StringUtils.defaultString(application.getApplicationCode()));
        contextVariables.put("applicationName", StringUtils.defaultString(application.getApplicationName()));
        contextVariables.put("pageId", pageId);
        contextVariables.put("pageTitle", pageTitle);
        contextVariables.put("capability", capability);
        contextVariables.put("applicationInstructions", instructions);

        String prompt = buildPrompt(application, pageId, pageTitle, capability, instructions, pageContext, message);
        AiClientAdapter.AiClientResult result = aiClientAdapter.call(
                agentCode, prompt, contextVariables, 60);
        if (result == null || result.isFallback() || StringUtils.isBlank(result.getContent())) {
            String reason = result == null ? null : result.getFallbackReason();
            throw new BusinessException("应用 AI 助理暂时不可用"
                    + (StringUtils.isBlank(reason) ? "" : "：" + reason));
        }
        return BusinessApplicationAiAssistantReplyVO.builder()
                .pageId(pageId)
                .capability(capability)
                .content(result.getContent())
                .build();
    }

    private Map<String, Object> findAccessiblePage(Map<String, Object> builder, String pageId) {
        Object nodes = builder.get("nodes");
        if (nodes instanceof Collection<?> collection) {
            for (Object value : collection) {
                Map<String, Object> node = objectMap(value);
                if (pageId.equals(string(node.get("id")))
                        && "page".equalsIgnoreCase(string(node.get("type")))) {
                    return node;
                }
            }
        }
        throw new BusinessException("当前用户无权使用 AI 助理访问该页面");
    }

    private String buildPrompt(BusinessApplicationVO application,
                               String pageId,
                               String pageTitle,
                               String capability,
                               String instructions,
                               String pageContext,
                               String message) {
        return "你是业务应用内的受限助理。必须遵守以下边界：\n"
                + "1. 只围绕当前已发布且已授权的页面回答，不得推测或索取其他页面、对象或租户的数据。\n"
                + "2. 当前调用只提供页面结构，不提供业务数据行；不得声称已经查询、修改或提交真实业务数据。\n"
                + "3. form 能力只能提供填写建议，不能声称已经保存；query/analysis 只能基于用户提供的信息和页面结构回答。\n"
                + "4. 如问题超出范围，明确说明无法访问，不得给出绕过权限的方法。\n\n"
                + "应用：" + application.getApplicationName() + "（" + application.getApplicationCode() + "）\n"
                + "页面：" + pageTitle + "（" + pageId + "）\n"
                + "本次能力：" + capability + "\n"
                + (StringUtils.isBlank(instructions) ? "" : "应用指令：" + instructions + "\n")
                + "授权页面结构：" + pageContext + "\n\n"
                + "用户问题：" + message;
    }

    private boolean isAvailable(Map<String, Object> config) {
        return booleanValue(config.get("enabled")) && isBound(config)
                && StringUtils.isNotBlank(string(config.get("agentCode")));
    }

    private boolean isBound(Map<String, Object> config) {
        return config.get("agentId") != null || StringUtils.isNotBlank(string(config.get("agentCode")));
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Set<String> stringSet(Object value, boolean lowerCase) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .map(this::string)
                    .map(StringUtils::trimToNull)
                    .filter(Objects::nonNull)
                    .map(item -> lowerCase ? item.toLowerCase(Locale.ROOT) : item)
                    .forEach(result::add);
        }
        return result;
    }

    private Map<String, Object> readObject(String json, String label) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            throw new BusinessException(label + "不是合法 JSON 对象");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private String abbreviateJson(Map<String, Object> value, int maxLength) {
        try {
            return StringUtils.abbreviate(objectMapper.writeValueAsString(value), maxLength);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
