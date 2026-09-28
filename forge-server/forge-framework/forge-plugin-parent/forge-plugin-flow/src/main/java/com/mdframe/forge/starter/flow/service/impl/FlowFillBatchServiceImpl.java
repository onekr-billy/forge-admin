package com.mdframe.forge.starter.flow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.plugin.message.service.MessageService;
import com.mdframe.forge.plugin.system.dto.SysOrgQuery;
import com.mdframe.forge.plugin.system.entity.SysOrg;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.starter.flow.dto.FlowFillBatchQueryDTO;
import com.mdframe.forge.starter.flow.entity.FlowFillBatch;
import com.mdframe.forge.starter.flow.entity.FlowFillBatchItem;
import com.mdframe.forge.starter.flow.enums.FlowFillBatchStatus;
import com.mdframe.forge.starter.flow.enums.FlowFillItemStatus;
import com.mdframe.forge.starter.flow.mapper.FlowFillBatchItemMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFillBatchMapper;
import com.mdframe.forge.starter.flow.security.FlowRuntimeIdentity;
import com.mdframe.forge.starter.flow.service.FlowFillBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 组织填报批次服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowFillBatchServiceImpl extends ServiceImpl<FlowFillBatchMapper, FlowFillBatch> implements FlowFillBatchService {

    private final FlowFillBatchMapper flowFillBatchMapper;
    private final FlowFillBatchItemMapper flowFillBatchItemMapper;
    private final SysOrgMapper sysOrgMapper;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    @Override
    public IPage<FlowFillBatch> pageBatches(FlowFillBatchQueryDTO query, Integer pageNum, Integer pageSize) {
        Long tenantId = FlowRuntimeIdentity.requireTenantId();
        return flowFillBatchMapper.selectBatchPage(new Page<>(pageNum, pageSize), tenantId, query);
    }

    @Override
    public List<FlowFillBatchItem> listItems(Long batchId) {
        Long tenantId = FlowRuntimeIdentity.requireTenantId();
        return flowFillBatchItemMapper.selectByBatchId(tenantId, batchId);
    }

    @Override
    public void saveBatchConfig(FlowFillBatch batch) {
        Long tenantId = FlowRuntimeIdentity.requireTenantId();
        batch.setTenantId(tenantId);
        if (batch.getStatus() == null) {
            batch.setStatus(FlowFillBatchStatus.DRAFT.getCode());
        }
        if (batch.getAllowResubmit() == null) {
            batch.setAllowResubmit(1);
        }
        if (batch.getId() == null) {
            save(batch);
        } else {
            requireBatch(batch.getId(), tenantId);
            updateById(batch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishBatch(Long id) {
        Long tenantId = FlowRuntimeIdentity.requireTenantId();
        FlowFillBatch batch = requireBatch(id, tenantId);
        List<SysOrg> targetOrgs = resolveTargetOrgs(batch, tenantId);
        if (targetOrgs.isEmpty()) {
            throw new RuntimeException("未解析到目标组织");
        }

        Set<Long> existingOrgIds = new HashSet<>();
        for (FlowFillBatchItem item : flowFillBatchItemMapper.selectByBatchId(tenantId, id)) {
            if (item.getOrgId() != null) {
                existingOrgIds.add(item.getOrgId());
            }
        }

        int created = 0;
        for (SysOrg org : targetOrgs) {
            if (org.getId() == null || existingOrgIds.contains(org.getId())) {
                continue;
            }
            FlowFillBatchItem item = new FlowFillBatchItem();
            item.setTenantId(tenantId);
            item.setBatchId(batch.getId());
            item.setEntryCode(batch.getEntryCode());
            item.setOrgId(org.getId());
            item.setOrgName(org.getOrgName());
            item.setOwnerUserId(org.getLeaderId());
            item.setOwnerUserName(org.getLeaderName());
            item.setSubmitStatus(FlowFillItemStatus.PENDING.getCode());
            item.setFlowStatus(FlowFillItemStatus.PENDING.getCode());
            item.setDeadlineTime(batch.getDeadlineTime());
            flowFillBatchItemMapper.insert(item);
            created++;
            sendFillMessage(batch, item);
        }

        FlowFillBatch update = new FlowFillBatch();
        update.setId(batch.getId());
        update.setStatus(FlowFillBatchStatus.PUBLISHED.getCode());
        updateById(update);
        log.info("组织填报批次发布完成: batchId={}, targetCount={}, created={}", id, targetOrgs.size(), created);
    }

    @Override
    public void deleteBatch(Long id) {
        requireBatch(id, FlowRuntimeIdentity.requireTenantId());
        removeById(id);
    }

    private List<SysOrg> resolveTargetOrgs(FlowFillBatch batch, Long tenantId) {
        JsonNode scope = parseJson(batch.getTargetScope());
        String type = scope == null ? "ALL_ORG" : scope.path("type").asText("ALL_ORG");
        Set<Long> orgIds = new HashSet<>();
        boolean explicitOrgScope = "ORG_IDS".equalsIgnoreCase(type) || "ORG_LIST".equalsIgnoreCase(type);
        if (explicitOrgScope) {
            boolean includeChildren = scope != null && scope.path("includeChildren").asBoolean(false);
            JsonNode array = scope == null ? null : scope.path("orgIds");
            if (array != null && array.isArray()) {
                for (JsonNode node : array) {
                    if (!node.canConvertToLong()) {
                        continue;
                    }
                    long orgId = node.asLong();
                    if (includeChildren) {
                        orgIds.addAll(sysOrgMapper.selectOrgAndChildrenIds(orgId));
                    } else {
                        orgIds.add(orgId);
                    }
                }
            }
        }
        if (explicitOrgScope && orgIds.isEmpty()) {
            return List.of();
        }
        SysOrgQuery query = new SysOrgQuery();
        query.setTenantId(tenantId);
        query.setOrgStatus(1);
        List<SysOrg> allOrgs = sysOrgMapper.selectOrgList(query);
        List<SysOrg> result = new ArrayList<>();
        for (SysOrg org : allOrgs) {
            if (org == null || org.getId() == null) {
                continue;
            }
            if (!orgIds.isEmpty() && !orgIds.contains(org.getId())) {
                continue;
            }
            if (org.getLeaderId() == null) {
                log.warn("组织填报目标组织未配置负责人，跳过生成明细: batchId={}, orgId={}, orgName={}",
                        batch.getId(), org.getId(), org.getOrgName());
                continue;
            }
            result.add(org);
        }
        return result;
    }

    private JsonNode parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            log.warn("组织填报目标范围JSON解析失败: value={}", value, e);
            return null;
        }
    }

    private void sendFillMessage(FlowFillBatch batch, FlowFillBatchItem item) {
        if (item.getOwnerUserId() == null) {
            return;
        }
        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setTitle("您有新的组织填报任务");
        request.setContent("请填报：" + batch.getBatchName() + " - " + item.getOrgName());
        request.setType("SYSTEM");
        request.setChannel("WEB");
        request.setSendScope("USERS");
        request.setUserIds(Set.of(item.getOwnerUserId()));
        request.setParams(java.util.Map.of(
                "batchId", String.valueOf(batch.getId()),
                "batchItemId", String.valueOf(item.getId()),
                "entryCode", item.getEntryCode(),
                "deadlineTime", item.getDeadlineTime() == null ? "" : item.getDeadlineTime().toString(),
                "jumpUrl", "/flow/entry-runtime/" + item.getEntryCode() + "?batchItemId=" + item.getId()
        ));
        try {
            messageService.sendIfAbsent(request, "FLOW_FILL_BATCH_ITEM", String.valueOf(item.getId()));
        } catch (Exception e) {
            log.warn("组织填报站内信发送失败，不阻断批次发布: batchId={}, itemId={}", batch.getId(), item.getId(), e);
        }
    }

    private FlowFillBatch requireBatch(Long id, Long tenantId) {
        FlowFillBatch batch = flowFillBatchMapper.selectByIdAndTenant(id, tenantId);
        if (batch == null) {
            throw new RuntimeException("填报批次不存在");
        }
        return batch;
    }
}
