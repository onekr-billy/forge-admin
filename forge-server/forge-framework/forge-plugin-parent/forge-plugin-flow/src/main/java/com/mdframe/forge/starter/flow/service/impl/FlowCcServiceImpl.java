package com.mdframe.forge.starter.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.flow.client.spi.FlowBusinessListDisplayAdapter;
import com.mdframe.forge.flow.client.spi.FlowBusinessListDisplayItem;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowCc;
import com.mdframe.forge.starter.flow.entity.FlowRecordParticipant;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowCcMapper;
import com.mdframe.forge.starter.flow.service.FlowCcService;
import com.mdframe.forge.starter.flow.service.FlowRecordParticipantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程抄送服务实现
 */
@Slf4j
@Service
public class FlowCcServiceImpl extends ServiceImpl<FlowCcMapper, FlowCc> implements FlowCcService {

    @Autowired(required = false)
    private FlowBusinessListDisplayAdapter flowBusinessListDisplayAdapter;

    @Autowired(required = false)
    private FlowRecordParticipantService flowRecordParticipantService;

    @Autowired(required = false)
    private FlowBusinessMapper flowBusinessMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendCc(String processInstanceId, String processDefKey, String taskId,
                       String title, String content, String businessKey,
                       List<String> ccUserIds, List<String> ccUserNames,
                       String sendUserId, String sendUserName) {
        
        if (ccUserIds == null || ccUserIds.isEmpty()) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < ccUserIds.size(); i++) {
            FlowCc cc = new FlowCc();
            cc.setProcessInstanceId(processInstanceId);
            cc.setProcessDefKey(processDefKey);
            cc.setTaskId(taskId);
            cc.setTitle(title);
            cc.setContent(content);
            cc.setBusinessKey(businessKey);
            cc.setCcUserId(ccUserIds.get(i));
            cc.setCcUserName(ccUserNames != null && i < ccUserNames.size() ? ccUserNames.get(i) : null);
            cc.setSendUserId(sendUserId);
            cc.setSendUserName(sendUserName);
            cc.setCcTime(now);
            cc.setIsRead(0);
            
            save(cc);
            recordCcParticipant(processInstanceId, businessKey, ccUserIds.get(i));
        }
        
        log.info("发送抄送：processInstanceId={}, ccUserIds={}", processInstanceId, ccUserIds);
    }

    private void recordCcParticipant(String processInstanceId, String businessKey, String ccUserId) {
        if (flowRecordParticipantService == null) {
            return;
        }
        FlowBusiness business = null;
        if (flowBusinessMapper != null && processInstanceId != null) {
            business = flowBusinessMapper.selectByProcessInstanceId(processInstanceId);
        }
        if (business != null) {
            flowRecordParticipantService.record(business, ccUserId, FlowRecordParticipant.CC);
            return;
        }
        flowRecordParticipantService.record(null, null, businessKey, processInstanceId,
                ccUserId, FlowRecordParticipant.CC);
    }

    @Override
    public IPage<FlowCc> myCc(Page<FlowCc> page, String userId, Integer isRead) {
        LambdaQueryWrapper<FlowCc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowCc::getCcUserId, userId)
                .eq(isRead != null, FlowCc::getIsRead, isRead)
                .orderByDesc(FlowCc::getCcTime);
        return enrichCcPage(page(page, wrapper));
    }

    @Override
    public IPage<FlowCc> sentCc(Page<FlowCc> page, String userId) {
        LambdaQueryWrapper<FlowCc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowCc::getSendUserId, userId)
                .orderByDesc(FlowCc::getCcTime);
        return enrichCcPage(page(page, wrapper));
    }

    private IPage<FlowCc> enrichCcPage(IPage<FlowCc> page) {
        if (flowBusinessListDisplayAdapter == null || page == null || page.getRecords() == null
                || page.getRecords().isEmpty()) {
            return page;
        }
        List<FlowBusinessListDisplayItem> items = page.getRecords().stream()
                .map(this::toDisplayItem)
                .collect(Collectors.toList());
        try {
            flowBusinessListDisplayAdapter.enrich(items);
            for (int i = 0; i < page.getRecords().size(); i++) {
                applyDisplayItem(page.getRecords().get(i), items.get(i));
            }
        } catch (Exception e) {
            log.warn("补齐流程抄送业务摘要失败，继续返回流程基础信息: {}", e.getMessage());
        }
        return page;
    }

    private FlowBusinessListDisplayItem toDisplayItem(FlowCc cc) {
        FlowBusinessListDisplayItem item = new FlowBusinessListDisplayItem();
        item.setBusinessKey(cc.getBusinessKey());
        item.setProcessInstanceId(cc.getProcessInstanceId());
        item.setProcessDefKey(cc.getProcessDefKey());
        item.setProcessName(cc.getProcessName());
        item.setProcessDefinitionName(cc.getProcessDefinitionName());
        item.setTaskId(cc.getTaskId());
        item.setTitle(cc.getTitle());
        item.setObjectCode(cc.getObjectCode());
        item.setRecordId(cc.getRecordId());
        item.setBusinessObjectName(cc.getBusinessObjectName());
        item.setBusinessSummary(cc.getBusinessSummary());
        return item;
    }

    private void applyDisplayItem(FlowCc cc, FlowBusinessListDisplayItem item) {
        if (item == null) {
            return;
        }
        cc.setObjectCode(firstNonBlank(item.getObjectCode(), cc.getObjectCode()));
        cc.setRecordId(item.getRecordId() != null ? item.getRecordId() : cc.getRecordId());
        cc.setBusinessObjectName(firstNonBlank(item.getBusinessObjectName(), cc.getBusinessObjectName()));
        cc.setBusinessSummary(firstNonBlank(item.getBusinessSummary(), cc.getBusinessSummary()));
        cc.setProcessName(firstNonBlank(cc.getProcessName(), item.getProcessName()));
        cc.setProcessDefinitionName(firstNonBlank(
                cc.getProcessDefinitionName(),
                item.getProcessDefinitionName(),
                cc.getProcessName(),
                cc.getProcessDefKey()));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(String id) {
        LambdaUpdateWrapper<FlowCc> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FlowCc::getId, id)
                .set(FlowCc::getIsRead, 1)
                .set(FlowCc::getReadTime, LocalDateTime.now());
        update(wrapper);
        log.info("标记抄送已读：{}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMarkRead(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        
        LambdaUpdateWrapper<FlowCc> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(FlowCc::getId, ids)
                .set(FlowCc::getIsRead, 1)
                .set(FlowCc::getReadTime, LocalDateTime.now());
        update(wrapper);
        log.info("批量标记抄送已读：{}条", ids.size());
    }

    @Override
    public long countUnread(String userId) {
        LambdaQueryWrapper<FlowCc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowCc::getCcUserId, userId)
                .eq(FlowCc::getIsRead, 0);
        return count(wrapper);
    }
}
