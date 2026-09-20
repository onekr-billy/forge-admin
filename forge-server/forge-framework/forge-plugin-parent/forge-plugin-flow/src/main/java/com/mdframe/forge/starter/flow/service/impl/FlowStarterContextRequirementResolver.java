package com.mdframe.forge.starter.flow.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 解析流程定义实际引用的发起人组织上下文，避免每次启动都无条件查询所有组织数据。
 */
@Slf4j
final class FlowStarterContextRequirementResolver {

    private static final int DEFAULT_MAX_CACHE_ENTRIES = 512;
    private static final Pattern LEADER_VARIABLE = variablePattern("initiatorLeader");
    private static final Pattern REGION_VARIABLE = Pattern.compile(
            "(?<![A-Za-z0-9_])(?:regionCode|startUserRegionCode)(?![A-Za-z0-9_])");
    private static final Pattern ROLE_VARIABLE = variablePattern("startUserRoleIds");
    private static final Pattern ACTIVE_ORG_VARIABLE = variablePattern("startUserActiveOrgId");
    private static final Pattern ORGANIZATION_VARIABLE = variablePattern("startUserOrgIds");
    private static final Pattern OPAQUE_EXECUTION_POINT = Pattern.compile(
            "<(?:[A-Za-z0-9_-]+:)?(?:serviceTask|scriptTask|businessRuleTask|callActivity)\\b"
                    + "|<(?:[A-Za-z0-9_-]+:)?(?:executionListener|taskListener)\\b"
                    + "|(?:flowable:)?delegateExpression\\s*="
                    + "|flowable:class\\s*=");

    private final Function<BpmnModel, String> xmlExtractor;
    private final int maxCacheEntries;
    private final Map<String, Requirements> cache = new ConcurrentHashMap<>();
    private final Queue<String> insertionOrder = new ConcurrentLinkedQueue<>();

    FlowStarterContextRequirementResolver() {
        this(FlowStarterContextRequirementResolver::toXml, DEFAULT_MAX_CACHE_ENTRIES);
    }

    FlowStarterContextRequirementResolver(Function<BpmnModel, String> xmlExtractor, int maxCacheEntries) {
        this.xmlExtractor = xmlExtractor;
        this.maxCacheEntries = Math.max(1, maxCacheEntries);
    }

    Requirements resolve(String processDefinitionId, BpmnModel bpmnModel) {
        if (bpmnModel == null) {
            return Requirements.allRequired();
        }
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return inspect(bpmnModel);
        }
        Requirements requirements = cache.computeIfAbsent(processDefinitionId, key -> {
            insertionOrder.offer(key);
            return inspect(bpmnModel);
        });
        trimCache();
        return requirements;
    }

    int cacheSize() {
        return cache.size();
    }

    private Requirements inspect(BpmnModel bpmnModel) {
        try {
            String xml = xmlExtractor.apply(bpmnModel);
            if (xml == null) {
                return Requirements.allRequired();
            }
            if (OPAQUE_EXECUTION_POINT.matcher(xml).find()) {
                return Requirements.allRequired();
            }
            return new Requirements(
                    LEADER_VARIABLE.matcher(xml).find(),
                    REGION_VARIABLE.matcher(xml).find(),
                    ROLE_VARIABLE.matcher(xml).find(),
                    ACTIVE_ORG_VARIABLE.matcher(xml).find(),
                    ORGANIZATION_VARIABLE.matcher(xml).find());
        } catch (RuntimeException exception) {
            log.warn("解析流程发起人上下文需求失败，回退为完整加载", exception);
            return Requirements.allRequired();
        }
    }

    private void trimCache() {
        while (cache.size() > maxCacheEntries) {
            String oldestKey = insertionOrder.poll();
            if (oldestKey == null) {
                return;
            }
            cache.remove(oldestKey);
        }
    }

    private static String toXml(BpmnModel bpmnModel) {
        byte[] xml = new BpmnXMLConverter().convertToXML(bpmnModel);
        return new String(xml, StandardCharsets.UTF_8);
    }

    private static Pattern variablePattern(String variableName) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(variableName) + "(?![A-Za-z0-9_])");
    }

    record Requirements(boolean leader, boolean region, boolean roles,
                        boolean activeOrg, boolean organizations) {

        static Requirements allRequired() {
            return new Requirements(true, true, true, true, true);
        }

        boolean any() {
            return leader || region || roles || activeOrg || organizations;
        }

        boolean all() {
            return leader && region && roles && activeOrg && organizations;
        }
    }
}
